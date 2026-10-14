import assert from "node:assert/strict";
import { test } from "node:test";
import {
  createKrwPhpQuote,
  createFxPhpQuote,
  FxQuoteError,
  getLatestPhpRate,
  getLatestKrwPhpRate,
  KRW_PHP_QUOTE_TTL_MS,
  verifyFxPhpQuote,
  verifyKrwPhpQuote
} from "../src/fx-quotes.js";

const signingKey = "test-session-signing-key-at-least-32-bytes";
const now = Date.parse("2026-10-13T00:00:00.000Z");

test("CurrencyAPI fetches KRW/PHP rate with header auth and rejects stale quotes", async () => {
  let request;
  const rate = await getLatestKrwPhpRate(
    "test-key",
    async (url, options) => {
      request = { url: url.toString(), options };
      return Response.json({
        meta: { base_currency: "KRW", last_updated_at: new Date(now - 60_000).toISOString() },
        data: { PHP: { code: "PHP", value: 0.0425 } }
      });
    },
    now
  );
  assert.deepEqual(rate, {
    phpPerKrw: 0.0425,
    updatedAt: new Date(now - 60_000).toISOString()
  });
  assert.equal(request.url, "https://api.currencyapi.com/v3/latest?base_currency=KRW&currencies=PHP");
  assert.equal(request.options.headers.apikey, "test-key");
  assert.equal(new URL(request.url).searchParams.has("apikey"), false);

  await assert.rejects(
    getLatestKrwPhpRate("test-key", async () => Response.json({
      meta: { base_currency: "KRW", last_updated_at: new Date(now - 3 * 60 * 60_000).toISOString() },
      data: { PHP: { code: "PHP", value: 0.0425 } }
    }), now),
    (error) => error instanceof FxQuoteError && error.status === 502
  );
});

test("CurrencyAPI errors fail closed with actionable configuration and quota messages", async () => {
  await assert.rejects(
    getLatestKrwPhpRate(null),
    (error) => error instanceof FxQuoteError && error.status === 503 && /not configured/.test(error.message)
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
  const tampered = quote.quoteToken.replace(/.$/, quote.quoteToken.endsWith("A") ? "B" : "A");
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
        meta: { base_currency: "EUR", last_updated_at: new Date(now).toISOString() },
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
