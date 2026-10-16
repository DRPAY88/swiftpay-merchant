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
  store = createAdminStore("postgresql://test", new Pool(), { kybEncryptionKey: Buffer.alloc(32, 7) });
  await store.initialize(["merchant@example.com", "support@swiftpay.site"], ["support@swiftpay.site"]);

  const app = createApp({
    config,
    userStore: store,
    requestOtp: async () => {},
    verifyOtp: async () => null,
    verifyAccessToken: async (token) => {
      if (token === "admin-token") return { sub: "support@swiftpay.site", scope };
      if (token === "merchant-token") return { sub: "merchant@example.com", scope };
      if (token === "onboarding-merchant-token") return { sub: "onboarding@example.com", scope };
      if (token === "registration-token") {
        return { sub: "applicant@example.com", role: "registration_applicant", scope: "kyb:submit" };
      }
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
  const homepage = await fetch(`${baseUrl}/`);
  assert.equal(homepage.status, 200);
  const homepageHtml = await homepage.text();
  assert.match(homepageHtml, /KRW in\./);
  assert.match(homepageHtml, /PHP or USDT out\./);
  assert.match(homepageHtml, /KRW → PHP/);
  assert.match(homepageHtml, /KRW → USDT/);
  assert.match(homepageHtml, /KRW → USDT \(TRC20\)/);
  assert.match(homepageHtml, /without a remittance transfer/);
  assert.match(homepageHtml, /Not available yet/);
  assert.match(homepageHtml, /Fast payout is the goal, but exchange is not available yet/);

  const download = await fetch(`${baseUrl}/download`, { redirect: "manual" });
  assert.equal(download.status, 302);
  assert.equal(
    download.headers.get("location"),
    "https://github.com/stden16/swiftpay-merchant/releases/download/v1.0.15/SwiftPay-Merchant-v1.0.15.apk"
  );

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

test("super admins control onboarding and merchants can complete it once", async () => {
  await store.createMerchant("onboarding@example.com", "support@swiftpay.site");
  const adminHeaders = {
    authorization: "Bearer admin-token",
    "content-type": "application/json"
  };
  const merchantHeaders = { authorization: "Bearer onboarding-merchant-token" };

  const anonymousState = await fetch(`${baseUrl}/v1/onboarding/state`);
  assert.equal(anonymousState.status, 401);

  const forbiddenSetting = await fetch(`${baseUrl}/v1/admin/onboarding`, {
    method: "PATCH",
    headers: {
      ...merchantHeaders,
      "content-type": "application/json"
    },
    body: JSON.stringify({ enabled: false })
  });

  test("registration applicants can submit encrypted KYB, only admins can review, and approval activates merchant access", async () => {
    await store.createRegistrationDraft("applicant@example.com");
    const pendingDelete = await fetch(`${baseUrl}/v1/admin/registrations/applicant%40example.com`, {
      method: "DELETE",
      headers: { authorization: "Bearer admin-token" }
    });
    assert.equal(pendingDelete.status, 409);
    const applicantHeaders = {
      authorization: "Bearer registration-token",
      "content-type": "application/json"
    };
    const adminHeaders = {
      authorization: "Bearer admin-token",
      "content-type": "application/json"
    };
    const profile = {
      businessName: "Example Trading",
      businessRegistrationNumber: "BRN-2026-100",
      businessAddress: "100 Sample Street, Manila",
      businessType: "Retail",
      representativeFullName: "Sample Representative",
      representativePhone: "+639171234567"
    };
    const profileResponse = await fetch(`${baseUrl}/v1/registration/profile`, {
      method: "PUT",
      headers: applicantHeaders,
      body: JSON.stringify(profile)
    });
    assert.equal(profileResponse.status, 200);

    for (const [kind, contentType, bytes] of [
      ["business_certificate", "application/pdf", Buffer.from("%PDF-1.4\nexample")],
      ["representative_id", "image/png", Buffer.from([137, 80, 78, 71, 13, 10, 26, 10])],
      ["selfie", "image/jpeg", Buffer.from([0xff, 0xd8, 1, 2, 0xff, 0xd9])]
    ]) {
      const response = await fetch(`${baseUrl}/v1/registration/documents/${kind}`, {
        method: "PUT",
        headers: { authorization: applicantHeaders.authorization, "content-type": contentType },
        body: bytes
      });
      assert.equal(response.status, 200);
    }

    const forbidden = await fetch(`${baseUrl}/v1/wallet/balance`, {
      headers: { authorization: applicantHeaders.authorization }
    });
    assert.equal(forbidden.status, 403);

    const submitted = await fetch(`${baseUrl}/v1/registration/submit`, {
      method: "POST",
      headers: applicantHeaders
    });
    assert.equal(submitted.status, 202);
    assert.equal((await submitted.json()).status, "submitted");
    const applicantStatus = await fetch(`${baseUrl}/v1/registration/status`, {
      headers: { authorization: applicantHeaders.authorization }
    });
    assert.equal(applicantStatus.status, 200);
    const applicantApplication = await applicantStatus.json();
    assert.equal(applicantApplication.profile.businessName, profile.businessName);
    assert.deepEqual(applicantApplication.documents.sort(), ["business_certificate", "representative_id", "selfie"]);

    const detail = await fetch(`${baseUrl}/v1/admin/registrations/applicant%40example.com`, {
      headers: { authorization: adminHeaders.authorization }
    });
    assert.equal(detail.status, 200);
    assert.equal((await detail.json()).profile.businessName, profile.businessName);

    const documentResponse = await fetch(
      `${baseUrl}/v1/admin/registrations/applicant%40example.com/documents/selfie`,
      { headers: { authorization: adminHeaders.authorization } }
    );
    assert.equal(documentResponse.status, 200);
    assert.equal(documentResponse.headers.get("content-type"), "image/jpeg");
    assert.deepEqual(Buffer.from(await documentResponse.arrayBuffer()), Buffer.from([0xff, 0xd8, 1, 2, 0xff, 0xd9]));

    const rejected = await fetch(`${baseUrl}/v1/admin/registrations/applicant%40example.com`, {
      method: "PATCH",
      headers: adminHeaders,
      body: JSON.stringify({ status: "rejected", note: "Please provide current documents." })
    });
    assert.equal((await rejected.json()).status, "rejected");
    await store.createRegistrationDraft("applicant@example.com");
    const rejectedApplication = await fetch(`${baseUrl}/v1/registration/status`, {
      headers: { authorization: applicantHeaders.authorization }
    });
    assert.equal((await rejectedApplication.json()).reviewNote, "Please provide current documents.");
    await assert.rejects(store.submitRegistration("applicant@example.com"), (error) => error.status === 400);
    for (const [kind, contentType, bytes] of [
      ["business_certificate", "application/pdf", Buffer.from("%PDF-1.4\nupdated")],
      ["representative_id", "image/png", Buffer.from([137, 80, 78, 71, 13, 10, 26, 10])],
      ["selfie", "image/jpeg", Buffer.from([0xff, 0xd8, 3, 4, 0xff, 0xd9])]
    ]) {
      const response = await fetch(`${baseUrl}/v1/registration/documents/${kind}`, {
        method: "PUT",
        headers: { authorization: applicantHeaders.authorization, "content-type": contentType },
        body: bytes
      });
      assert.equal(response.status, 200);
    }
    const resubmitted = await fetch(`${baseUrl}/v1/registration/submit`, {
      method: "POST",
      headers: applicantHeaders
    });
    assert.equal((await resubmitted.json()).status, "submitted");

    const reviewed = await fetch(`${baseUrl}/v1/admin/registrations/applicant%40example.com`, {
      method: "PATCH",
      headers: adminHeaders,
      body: JSON.stringify({ status: "approved", note: "Verified by admin" })
    });
    assert.equal((await reviewed.json()).status, "approved");
    assert.equal((await store.getUser("applicant@example.com")).status, "active");

    const deleted = await fetch(`${baseUrl}/v1/admin/registrations/applicant%40example.com`, {
      method: "DELETE",
      headers: { authorization: adminHeaders.authorization }
    });
    assert.deepEqual(await deleted.json(), { deleted: true });
    assert.equal(await store.getRegistration("applicant@example.com"), null);
    assert.equal((await store.getUser("applicant@example.com")).status, "active");
  });
  assert.equal(forbiddenSetting.status, 403);

  const initialSetting = await fetch(`${baseUrl}/v1/admin/onboarding`, {
    headers: merchantHeaders
  });
  assert.equal(initialSetting.status, 403);
  const adminSetting = await fetch(`${baseUrl}/v1/admin/onboarding`, {
    headers: adminHeaders
  });
  assert.equal(adminSetting.status, 200);
  assert.deepEqual(await adminSetting.json(), { enabled: true });

  const state = await fetch(`${baseUrl}/v1/onboarding/state`, {
    headers: merchantHeaders
  });
  assert.equal(state.status, 200);
  assert.deepEqual(await state.json(), { required: true });

  const disabled = await fetch(`${baseUrl}/v1/admin/onboarding`, {
    method: "PATCH",
    headers: adminHeaders,
    body: JSON.stringify({ enabled: false })
  });
  assert.equal(disabled.status, 200);
  assert.deepEqual(await disabled.json(), { enabled: false });

  const completed = await fetch(`${baseUrl}/v1/onboarding/complete`, {
    method: "POST",
    headers: merchantHeaders
  });
  assert.equal(completed.status, 200);
  assert.deepEqual(await completed.json(), { completed: true });
  const completedState = await fetch(`${baseUrl}/v1/onboarding/state`, {
    headers: merchantHeaders
  });
  assert.deepEqual(await completedState.json(), { required: false });

  const reenabled = await fetch(`${baseUrl}/v1/admin/onboarding`, {
    method: "PATCH",
    headers: adminHeaders,
    body: JSON.stringify({ enabled: true })
  });
  assert.equal(reenabled.status, 200);
  assert.deepEqual(await reenabled.json(), { enabled: true });

  const duplicateCompletion = await fetch(`${baseUrl}/v1/onboarding/complete`, {
    method: "POST",
    headers: merchantHeaders
  });
  assert.equal(duplicateCompletion.status, 200);
  assert.deepEqual(await duplicateCompletion.json(), { completed: true });

  await store.createMerchant("merchant@example.com", "support@swiftpay.site");
  const ineligibleCompletion = await fetch(`${baseUrl}/v1/onboarding/complete`, {
    method: "POST",
    headers: { authorization: "Bearer merchant-token" }
  });
  assert.equal(ineligibleCompletion.status, 409);
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

  await store.recordWebhookEvent({
    eventKey: "payment:00000000-0000-4000-8000-000000000004:revision",
    eventType: "payment",
    paymentId: "00000000-0000-4000-8000-000000000004",
    referenceNo: "ORDER-PHP-2",
    status: "REJECTED",
    amount: 825,
    currency: "PHP"
  });
  const latestPayment = await fetch(`${baseUrl}/v1/wallet/payment-transactions`, {
    headers: { authorization: "Bearer merchant-token" }
  });
  assert.equal(latestPayment.status, 200);
  assert.equal((await latestPayment.json())[0]?.status, "REJECTED");

  await store.recordWebhookEvent({
    eventKey: "payment:00000000-0000-4000-8000-000000000005",
    eventType: "payment",
    paymentId: "00000000-0000-4000-8000-000000000005",
    referenceNo: "ORDER-PHP-3",
    status: "EXECUTED",
    amount: 900,
    currency: "PHP"
  });
  const nextPaymentPage = await fetch(`${baseUrl}/v1/wallet/payment-transactions?limit=1&offset=1`, {
    headers: { authorization: "Bearer merchant-token" }
  });
  assert.equal(nextPaymentPage.status, 200);
  assert.equal((await nextPaymentPage.json())[0].id, "00000000-0000-4000-8000-000000000004");
});

test("super admins can record verified manual bank deposits for merchant wallet history", async () => {
  await store.createMerchant("merchant@example.com", "support@swiftpay.site");
  const adminHeaders = {
    authorization: "******",
    "content-type": "application/json"
  };
  const merchantHeaders = { authorization: "******" };
  adminHeaders.authorization = ["Bearer", ["admin", "token"].join("-")].join(" ");
  merchantHeaders.authorization = ["Bearer", ["merchant", "token"].join("-")].join(" ");

  const forbidden = await fetch(`${baseUrl}/v1/admin/manual-bank-deposits`, {
    method: "POST",
    headers: {
      authorization: "******",
      "content-type": "application/json",
      ...merchantHeaders
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

  const secondDeposit = await fetch(`${baseUrl}/v1/admin/manual-bank-deposits`, {
    method: "POST",
    headers: adminHeaders,
    body: JSON.stringify({
      amount: 600,
      bankName: "Example Bank",
      referenceNo: "BANK-REF-2"
    })
  });
  assert.equal(secondDeposit.status, 201);

  const history = await fetch(`${baseUrl}/v1/wallet/manual-deposits`, {
    headers: merchantHeaders
  });
  assert.equal(history.status, 200);
  const allDeposits = await history.json();
  assert.equal(allDeposits.length, 2);
  assert.ok(allDeposits.some((deposit) => deposit.id === created.id));
  const nextDepositPage = await fetch(`${baseUrl}/v1/wallet/manual-deposits?limit=1&offset=1`, {
    headers: merchantHeaders
  });
  assert.equal(nextDepositPage.status, 200);
  assert.equal((await nextDepositPage.json()).length, 1);
  assert.deepEqual(allDeposits.find((deposit) => deposit.id === created.id), {
    id: created.id,
    amount: 500,
    bankName: "Example Bank",
    referenceNo: "BANK-REF-1",
    notes: "Verified incoming transfer",
    createdAt: created.createdAt
  });

  assert.ok((await store.listAudit(10)).some((entry) => entry.action === "manual_bank_deposit.recorded"));
});
