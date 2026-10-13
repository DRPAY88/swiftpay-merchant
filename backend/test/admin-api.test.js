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

  const wallet = await fetch(`${baseUrl}/v1/wallet/balance`, {
    headers: { authorization: "Bearer merchant-token" }
  });
  assert.equal(wallet.status, 200);
  assert.deepEqual(await wallet.json(), {
    balance: 75,
    adjustedBalance: 175,
    accountNumber: "000123"
  });

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

test("internal server errors are logged with redacted diagnostics while responses stay generic", async () => {
  const originalAdjustmentTotal = store.adjustmentTotal;
  const originalConsoleError = console.error;
  const logLines = [];
  store.adjustmentTotal = async () => {
    throw new Error(`Database connection failed using ${config.swiftPaySecretKey} Bearer sensitive-token`);
  };
  console.error = (...values) => logLines.push(values.join(" "));
  try {
    const response = await fetch(`${baseUrl}/v1/wallet/balance`, {
      headers: { authorization: "Bearer merchant-token" }
    });
    assert.equal(response.status, 500);
    assert.deepEqual(await response.json(), { error: "Internal server error" });
  } finally {
    store.adjustmentTotal = originalAdjustmentTotal;
    console.error = originalConsoleError;
  }
  assert.equal(logLines.length, 1);
  assert.match(logLines[0], /GET/);
  assert.match(logLines[0], /\/v1\/wallet\/balance/);
  assert.match(logLines[0], /Database connection failed/);
  assert.match(logLines[0], /Bearer \[REDACTED\]/);
  assert.equal(logLines[0].includes(config.swiftPaySecretKey), false);
  assert.equal(logLines[0].includes("sensitive-token"), false);
});

test("merchant wallet payment history includes PHP payment webhook receipts", async () => {
  await store.recordWebhookEvent({
    eventKey: "payment:00000000-0000-4000-8000-000000000004",
    eventType: "payment",
    paymentId: "00000000-0000-4000-8000-000000000004",
    referenceNo: "ORDER-PHP-2",
    status: "EXECUTED",
    amount: 825,
    currency: "PHP"
  });
  const payments = await fetch(`${baseUrl}/v1/wallet/payment-transactions`, {
    headers: { authorization: "Bearer merchant-token" }
  });
  assert.equal(payments.status, 200);
  const paymentTransactions = await payments.json();
  assert.deepEqual(paymentTransactions.map(({ id, referenceNo, amount, currency, status }) => ({
    id,
    referenceNo,
    amount,
    currency,
    status
  })), [{
    id: "00000000-0000-4000-8000-000000000004",
    referenceNo: "ORDER-PHP-2",
    amount: 825,
    currency: "PHP",
    status: "EXECUTED"
  }]);
});

test("super admins can record verified manual bank deposits for merchant wallet history", async () => {
  const adminHeaders = {
    authorization: "******",
    "content-type": "application/json"
  };
  const merchantHeaders = { authorization: "******" };

  const forbidden = await fetch(`${baseUrl}/v1/admin/manual-bank-deposits`, {
    method: "POST",
    headers: {
      authorization: "******",
      "content-type": "application/json"
    },
    body: JSON.stringify({
      amount: 500,
      bankName: "Example Bank",
      referenceNo: "BANK-REF-1"
    })
  });
  assert.equal(forbidden.status, 403);

  const invalid = await fetch(`${baseUrl}/v1/admin/manual-bank-deposits`, {
    method: "POST",
    headers: adminHeaders,
    body: JSON.stringify({
      amount: -500,
      bankName: "Example Bank",
      referenceNo: "BANK-REF-1"
    })
  });
  assert.equal(invalid.status, 400);

  const recorded = await fetch(`${baseUrl}/v1/admin/manual-bank-deposits`, {
    method: "POST",
    headers: adminHeaders,
    body: JSON.stringify({
      amount: 500,
      bankName: "Example Bank",
      referenceNo: "BANK-REF-1",
      notes: "Verified incoming transfer"
    })
  });
  assert.equal(recorded.status, 201);
  const created = await recorded.json();
  assert.equal(created.amount, 500);
  assert.equal(created.bankName, "Example Bank");
  assert.equal(created.referenceNo, "BANK-REF-1");
  assert.equal(created.affectsProviderFunds, false);

  const history = await fetch(`${baseUrl}/v1/wallet/manual-deposits`, {
    headers: merchantHeaders
  });
  assert.equal(history.status, 200);
  assert.deepEqual(await history.json(), [{
    id: created.id,
    amount: 500,
    bankName: "Example Bank",
    referenceNo: "BANK-REF-1",
    notes: "Verified incoming transfer",
    createdAt: created.createdAt
  }]);

  assert.ok((await store.listAudit(10)).some((entry) => entry.action === "manual_bank_deposit.recorded"));
});
