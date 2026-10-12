import assert from "node:assert/strict";
import { after, before, test } from "node:test";
import { createApp } from "../src/app.js";
import { signOrder } from "../src/swiftpay.js";

const config = {
  swiftPayBaseUrl: new URL("https://provider.example/api/"),
  swiftPayAccessKey: "A".repeat(32),
  swiftPaySecretKey: "S".repeat(32)
};
const paymentId = "5008f066-d68a-4ef2-b794-8596bac268f6";
let server;
let baseUrl;
let fetchCount;
const events = new Map();

before(async () => {
  fetchCount = 0;
  const app = createApp({
    config,
    verifyAccessToken: async () => ({
      sub: "merchant@example.com",
      role: "merchant",
      scope: "swiftpay:payments:read"
    }),
    requestOtp: async () => {},
    verifyOtp: async () => null,
    userStore: {
      getUser: async () => ({ status: "active", role: "merchant" }),
      recordWebhookEvent: async (event) => events.set(event.eventKey, event),
      getPaymentWebhookStatus: async (id) => {
        const event = events.get(`payment:${id}`);
        return event ? { status: event.status } : null;
      }
    },
    fetchImpl: async () => {
      fetchCount += 1;
      return new Response(JSON.stringify("PENDING"), {
        status: 200,
        headers: { "content-type": "application/json" }
      });
    }
  });
  server = app.listen(0, "127.0.0.1");
  await new Promise((resolve) => server.once("listening", resolve));
  baseUrl = `http://127.0.0.1:${server.address().port}`;
});

after(async () => {
  await new Promise((resolve, reject) => server.close((error) => error ? reject(error) : resolve()));
});

function signedPaymentEvent(status = "EXECUTED") {
  const fields = {
    x_payment_id: paymentId,
    x_payment_status: status,
    x_reference_no: "ORDER-1",
    x_access_key: config.swiftPayAccessKey
  };
  return { ...fields, signature: signOrder(fields, config.swiftPaySecretKey) };
}

async function sendWebhook(fields) {
  const query = new URLSearchParams(fields);
  return fetch(`${baseUrl}/v1/webhooks/swiftpay?${query}`, { method: "POST" });
}

test("verified payment webhooks are idempotently stored and become the status source", async () => {
  const fields = signedPaymentEvent();
  const first = await sendWebhook(fields);
  assert.equal(first.status, 200);
  assert.deepEqual(await first.json(), { status: "accepted" });
  assert.equal(events.size, 1);

  const duplicate = await sendWebhook(fields);
  assert.equal(duplicate.status, 200);
  assert.equal(events.size, 1);

  const status = await fetch(`${baseUrl}/v1/payments/${paymentId}/status`, {
    headers: { authorization: "Bearer valid-test-token" }
  });
  assert.equal(status.status, 200);
  assert.deepEqual(await status.json(), {
    paymentId,
    status: "EXECUTED",
    source: "webhook"
  });
  assert.equal(fetchCount, 0);
});

test("webhook signature, access key and event schema are validated", async () => {
  events.clear();
  const tampered = signedPaymentEvent();
  tampered.x_payment_status = "REJECTED";
  assert.equal((await sendWebhook(tampered)).status, 401);

  const wrongKey = signedPaymentEvent();
  wrongKey.x_access_key = "B".repeat(32);
  wrongKey.signature = signOrder(wrongKey, config.swiftPaySecretKey);
  assert.equal((await sendWebhook(wrongKey)).status, 401);

  const invalidEvent = signedPaymentEvent("PENDING");
  invalidEvent.signature = signOrder(invalidEvent, config.swiftPaySecretKey);
  assert.equal((await sendWebhook(invalidEvent)).status, 400);
  assert.equal(events.size, 0);
});
