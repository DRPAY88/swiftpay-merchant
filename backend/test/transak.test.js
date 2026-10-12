import assert from "node:assert/strict";
import { test } from "node:test";
import { createTransakWidgetService } from "../src/transak.js";

const config = {
  sessionSigningKey: "session-signing-key-that-is-at-least-32-bytes",
  transak: {
    apiKey: "partner-api-key",
    apiSecret: "partner-api-secret",
    environment: "staging"
  }
};

test("Transak widget sessions keep credentials server-side and pin PHP USDT TRON parameters", async () => {
  const requests = [];
  const service = createTransakWidgetService(config, async (url, options) => {
    requests.push({ url, options });
    if (url.endsWith("refresh-token")) {
      return new Response(JSON.stringify({
        data: { accessToken: "short-lived-access-token", expiresAt: Math.floor(Date.now() / 1000) + 3600 }
      }), { status: 200 });
    }
    return new Response(JSON.stringify({
      data: { widgetUrl: "https://global-stg.transak.com/?sessionId=one-time-session" }
    }), { status: 200 });
  });

  const result = await service.createWidgetUrl({
    mode: "BUY",
    amount: 2500,
    walletAddress: "T123456789ABCDEFGHJKLMNPQRSTUVWXYZabc",
    userIp: "203.0.113.10",
    customerId: "merchant@example.com"
  });

  assert.equal(result.widgetUrl, "https://global-stg.transak.com/?sessionId=one-time-session");
  assert.match(result.partnerOrderId, /^[0-9a-f-]{36}$/);
  assert.equal(requests.length, 2);
  assert.equal(requests[0].options.headers["api-secret"], "partner-api-secret");
  assert.equal(requests[1].options.headers["x-user-ip"], "203.0.113.10");
  const widgetParams = JSON.parse(requests[1].options.body).widgetParams;
  assert.equal(widgetParams.productsAvailed, "BUY");
  assert.equal(widgetParams.countryCode, "PH");
  assert.equal(widgetParams.fiatCurrency, "PHP");
  assert.equal(widgetParams.cryptoCurrencyCode, "USDT");
  assert.equal(widgetParams.network, "tron");
  assert.equal(widgetParams.walletAddress, "T123456789ABCDEFGHJKLMNPQRSTUVWXYZabc");
  assert.equal(widgetParams.disableWalletAddressForm, undefined);
  assert.equal(widgetParams.fiatAmount, 2500);
  assert.equal(widgetParams.cryptoAmount, undefined);
  assert.notEqual(widgetParams.partnerCustomerId, "merchant@example.com");
});

test("Transak sell sessions use crypto amount and cached partner access token", async () => {
  const requests = [];
  const service = createTransakWidgetService(config, async (url, options) => {
    requests.push({ url, options });
    if (url.endsWith("refresh-token")) {
      return new Response(JSON.stringify({
        data: { accessToken: "short-lived-access-token", expiresAt: Math.floor(Date.now() / 1000) + 3600 }
      }), { status: 200 });
    }
    return new Response(JSON.stringify({
      data: { widgetUrl: "https://global-stg.transak.com/?sessionId=sell-session" }
    }), { status: 200 });
  });

  await service.createWidgetUrl({ mode: "SELL", amount: 15.25, userIp: "2001:db8::1", customerId: "merchant-1" });
  await service.createWidgetUrl({ mode: "SELL", amount: 20, userIp: "2001:db8::1", customerId: "merchant-1" });

  assert.equal(requests.filter(({ url }) => url.endsWith("refresh-token")).length, 1);
  const sellParams = JSON.parse(requests[1].options.body).widgetParams;
  assert.equal(sellParams.productsAvailed, "SELL");
  assert.equal(sellParams.cryptoAmount, 15.25);
  assert.equal(sellParams.fiatAmount, undefined);
});

test("Transak widget service fails closed when partner credentials are missing", async () => {
  const service = createTransakWidgetService({ ...config, transak: { environment: "staging" } });
  await assert.rejects(
    service.createWidgetUrl({
      mode: "BUY",
      amount: 1000,
      walletAddress: "T123456789ABCDEFGHJKLMNPQRSTUVWXYZabc",
      userIp: "203.0.113.10",
      customerId: "merchant-1"
    }),
    { status: 503, message: "USDT checkout is not configured for this service" }
  );
});

test("Transak widget URL is restricted to the selected environment host", async () => {
  const service = createTransakWidgetService(config, async (url) => new Response(JSON.stringify(
    url.endsWith("refresh-token")
      ? { data: { accessToken: "short-lived-access-token", expiresAt: Math.floor(Date.now() / 1000) + 3600 } }
      : { data: { widgetUrl: "https://evil.example/checkout" } }
  ), { status: 200 }));
  await assert.rejects(
    service.createWidgetUrl({
      mode: "BUY",
      amount: 1000,
      walletAddress: "T123456789ABCDEFGHJKLMNPQRSTUVWXYZabc",
      userIp: "203.0.113.10",
      customerId: "merchant-1"
    }),
    { status: 502, message: "Transak did not return a valid checkout URL" }
  );
});