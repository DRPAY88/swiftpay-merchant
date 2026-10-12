import assert from "node:assert/strict";
import { after, before, test } from "node:test";
import { newDb } from "pg-mem";
import { createAdminStore } from "../src/admin-store.js";
import { createApp } from "../src/app.js";

const config = {
  swiftPayBaseUrl: new URL("https://provider.example/api/"),
  swiftPayAccessKey: "A".repeat(32),
  swiftPaySecretKey: "S".repeat(32)
};
const scope = "swiftpay:payments:read swiftpay:payments:write swiftpay:disbursements:read swiftpay:disbursements:write";
let store;
let server;
let baseUrl;

before(async () => {
  const database = newDb();
  const { Pool } = database.adapters.createPg();
  store = createAdminStore("postgresql://test", new Pool());
  await store.initialize(["merchant@example.com", "support@swiftpay.site"], ["support@swiftpay.site"]);

  const app = createApp({
    config,
    userStore: store,
    requestOtp: async () => {},
    verifyOtp: async () => null,
    verifyAccessToken: async (token) => {
      if (token === "admin-token") return { sub: "support@swiftpay.site", scope };
      if (token === "merchant-token") return { sub: "merchant@example.com", scope };
      throw new Error("invalid token");
    },
    fetchImpl: async (url) => {
      if (url.pathname.endsWith("/disbursements/send")) {
        return new Response(JSON.stringify({ id: "9d51c8e2-3211-4c01-a9a1-19a9a8bd5c01" }), { status: 200 });
      }
      return new Response(JSON.stringify({ balance: 75, number: "000123" }), { status: 200 });
    }
  });
  server = app.listen(0, "127.0.0.1");
  await new Promise((resolve) => server.once("listening", resolve));
  baseUrl = `http://127.0.0.1:${server.address().port}`;
});

after(async () => {
  await new Promise((resolve, reject) => server.close((error) => error ? reject(error) : resolve()));
  await store.close();
});

test("admin dashboard is served while its APIs require a signed super-admin role", async () => {
  const page = await fetch(`${baseUrl}/admin/`);
  assert.equal(page.status, 200);
  assert.match(await page.text(), /SwiftPay Super Admin/);

  const anonymous = await fetch(`${baseUrl}/v1/admin/users`);
  assert.equal(anonymous.status, 401);

  const merchant = await fetch(`${baseUrl}/v1/admin/users`, {
    headers: { authorization: "Bearer merchant-token" }
  });
  assert.equal(merchant.status, 403);
  assert.deepEqual(await merchant.json(), { error: "super_admin_required" });

  const admin = await fetch(`${baseUrl}/v1/admin/users`, {
    headers: { authorization: "Bearer admin-token" }
  });
  assert.equal(admin.status, 200);
  assert.equal((await admin.json()).length, 2);
});

test("admin merchant lifecycle suspends, removes, and reactivates sign-in access", async () => {
  const headers = {
    authorization: "Bearer admin-token",
    "content-type": "application/json"
  };
  const created = await fetch(`${baseUrl}/v1/admin/users`, {
    method: "POST",
    headers,
    body: JSON.stringify({ email: "new-merchant@example.com" })
  });
  assert.equal(created.status, 201);

  const suspended = await fetch(`${baseUrl}/v1/admin/users/new-merchant%40example.com`, {
    method: "PATCH",
    headers,
    body: JSON.stringify({ status: "suspended" })
  });
  assert.equal((await suspended.json()).status, "suspended");
  assert.equal((await store.getUser("new-merchant@example.com")).status, "suspended");

  const selfChange = await fetch(`${baseUrl}/v1/admin/users/support%40swiftpay.site`, {
    method: "PATCH",
    headers,
    body: JSON.stringify({ status: "removed" })
  });
  assert.equal(selfChange.status, 400);

  const removed = await fetch(`${baseUrl}/v1/admin/users/new-merchant%40example.com`, {
    method: "PATCH",
    headers,
    body: JSON.stringify({ status: "removed" })
  });
  assert.equal((await removed.json()).status, "removed");
});

test("internal adjustments are audited but do not affect the provider balance; admin transfers are audited", async () => {
  const headers = {
    authorization: "Bearer admin-token",
    "content-type": "application/json"
  };
  const adjustment = await fetch(`${baseUrl}/v1/admin/balance-adjustments`, {
    method: "POST",
    headers,
    body: JSON.stringify({ amount: 100, reason: "Internal reconciliation" })
  });
  assert.equal(adjustment.status, 201);
  assert.equal((await adjustment.json()).affectsProviderFunds, false);

  const transfer = await fetch(`${baseUrl}/v1/disbursements`, {
    method: "POST",
    headers,
    body: JSON.stringify({
      amount: 25,
      referenceNo: "ADMIN-TEST-1",
      channel: "INSTAPAY",
      institutionCode: "TESTBANK",
      recipientInformation: { accountNumber: "123456", fullName: "Test Merchant" }
    })
  });
  assert.equal(transfer.status, 202);
  const audit = await store.listAudit(10);
  const transferEvent = audit.find((entry) => entry.action === "transfer.accepted");
  assert.equal(transferEvent.details.amount, 25);
  assert.equal(JSON.stringify(transferEvent).includes("123456"), false);
  assert.equal(Number(await store.adjustmentTotal()), 100);
});
