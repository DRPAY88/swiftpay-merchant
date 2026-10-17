import assert from "node:assert/strict";
import { test } from "node:test";
import {
  createKrwPhpQuote,
  createFxPhpQuote,
  FxQuoteError,
  getLatestPhpRate,
  getLatestKrwPhpRate,
  getLatestMarketingKrwRates,
  KRW_PHP_QUOTE_TTL_MS,
  verifyFxPhpQuote,
  verifyKrwPhpQuote
} from "../src/fx-quotes.js";

const signingKey = "test-session-signing-key-at-least-32-bytes";
const now = Date.parse("2026-10-13T00:00:00.000Z");

test("CurrencyAPI accepts daily KRW/PHP rates but rejects data older than 26 hours", async () => {
  let request;
  const rate = await getLatestKrwPhpRate(
    "test-key",
    async (url, options) => {
      request = { url: url.toString(), options };
      return Response.json({
        meta: { last_updated_at: new Date(now - 24 * 60 * 60_000).toISOString() },
        data: { PHP: { code: "PHP", value: 0.0425 } }
      });
    },
    now
  );
  assert.deepEqual(rate, {
    phpPerKrw: 0.0425,
    updatedAt: new Date(now - 24 * 60 * 60_000).toISOString()
  });
  assert.equal(request.url, "https://api.currencyapi.com/v3/latest?base_currency=KRW&currencies=PHP");
  assert.equal(request.options.headers.apikey, "test-key");
  assert.equal(new URL(request.url).searchParams.has("apikey"), false);

  await assert.rejects(
    getLatestKrwPhpRate("test-key", async () => Response.json({
      meta: { last_updated_at: new Date(now - 27 * 60 * 60_000).toISOString() },
      data: { PHP: { code: "PHP", value: 0.0425 } }
    }), now),
    (error) => error instanceof FxQuoteError && error.status === 502
  );
});

test("CurrencyAPI errors fail closed with actionable configuration and quota messages", async () => {
  await assert.rejects(
    getLatestKrwPhpRate(null),
    (error) => error instanceof FxQuoteError && error.status === 503 && /CURRENCYAPI_KEY/.test(error.message)
  );
  await assert.rejects(
    getLatestKrwPhpRate("test-key", async () => new Response("{}", { status: 429 })),
    (error) => error instanceof FxQuoteError && /request limit/.test(error.message)
  );
  await assert.rejects(
    getLatestKrwPhpRate("test-key", async () => new Response("{}", { status: 403 })),
    (error) => error instanceof FxQuoteError && /not authorized/.test(error.message)
  );
});

test("marketing reference rates combine KRW/PHP and live USDT/USD prices", async () => {
  const fxRequests = [];
  const marketRequests = [];
  const rates = await getLatestMarketingKrwRates(
    "test-key",
    async (url, options) => {
      const requestUrl = new URL(url);
      fxRequests.push({ requestUrl, options });
      const currency = requestUrl.searchParams.get("base_currency");
      const value = currency === "KRW" ? 0.0425 : 58.5;
      return Response.json({
        meta: { last_updated_at: new Date(now - 60_000).toISOString() },
        data: { PHP: { code: "PHP", value } }
      });
    },
    async (url, options) => {
      marketRequests.push({ url: url.toString(), options });
      return Response.json({
        tether: { usd: 1.001, last_updated_at: Math.floor((now - 30_000) / 1000) }
      });
    },
    now
  );

  assert.equal(fxRequests.length, 2);
  assert.equal(fxRequests[0].options.headers.apikey, "test-key");
  assert.equal(fxRequests[1].options.headers.apikey, "test-key");
  assert.match(marketRequests[0].url, /^https:\/\/api\.coingecko\.com\/api\/v3\/simple\/price/);
  assert.equal(marketRequests[0].options.headers.apikey, undefined);
  assert.equal(rates.retrievedAt, new Date(now).toISOString());
  assert.equal(rates.rates.PHP.perKrw, 0.0425);
  assert.equal(rates.rates.USDT.perKrw, 0.0425 / 58.5 / 1.001);
  assert.equal(rates.rates.USDT.usdtUsdUpdatedAt, new Date(now - 30_000).toISOString());
});

test("marketing reference rates remain available for PHP when USDT providers fail", async () => {
  const fxFetch = async (url) => {
    const currency = new URL(url).searchParams.get("base_currency");
    if (currency === "USD") return new Response("{}", { status: 429 });
    return Response.json({
      meta: { last_updated_at: new Date(now).toISOString() },
      data: { PHP: { code: "PHP", value: 0.04 } }
    });
  };
  const rates = await getLatestMarketingKrwRates(
    "test-key",
    fxFetch,
    async () => new Response("{}", { status: 429 }),
    now
  );
  assert.equal(rates.rates.PHP.perKrw, 0.04);
  assert.equal("USDT" in rates.rates, false);
});

test("marketing reference rates fall back to daily Frankfurter data when CurrencyAPI is unavailable", async () => {
  let fallbackUrl;
  const rates = await getLatestMarketingKrwRates(
    "test-key",
    async () => new Response("{}", { status: 429 }),
    async () => Response.json({
      tether: { usd: 1.001, last_updated_at: Math.floor((now - 30_000) / 1000) }
    }),
    now,
    async (url) => {
      fallbackUrl = url.toString();
      return Response.json({
        base: "KRW",
        date: new Date(now).toISOString().slice(0, 10),
        rates: { PHP: 0.04, USD: 0.0007 }
      });
    }
  );

  assert.equal(
    fallbackUrl,
    "https://api.frankfurter.dev/v1/latest?base=KRW&symbols=PHP,USD"
  );
  assert.equal(rates.source, "Frankfurter");
  assert.equal(rates.rates.PHP.perKrw, 0.04);
  assert.equal(rates.rates.PHP.updatedAt, new Date(now).toISOString().slice(0, 10) + "T00:00:00.000Z");
  assert.equal(rates.rates.USDT.perKrw, 0.0007 / 1.001);
});

test("marketing fallback rejects stale daily rates", async () => {
  const staleDate = new Date(now - 5 * 24 * 60 * 60_000).toISOString().slice(0, 10);
  await assert.rejects(
    getLatestMarketingKrwRates(
      "test-key",
      async () => new Response("{}", { status: 429 }),
      async () => new Response("{}", { status: 429 }),
      now,
      async () => Response.json({
        base: "KRW",
        date: staleDate,
        rates: { PHP: 0.04, USD: 0.0007 }
      })
    ),
    (error) => error instanceof FxQuoteError && error.status === 502 && /stale KRW rates/.test(error.message)
  );
});

test("marketing reference rates reject stale or unavailable USDT market prices without losing PHP", async () => {
  const fxFetch = async (url) => Response.json({
    meta: { last_updated_at: new Date(now).toISOString() },
    data: { PHP: { code: "PHP", value: new URL(url).searchParams.get("base_currency") === "KRW" ? 0.04 : 58 } }
  });
  const staleUsdt = await getLatestMarketingKrwRates(
    "test-key",
    fxFetch,
    async () => Response.json({
      tether: { usd: 1, last_updated_at: Math.floor((now - 6 * 60 * 1000) / 1000) }
    }),
    now
  );
  assert.equal(staleUsdt.rates.PHP.perKrw, 0.04);
  assert.equal("USDT" in staleUsdt.rates, false);
});

test("KRW/PHP quote is rounded to cents, bound to the merchant, and expires after five minutes", () => {
  const quote = createKrwPhpQuote({
    amountKrw: 12000,
    phpPerKrw: 0.0425,
    rateUpdatedAt: new Date(now - 60_000).toISOString(),
    subject: "merchant-a",
    signingKey,
    now
  });
  assert.equal(quote.amountPhp, 510);
  assert.equal(quote.expiresAt - quote.issuedAt, KRW_PHP_QUOTE_TTL_MS);
  assert.equal(verifyKrwPhpQuote(quote.quoteToken, "merchant-a", signingKey, now).amountPhp, 510);
  assert.throws(
    () => verifyKrwPhpQuote(quote.quoteToken, "merchant-b", signingKey, now),
    (error) => error instanceof FxQuoteError && error.status === 400
  );
  assert.throws(
    () => verifyKrwPhpQuote(quote.quoteToken, "merchant-a", signingKey, quote.expiresAt),
    (error) => error instanceof FxQuoteError && error.status === 410
  );
  const [encoded, signature] = quote.quoteToken.split(".");
  const tamperedSignature = (signature[0] === "A" ? "B" : "A") + signature.slice(1);
  const tampered = `${encoded}.${tamperedSignature}`;
  assert.throws(
    () => verifyKrwPhpQuote(tampered, "merchant-a", signingKey, now),
    (error) => error instanceof FxQuoteError && error.status === 400
  );
});

test("USD, EUR, and CNY FX quotes convert the exact customer price into locked PHP", async () => {
  let requestUrl;
  const rate = await getLatestPhpRate(
    "EUR",
    "test-key",
    async (url) => {
      requestUrl = url.toString();
      return Response.json({
        meta: { last_updated_at: new Date(now).toISOString() },
        data: { PHP: { code: "PHP", value: 62 } }
      });
    },
    now
  );
  assert.equal(requestUrl, "https://api.currencyapi.com/v3/latest?base_currency=EUR&currencies=PHP");
  assert.equal(rate.phpPerUnit, 62);

  const quote = createFxPhpQuote({
    amount: 10.5,
    currency: "EUR",
    phpPerUnit: rate.phpPerUnit,
    rateUpdatedAt: rate.updatedAt,
    subject: "merchant-a",
    signingKey,
    now
  });
  assert.equal(quote.amountPhp, 651);
  assert.equal(verifyFxPhpQuote(quote.quoteToken, "merchant-a", signingKey, now).customerCurrency, "EUR");
  const cnyQuote = createFxPhpQuote({
    amount: 15,
    currency: "CNY",
    phpPerUnit: 7.8,
    rateUpdatedAt: rate.updatedAt,
    subject: "merchant-a",
    signingKey,
    now
  });
  assert.equal(cnyQuote.amountPhp, 117);
  assert.equal(verifyFxPhpQuote(cnyQuote.quoteToken, "merchant-a", signingKey, now).customerCurrency, "CNY");
  assert.throws(
    () => verifyKrwPhpQuote(quote.quoteToken, "merchant-a", signingKey, now),
    (error) => error instanceof FxQuoteError && error.status === 400
  );
  assert.throws(
    () => createFxPhpQuote({
      amount: 1.5,
      currency: "KRW",
      phpPerUnit: 0.0425,
      rateUpdatedAt: rate.updatedAt,
      subject: "merchant-a",
      signingKey,
      now
    }),
    (error) => error instanceof FxQuoteError && error.status === 400
  );
});
