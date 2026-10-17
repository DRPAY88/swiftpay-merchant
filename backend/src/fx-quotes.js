import { createHmac, randomUUID, timingSafeEqual } from "node:crypto";

export const FX_PHP_QUOTE_TTL_MS = 5 * 60 * 1000;
export const KRW_PHP_QUOTE_TTL_MS = FX_PHP_QUOTE_TTL_MS;
const MAX_FX_RATE_AGE_MS = 26 * 60 * 60 * 1000;
const MAX_REFERENCE_RATE_AGE_MS = 4 * 24 * 60 * 60 * 1000;
const MAX_USDT_RATE_AGE_MS = 5 * 60 * 1000;
const FX_CURRENCIES = new Set(["KRW", "USD", "EUR", "CNY"]);

export class FxQuoteError extends Error {
  constructor(status, message) {
    super(message);
    this.status = status;
  }
}

export async function getLatestPhpRate(baseCurrency, apiKey, fetchImpl = fetch, now = Date.now()) {
  if (!FX_CURRENCIES.has(baseCurrency)) {
    throw new FxQuoteError(400, "Only KRW, USD, EUR, and CNY customer prices can be quoted for PHP QR settlement");
  }
  if (!apiKey) {
    throw new FxQuoteError(503, "FX quotes unavailable: configure the backend CURRENCYAPI_KEY secret");
  }

  let response;
  try {
    response = await fetchImpl(
      `https://api.currencyapi.com/v3/latest?base_currency=${baseCurrency}&currencies=PHP`,
      {
        headers: { Accept: "application/json", apikey: apiKey },
        signal: AbortSignal.timeout(8000)
      }
    );
  } catch {
    throw new FxQuoteError(503, "The FX rate service is temporarily unavailable; no quote was created");
  }

  if (!response.ok) {
    if (response.status === 401 || response.status === 403) {
      throw new FxQuoteError(503, "The FX rate service is not authorized for live commercial quotes");
    }
    if (response.status === 429) {
      throw new FxQuoteError(503, "The FX rate service is at its request limit; try again later");
    }
    throw new FxQuoteError(503, "The FX rate service could not provide a quote; try again later");
  }

  let body;
  try {
    body = await response.json();
  } catch {
    throw new FxQuoteError(502, "The FX rate service returned an invalid response");
  }

  const rate = body?.data?.PHP?.value;
  const updatedAt = Date.parse(body?.meta?.last_updated_at);
  if (
    typeof rate !== "number" ||
    !Number.isFinite(rate) ||
    rate <= 0 ||
    !Number.isFinite(updatedAt) ||
    updatedAt > now + 60_000 ||
    now - updatedAt > MAX_FX_RATE_AGE_MS
  ) {
    throw new FxQuoteError(502, `The FX rate service returned a missing or stale ${baseCurrency}/PHP rate`);
  }

  return { phpPerUnit: rate, updatedAt: new Date(updatedAt).toISOString() };
}

export function getLatestKrwPhpRate(apiKey, fetchImpl = fetch, now = Date.now()) {
  return getLatestPhpRate("KRW", apiKey, fetchImpl, now)
    .then(({ phpPerUnit, updatedAt }) => ({ phpPerKrw: phpPerUnit, updatedAt }));
}

async function getLatestFallbackKrwRates(fetchImpl, now) {
  let response;
  try {
    response = await fetchImpl(
      "https://api.frankfurter.dev/v1/latest?base=KRW&symbols=PHP,USD",
      { headers: { Accept: "application/json" }, signal: AbortSignal.timeout(8000) }
    );
  } catch {
    throw new FxQuoteError(503, "Reference rates are temporarily unavailable from the primary and fallback FX services");
  }

  if (!response.ok) {
    throw new FxQuoteError(503, "Reference rates are temporarily unavailable from the primary and fallback FX services");
  }

  let body;
  try {
    body = await response.json();
  } catch {
    throw new FxQuoteError(502, "The fallback reference rate service returned an invalid response");
  }

  const updatedAt = Date.parse(`${body?.date}T00:00:00.000Z`);
  const phpPerKrw = body?.rates?.PHP;
  const usdPerKrw = body?.rates?.USD;
  if (
    !Number.isFinite(updatedAt) ||
    updatedAt > now + 60_000 ||
    now - updatedAt > MAX_REFERENCE_RATE_AGE_MS ||
    typeof phpPerKrw !== "number" ||
    !Number.isFinite(phpPerKrw) ||
    phpPerKrw <= 0 ||
    typeof usdPerKrw !== "number" ||
    !Number.isFinite(usdPerKrw) ||
    usdPerKrw <= 0
  ) {
    throw new FxQuoteError(502, "The fallback reference rate service returned missing or stale KRW rates");
  }

  return {
    phpPerKrw,
    usdPerKrw,
    updatedAt: new Date(updatedAt).toISOString()
  };
}

async function getLatestUsdtUsdRate(fetchImpl, now) {
  let response;
  try {
    response = await fetchImpl(
      "https://api.coingecko.com/api/v3/simple/price?ids=tether&vs_currencies=usd&include_last_updated_at=true",
      { headers: { Accept: "application/json" }, signal: AbortSignal.timeout(8000) }
    );
  } catch (error) {
    if (error instanceof TypeError || error?.name === "AbortError" || error?.name === "TimeoutError") {
      return null;
    }
    throw error;
  }
  if (!response.ok) return null;

  let body;
  try {
    body = await response.json();
  } catch (error) {
    if (error instanceof SyntaxError) return null;
    throw error;
  }

  const usdPerUsdt = body?.tether?.usd;
  const updatedAt = Number(body?.tether?.last_updated_at) * 1000;
  if (
    typeof usdPerUsdt !== "number" ||
    !Number.isFinite(usdPerUsdt) ||
    usdPerUsdt <= 0 ||
    !Number.isFinite(updatedAt) ||
    updatedAt > now + 60_000 ||
    now - updatedAt > MAX_USDT_RATE_AGE_MS
  ) {
    return null;
  }

  return { usdPerUsdt, updatedAt: new Date(updatedAt).toISOString() };
}

export async function getLatestMarketingKrwRates(
  apiKey,
  fxFetchImpl = fetch,
  marketFetchImpl = fetch,
  now = Date.now(),
  fallbackFetchImpl = fetch
) {
  let phpPerKrw;
  let usdPerKrw = null;
  let fxUpdatedAt;
  let source;
  let fxUsdRequest = null;

  try {
    const krwPhpRate = await getLatestPhpRate("KRW", apiKey, fxFetchImpl, now);
    phpPerKrw = krwPhpRate.phpPerUnit;
    fxUpdatedAt = krwPhpRate.updatedAt;
    source = "CurrencyAPI";
    fxUsdRequest = getLatestPhpRate("USD", apiKey, fxFetchImpl, now);
  } catch (error) {
    if (!(error instanceof FxQuoteError)) throw error;
    const fallbackRates = await getLatestFallbackKrwRates(fallbackFetchImpl, now);
    phpPerKrw = fallbackRates.phpPerKrw;
    usdPerKrw = fallbackRates.usdPerKrw;
    fxUpdatedAt = fallbackRates.updatedAt;
    source = "Frankfurter";
  }

  const [usdPhpResult, usdtUsdResult] = await Promise.allSettled([
    fxUsdRequest,
    getLatestUsdtUsdRate(marketFetchImpl, now)
  ]);
  if (usdPhpResult.status === "rejected" && !(usdPhpResult.reason instanceof FxQuoteError)) {
    throw usdPhpResult.reason;
  }
  if (usdtUsdResult.status === "rejected") {
    throw usdtUsdResult.reason;
  }
  if (usdPerKrw == null && fxUsdRequest && usdPhpResult.status === "fulfilled") {
    usdPerKrw = phpPerKrw / usdPhpResult.value.phpPerUnit;
  }

  let usdtRate = null;
  const usdtUsdRate = usdtUsdResult.value;
  if (usdPerKrw != null && usdtUsdRate) {
    const usdtPerKrw = usdPerKrw / usdtUsdRate.usdPerUsdt;
    if (!Number.isFinite(usdtPerKrw) || usdtPerKrw <= 0) {
      throw new FxQuoteError(502, "The rate services returned an invalid KRW/USDT conversion");
    }
    usdtRate = {
      perKrw: usdtPerKrw,
      fxUpdatedAt,
      usdFxUpdatedAt: usdPhpResult.status === "fulfilled" && usdPhpResult.value
        ? usdPhpResult.value.updatedAt
        : fxUpdatedAt,
      usdtUsdUpdatedAt: usdtUsdRate.updatedAt
    };
  }

  return {
    retrievedAt: new Date(now).toISOString(),
    source,
    rates: {
      PHP: {
        perKrw: phpPerKrw,
        updatedAt: fxUpdatedAt
      },
      ...(usdtRate ? { USDT: usdtRate } : {})
    }
  };
}

export function createFxPhpQuote({
  amount,
  currency,
  phpPerUnit,
  rateUpdatedAt,
  subject,
  signingKey,
  now = Date.now()
}) {
  if (!FX_CURRENCIES.has(currency) || !Number.isFinite(amount) || amount <= 0 || amount > 1_000_000) {
    throw new FxQuoteError(400, "Enter a valid KRW, USD, EUR, or CNY customer amount");
  }
  if (currency === "KRW" && !Number.isInteger(amount)) {
    throw new FxQuoteError(400, "KRW customer amounts must be whole numbers");
  }
  const amountPhp = Math.round(amount * phpPerUnit * 100) / 100;
  if (!Number.isFinite(amountPhp) || amountPhp < 0.01 || amountPhp > 1_000_000) {
    throw new FxQuoteError(400, "The converted PHP QR amount is outside the supported range");
  }

  const payload = {
    purpose: "fx-php-qr",
    quoteId: randomUUID(),
    subject,
    customerAmount: amount,
    customerCurrency: currency,
    amountPhp,
    phpPerUnit,
    rateUpdatedAt,
    issuedAt: now,
    expiresAt: now + FX_PHP_QUOTE_TTL_MS
  };
  const encoded = Buffer.from(JSON.stringify(payload)).toString("base64url");
  const signature = createHmac("sha256", signingKey).update(encoded).digest("base64url");
  return { ...payload, quoteToken: `${encoded}.${signature}` };
}

export function createKrwPhpQuote({
  amountKrw,
  phpPerKrw,
  rateUpdatedAt,
  subject,
  signingKey,
  now = Date.now()
}) {
  return createFxPhpQuote({
    amount: amountKrw,
    currency: "KRW",
    phpPerUnit: phpPerKrw,
    rateUpdatedAt,
    subject,
    signingKey,
    now
  });
}

export function verifyFxPhpQuote(token, subject, signingKey, now = Date.now()) {
  if (typeof token !== "string" || token.length > 2048) {
    throw new FxQuoteError(400, "Request a new FX-to-PHP quote before creating this QR");
  }

  const [encoded, submittedSignature, extra] = token.split(".");
  if (!encoded || !submittedSignature || extra !== undefined) {
    throw new FxQuoteError(400, "Request a new FX-to-PHP quote before creating this QR");
  }
  const expectedSignature = createHmac("sha256", signingKey).update(encoded).digest();
  let providedSignature;
  try {
    providedSignature = Buffer.from(submittedSignature, "base64url");
  } catch {
    throw new FxQuoteError(400, "Request a new FX-to-PHP quote before creating this QR");
  }
  if (
    providedSignature.length !== expectedSignature.length ||
    !timingSafeEqual(providedSignature, expectedSignature)
  ) {
    throw new FxQuoteError(400, "Request a new FX-to-PHP quote before creating this QR");
  }

  let payload;
  try {
    payload = JSON.parse(Buffer.from(encoded, "base64url").toString("utf8"));
  } catch {
    throw new FxQuoteError(400, "Request a new FX-to-PHP quote before creating this QR");
  }
  const isLegacyKrwQuote = payload?.purpose === "krw-php-qr";
  if (isLegacyKrwQuote) {
    payload.customerAmount = payload.amountKrw;
    payload.customerCurrency = "KRW";
    payload.phpPerUnit = payload.phpPerKrw;
  }
  if (
    (payload?.purpose !== "fx-php-qr" && !isLegacyKrwQuote) ||
    payload.subject !== subject ||
    !FX_CURRENCIES.has(payload.customerCurrency) ||
    !Number.isFinite(payload.customerAmount) ||
    payload.customerAmount <= 0 ||
    payload.customerAmount > 1_000_000 ||
    (payload.customerCurrency === "KRW" && !Number.isInteger(payload.customerAmount)) ||
    !Number.isFinite(payload.amountPhp) ||
    payload.amountPhp < 0.01 ||
    payload.amountPhp > 1_000_000 ||
    !Number.isFinite(payload.phpPerUnit) ||
    payload.phpPerUnit <= 0 ||
    !Number.isFinite(payload.issuedAt) ||
    !Number.isFinite(payload.expiresAt)
  ) {
    throw new FxQuoteError(400, "Request a new FX-to-PHP quote before creating this QR");
  }
  if (payload.expiresAt <= now) {
    throw new FxQuoteError(410, "The FX-to-PHP quote expired; request a new quote");
  }
  if (payload.issuedAt > now + 60_000 || payload.expiresAt - payload.issuedAt !== FX_PHP_QUOTE_TTL_MS) {
    throw new FxQuoteError(400, "Request a new FX-to-PHP quote before creating this QR");
  }
  return payload;
}

export function verifyKrwPhpQuote(token, subject, signingKey, now = Date.now()) {
  const quote = verifyFxPhpQuote(token, subject, signingKey, now);
  if (quote.customerCurrency !== "KRW") {
    throw new FxQuoteError(400, "Request a new KRW-to-PHP quote before creating this QR");
  }
  return {
    ...quote,
    amountKrw: quote.customerAmount,
    phpPerKrw: quote.phpPerUnit
  };
}
