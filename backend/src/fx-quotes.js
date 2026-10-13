import { createHmac, randomUUID, timingSafeEqual } from "node:crypto";

export const KRW_PHP_QUOTE_TTL_MS = 5 * 60 * 1000;

export class FxQuoteError extends Error {
  constructor(status, message) {
    super(message);
    this.status = status;
  }
}

export async function getLatestKrwPhpRate(apiKey, fetchImpl = fetch, now = Date.now()) {
  if (!apiKey) {
    throw new FxQuoteError(503, "KRW-to-PHP quotes are not configured on this server");
  }

  let response;
  try {
    response = await fetchImpl(
      "https://api.currencyapi.com/v3/latest?base_currency=KRW&currencies=PHP",
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
    body?.meta?.base_currency !== "KRW" ||
    typeof rate !== "number" ||
    !Number.isFinite(rate) ||
    rate <= 0 ||
    !Number.isFinite(updatedAt) ||
    updatedAt > now + 60_000 ||
    now - updatedAt > 2 * 60 * 60 * 1000
  ) {
    throw new FxQuoteError(502, "The FX rate service returned a missing or stale KRW/PHP rate");
  }

  return { phpPerKrw: rate, updatedAt: new Date(updatedAt).toISOString() };
}

export function createKrwPhpQuote({
  amountKrw,
  phpPerKrw,
  rateUpdatedAt,
  subject,
  signingKey,
  now = Date.now()
}) {
  const amountPhp = Math.round(amountKrw * phpPerKrw * 100) / 100;
  if (!Number.isFinite(amountPhp) || amountPhp < 0.01 || amountPhp > 1_000_000) {
    throw new FxQuoteError(400, "The converted PHP QR amount is outside the supported range");
  }

  const payload = {
    purpose: "krw-php-qr",
    quoteId: randomUUID(),
    subject,
    amountKrw,
    amountPhp,
    phpPerKrw,
    rateUpdatedAt,
    issuedAt: now,
    expiresAt: now + KRW_PHP_QUOTE_TTL_MS
  };
  const encoded = Buffer.from(JSON.stringify(payload)).toString("base64url");
  const signature = createHmac("sha256", signingKey).update(encoded).digest("base64url");
  return { ...payload, quoteToken: `${encoded}.${signature}` };
}

export function verifyKrwPhpQuote(token, subject, signingKey, now = Date.now()) {
  if (typeof token !== "string" || token.length > 2048) {
    throw new FxQuoteError(400, "Request a new KRW-to-PHP quote before creating this QR");
  }

  const [encoded, submittedSignature, extra] = token.split(".");
  if (!encoded || !submittedSignature || extra !== undefined) {
    throw new FxQuoteError(400, "Request a new KRW-to-PHP quote before creating this QR");
  }
  const expectedSignature = createHmac("sha256", signingKey).update(encoded).digest();
  let providedSignature;
  try {
    providedSignature = Buffer.from(submittedSignature, "base64url");
  } catch {
    throw new FxQuoteError(400, "Request a new KRW-to-PHP quote before creating this QR");
  }
  if (
    providedSignature.length !== expectedSignature.length ||
    !timingSafeEqual(providedSignature, expectedSignature)
  ) {
    throw new FxQuoteError(400, "Request a new KRW-to-PHP quote before creating this QR");
  }

  let payload;
  try {
    payload = JSON.parse(Buffer.from(encoded, "base64url").toString("utf8"));
  } catch {
    throw new FxQuoteError(400, "Request a new KRW-to-PHP quote before creating this QR");
  }
  if (
    payload?.purpose !== "krw-php-qr" ||
    payload.subject !== subject ||
    !Number.isInteger(payload.amountKrw) ||
    payload.amountKrw < 1 ||
    !Number.isFinite(payload.amountPhp) ||
    payload.amountPhp < 0.01 ||
    payload.amountPhp > 1_000_000 ||
    !Number.isFinite(payload.phpPerKrw) ||
    payload.phpPerKrw <= 0 ||
    !Number.isFinite(payload.issuedAt) ||
    !Number.isFinite(payload.expiresAt)
  ) {
    throw new FxQuoteError(400, "Request a new KRW-to-PHP quote before creating this QR");
  }
  if (payload.expiresAt <= now) {
    throw new FxQuoteError(410, "The KRW-to-PHP quote expired; request a new quote");
  }
  if (payload.issuedAt > now + 60_000 || payload.expiresAt - payload.issuedAt !== KRW_PHP_QUOTE_TTL_MS) {
    throw new FxQuoteError(400, "Request a new KRW-to-PHP quote before creating this QR");
  }
  return payload;
}
