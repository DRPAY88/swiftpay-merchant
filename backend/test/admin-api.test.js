import assert from "node:assert/strict";
import { after, before, test } from "node:test";
import { newDb } from "pg-mem";
import { createAdminStore } from "../src/admin-store.js";
import { createApp } from "../src/app.js";

const config = {
  swiftPayBaseUrl: new URL("https://provider.example/api/"),
  swiftPayAccessKey: "A".repeat(32),
  swiftPaySecretKey: "S".repeat(32),
  sessionSigningKey: "test-session-signing-key-with-sufficient-length",
  currencyApiKey: "test-currency-key"
};
const scope = "swiftpay:payments:read swiftpay:payments:write swiftpay:disbursements:read swiftpay:disbursements:write";
let store;
let server;
let baseUrl;
let marketRateRequestCount = 0;
let qrPaymentSequence = 0;
let fxResponseStatus = 200;
let apiClock = Date.now();

before(async () => {
  const database = newDb();
  const { Pool } = database.adapters.createPg();
  store = createAdminStore("postgresql://test", new Pool(), { kybEncryptionKey: Buffer.alloc(32, 7) });
  await store.initialize(["merchant@example.com", "support@swiftpay.site"], ["support@swiftpay.site"]);

  const app = createApp({
    config,
    userStore: store,
    requestOtp: async () => {},
    verifyOtp: async (email) => {
      await store.ensureEmailCustomer(email);
      return {
        accessToken: "customer-token",
        expiresInSeconds: 8 * 60 * 60,
        email,
        role: "customer"
      };
    },
    verifyAccessToken: async (token) => {
      if (token === "admin-token") return { sub: "support@swiftpay.site", scope };
      if (token === "merchant-token") return { sub: "merchant@example.com", scope };
      if (token === "customer-token") return { sub: "customer@example.com", scope };
      if (token === "second-customer-token") return { sub: "second-customer@example.com", scope };
      if (token === "krw-customer-token") return { sub: "krw-customer@example.com", scope };
      if (token === "onboarding-merchant-token") return { sub: "onboarding@example.com", scope };
      if (token === "registration-token") {
        return { sub: "applicant@example.com", role: "registration_applicant", scope: "kyb:submit" };
      }
      throw new Error("invalid token");
    },
    fetchImpl: async (url, options = {}) => {
      if (url.pathname.endsWith("/disbursements/send")) {
        return new Response(JSON.stringify({ id: "9d51c8e2-3211-4c01-a9a1-19a9a8bd5c01" }), { status: 200 });
      }
      if (url.pathname.endsWith("/bootstrap/qrph")) {
        const request = JSON.parse(options.body);
        return Response.json({
          paymentId: `wallet-topup-payment-${++qrPaymentSequence}`,
          paymentStatus: "PENDING",
          referenceNo: request.x_reference_no,
          amount: request.x_amount,
          currency: request.x_currency,
          qrCode: "test-qr-payload"
        });
      }
      if (url.pathname.endsWith("/payments/qrph/image")) {
        return new Response(Buffer.from("test-qr-image"), {
          status: 200,
          headers: { "content-type": "image/png" }
        });
      }
      if (url.pathname.endsWith("/payments/status")) {
        return Response.json("EXECUTED");
      }
      return new Response(JSON.stringify({ balance: 75, number: "000123" }), { status: 200 });
    },
    fxFetchImpl: async (url) => {
      const currency = new URL(url).searchParams.get("base_currency");
      const value = currency === "KRW" ? 0.04 : 58.4;
      return Response.json({
        meta: { last_updated_at: new Date().toISOString() },
        data: { PHP: { code: "PHP", value } }
      }, { status: fxResponseStatus });
    },
    referenceRateFetchImpl: async () => Response.json({
      base: "KRW",
      date: new Date(apiClock).toISOString().slice(0, 10),
      rates: { PHP: 0.041, USD: 0.00071 }
    }),
    marketRateFetchImpl: async () => {
      marketRateRequestCount += 1;
      return Response.json({
        tether: { usd: 1.002, last_updated_at: Math.floor(Date.now() / 1000) }
      });
    },
    clock: () => apiClock
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
  assert.match(homepageHtml, /Collect in KRW\./);
  assert.match(homepageHtml, /Track PHP clearly/);
  assert.match(homepageHtml, /KRW QR PAYMENTS · PERSONAL PHP WALLET/);
  assert.match(homepageHtml, /PHP appears in your personal wallet only after SwiftPay confirms settlement/);
  assert.match(homepageHtml, /KRW → PHP/);
  assert.match(homepageHtml, /KRW → USDT/);
  assert.match(homepageHtml, /KRW → USDT \(TRC20\)/);
  assert.match(homepageHtml, /PHP wallet top-ups available/);
  assert.match(homepageHtml, /USDT conversion is not available yet/);
  assert.match(homepageHtml, /Create an account with email verification/);
  assert.match(homepageHtml, /manage your own wallet and activity/);
  assert.match(homepageHtml, /no KYB or document submission/);
  assert.match(homepageHtml, /Create a KRW QR payment/);
  assert.match(homepageHtml, /Settlement must be verified first/);
  assert.match(homepageHtml, /app-managed ledger over pooled provider funds/);
  assert.match(homepageHtml, /id="promo"/);
  assert.equal((homepageHtml.match(/id="how-it-works"/g) || []).length, 1);
  assert.match(homepageHtml, /DRPAY IN 15 SECONDS/);
  assert.match(homepageHtml, /earlier DRPAY interface/);
  assert.match(homepageHtml, /Version 1\.0\.22/);
  assert.match(homepageHtml, /src="\/drpay-promo\.mp4\?v=2"/);
  assert.match(homepageHtml, /poster="\/drpay-promo-poster\.jpg\?v=2"/);
  assert.match(homepageHtml, /id="market-rates"/);
  assert.match(homepageHtml, /Reference estimate only—not a DRPAY offer/);
  assert.match(homepageHtml, /href="\/site\.css\?v=3"/);
  assert.match(homepageHtml, /src="\/rates\.js\?v=3"/);

  const ratesScript = await fetch(`${baseUrl}/rates.js`);
  assert.equal(ratesScript.status, 200);
  assert.match(await ratesScript.text(), /api\/marketing\/rates/);

  const promoVideo = await fetch(`${baseUrl}/drpay-promo.mp4`, { method: "HEAD" });
  assert.equal(promoVideo.status, 200);
  assert.equal(promoVideo.headers.get("content-type"), "video/mp4");
  assert.ok(Number(promoVideo.headers.get("content-length")) > 100_000);

  const promoPoster = await fetch(`${baseUrl}/drpay-promo-poster.jpg`, { method: "HEAD" });
  assert.equal(promoPoster.status, 200);
  assert.equal(promoPoster.headers.get("content-type"), "image/jpeg");

  const download = await fetch(`${baseUrl}/download`, { redirect: "manual" });
  assert.equal(download.status, 302);
  assert.equal(download.headers.get("location"), "/SwiftPay-Merchant-v1.0.22.apk");
  const apk = await fetch(`${baseUrl}/SwiftPay-Merchant-v1.0.22.apk`, { method: "HEAD" });
  assert.equal(apk.status, 200);
  assert.equal(apk.headers.get("content-type"), "application/vnd.android.package-archive");
  assert.ok(Number(apk.headers.get("content-length")) > 40_000_000);

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

test("merchant API keys are shown once, collection-scoped, rotatable, and revocable", async () => {
  const create = await fetch(`${baseUrl}/v1/merchant/api-keys`, {
    method: "POST",
    headers: {
      authorization: "Bearer merchant-token",
      "content-type": "application/json"
    },
    body: JSON.stringify({ name: "Store backend" })
  });
  assert.equal(create.status, 201);
  assert.equal(create.headers.get("cache-control"), "no-store");
  const created = await create.json();
  assert.match(created.apiKey, /^drpay_live_[a-f0-9]{16}_[a-f0-9]{64}$/);
  assert.equal(created.name, "Store backend");
  assert.deepEqual(created.scopes, ["collections:create", "collections:read"]);

  const list = await fetch(`${baseUrl}/v1/merchant/api-keys`, {
    headers: { authorization: "Bearer merchant-token" }
  });
  assert.equal(list.status, 200);
  const listed = await list.json();
  assert.equal(listed.length, 1);
  assert.equal(listed[0].id, created.id);
  assert.equal("apiKey" in listed[0], false);
  assert.equal("key_hash" in listed[0], false);

  const forbidden = await fetch(`${baseUrl}/v1/wallet/balance`, {
    headers: { authorization: `Bearer ${created.apiKey}` }
  });
  assert.equal(forbidden.status, 403);
  assert.deepEqual(await forbidden.json(), { error: "api_key_endpoint_not_allowed" });

  const collection = await fetch(`${baseUrl}/v1/payments/qrph`, {
    method: "POST",
    headers: {
      authorization: `Bearer ${created.apiKey}`,
      "content-type": "application/json"
    },
    body: JSON.stringify({})
  });
  assert.equal(collection.status, 400);

  const rotate = await fetch(`${baseUrl}/v1/merchant/api-keys/${created.id}/rotate`, {
    method: "POST",
    headers: {
      authorization: "Bearer merchant-token",
      "content-type": "application/json"
    },
    body: JSON.stringify({ name: "Store backend" })
  });
  assert.equal(rotate.status, 201);
  const rotated = await rotate.json();
  assert.notEqual(rotated.apiKey, created.apiKey);

  const oldKey = await fetch(`${baseUrl}/v1/payments/qrph/quote`, {
    method: "POST",
    headers: {
      authorization: `Bearer ${created.apiKey}`,
      "content-type": "application/json"
    },
    body: JSON.stringify({ amount: 100, currency: "USD" })
  });
  assert.equal(oldKey.status, 401);

  const quote = await fetch(`${baseUrl}/v1/payments/qrph/quote`, {
    method: "POST",
    headers: {
      authorization: `Bearer ${rotated.apiKey}`,
      "content-type": "application/json"
    },
    body: JSON.stringify({ amount: 100, currency: "USD" })
  });
  assert.equal(quote.status, 200);
  assert.equal((await quote.json()).settlementCurrency, "PHP");

  const revoke = await fetch(`${baseUrl}/v1/merchant/api-keys/${rotated.id}`, {
    method: "DELETE",
    headers: { authorization: "Bearer merchant-token" }
  });
  assert.equal(revoke.status, 204);
  const revokedKey = await fetch(`${baseUrl}/v1/payments/qrph/quote`, {
    method: "POST",
    headers: {
      authorization: `Bearer ${rotated.apiKey}`,
      "content-type": "application/json"
    },
    body: JSON.stringify({ amount: 100, currency: "USD" })
  });
  assert.equal(revokedKey.status, 401);
});

test("public marketing rates are sourced, cached, and labeled as reference values", async () => {
  const response = await fetch(`${baseUrl}/api/marketing/rates`);
  assert.equal(response.status, 200);
  assert.match(response.headers.get("cache-control"), /max-age=30/);
  const result = await response.json();
  assert.equal(result.rates.PHP.perKrw, 0.04);
  assert.equal(result.rates.USDT.perKrw, 0.04 / 58.4 / 1.002);
  assert.ok(result.rates.PHP.updatedAt);
  assert.ok(result.rates.USDT.usdtUsdUpdatedAt);
  assert.equal(marketRateRequestCount, 1);

  const cachedResponse = await fetch(`${baseUrl}/api/marketing/rates`);
  assert.equal(cachedResponse.status, 200);
  assert.equal(marketRateRequestCount, 1);
});

test("public marketing rates fall back when CurrencyAPI has reached its request limit", async () => {
  apiClock += 60_001;
  fxResponseStatus = 429;
  try {
    const response = await fetch(`${baseUrl}/api/marketing/rates`);
    assert.equal(response.status, 200);
    const result = await response.json();
    assert.equal(result.source, "Frankfurter");
    assert.equal(result.rates.PHP.perKrw, 0.041);
    assert.equal(result.rates.USDT.perKrw, 0.00071 / 1.002);
  } finally {
    fxResponseStatus = 200;
    apiClock += 60_001;
  }
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
    assert.equal((await store.getUser("applicant@example.com")).wallet_mode, "internal");

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

test("email customers receive isolated wallet balances and only settled QR top-ups can fund transfers", async () => {
  const anonymousRequest = await fetch(`${baseUrl}/v1/auth/request-otp`, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ email: "customer@example.com" })
  });
  assert.equal(anonymousRequest.status, 202);

  const verified = await fetch(`${baseUrl}/v1/auth/verify-otp`, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ email: "customer@example.com", code: "123456" })
  });
  assert.equal(verified.status, 200);
  assert.deepEqual(await verified.json(), {
    accessToken: "customer-token",
    expiresInSeconds: 8 * 60 * 60,
    email: "customer@example.com",
    role: "customer"
  });

  await store.ensureEmailCustomer("second-customer@example.com");
  const customerHeaders = {
    authorization: "Bearer customer-token",
    "content-type": "application/json"
  };
  const secondCustomerHeaders = {
    authorization: "Bearer second-customer-token",
    "content-type": "application/json"
  };
  const emptyBalance = await fetch(`${baseUrl}/v1/wallet/balance`, {
    headers: customerHeaders
  });
  assert.equal(emptyBalance.status, 200);
  assert.deepEqual(await emptyBalance.json(), {
    balance: 0,
    adjustedBalance: 0,
    accountNumber: null,
    currency: "PHP",
    walletMode: "personal"
  });

  const createdQr = await fetch(`${baseUrl}/v1/wallet/topups/qrph`, {
    method: "POST",
    headers: customerHeaders,
    body: JSON.stringify({ amount: 125 })
  });
  assert.equal(createdQr.status, 201);
  const qr = await createdQr.json();
  assert.equal(qr.amount, 125);
  assert.equal(qr.currency, "PHP");
  assert.equal(qr.status, "PENDING");
  assert.match(qr.referenceNo, /^WLT-/);
  assert.equal(qr.qrImageBase64, Buffer.from("test-qr-image").toString("base64"));
  assert.equal(Number(await store.getCustomerWalletBalance("customer@example.com")), 0);

  const reconciledTopup = await fetch(`${baseUrl}/v1/payments/${encodeURIComponent(qr.paymentId)}/status`, {
    headers: customerHeaders
  });
  assert.equal(reconciledTopup.status, 200);
  assert.deepEqual(await reconciledTopup.json(), {
    paymentId: qr.paymentId,
    status: "EXECUTED",
    source: "provider_reconciliation"
  });
  assert.equal(Number(await store.getCustomerWalletBalance("customer@example.com")), 125);

  await store.recordWebhookEvent({
    eventKey: `payment:${qr.paymentId}`,
    eventType: "payment",
    paymentId: qr.paymentId,
    referenceNo: qr.referenceNo,
    status: "EXECUTED",
    amount: 125,
    currency: "PHP"
  });
  const settledStatus = await fetch(`${baseUrl}/v1/wallet/topups/${encodeURIComponent(qr.referenceNo)}`, {
    headers: customerHeaders
  });
  assert.equal((await settledStatus.json()).status, "EXECUTED");

  const fundedBalance = await fetch(`${baseUrl}/v1/wallet/balance`, {
    headers: customerHeaders
  });
  assert.equal((await fundedBalance.json()).balance, 125);
  const isolatedHistory = await fetch(`${baseUrl}/v1/wallet/activity`, {
    headers: customerHeaders
  });
  assert.equal(isolatedHistory.status, 200);
  assert.deepEqual((await isolatedHistory.json()).map(({ amount, transactionType }) => [amount, transactionType]), [
    [125, "WALLET_TOP_UP"]
  ]);
  const otherHistory = await fetch(`${baseUrl}/v1/wallet/activity`, {
    headers: secondCustomerHeaders
  });
  assert.deepEqual(await otherHistory.json(), []);

  await store.ensureEmailCustomer("krw-customer@example.com");
  const krwCustomerHeaders = {
    authorization: "Bearer krw-customer-token",
    "content-type": "application/json"
  };
  const quoteResponse = await fetch(`${baseUrl}/v1/wallet/topups/qrph/quote`, {
    method: "POST",
    headers: krwCustomerHeaders,
    body: JSON.stringify({ amountKrw: 100_000 })
  });
  assert.equal(quoteResponse.status, 200);
  const quote = await quoteResponse.json();
  assert.equal(quote.customerAmountKrw, 100_000);
  assert.equal(quote.settlementAmountPhp, 4_000);
  assert.equal(quote.settlementCurrency, "PHP");

  const krwTopupResponse = await fetch(`${baseUrl}/v1/wallet/topups/qrph`, {
    method: "POST",
    headers: krwCustomerHeaders,
    body: JSON.stringify({ quoteToken: quote.quoteToken, paymentApp: "TOSS" })
  });
  assert.equal(krwTopupResponse.status, 201);
  const krwTopup = await krwTopupResponse.json();
  assert.equal(krwTopup.amount, 4_000);
  assert.equal(krwTopup.currency, "PHP");
  assert.equal(krwTopup.customerAmountKrw, 100_000);
  assert.equal(krwTopup.settlementCurrency, "PHP");
  assert.equal(Number(await store.getCustomerWalletBalance("krw-customer@example.com")), 0);
  const replayedQuote = await fetch(`${baseUrl}/v1/wallet/topups/qrph`, {
    method: "POST",
    headers: krwCustomerHeaders,
    body: JSON.stringify({ quoteToken: quote.quoteToken, paymentApp: "TOSS" })
  });
  assert.equal(replayedQuote.status, 409);

  await store.recordWebhookEvent({
    eventKey: `payment:${krwTopup.paymentId}`,
    eventType: "payment",
    paymentId: krwTopup.paymentId,
    referenceNo: krwTopup.referenceNo,
    status: "EXECUTED",
    amount: 4_000,
    currency: "PHP"
  });
  assert.equal(Number(await store.getCustomerWalletBalance("krw-customer@example.com")), 4_000);
  const krwWalletActivity = await fetch(`${baseUrl}/v1/wallet/activity`, {
    headers: krwCustomerHeaders
  });
  assert.equal((await krwWalletActivity.json())[0].description, "KRW exchange (₩100,000)");

  const transfer = await fetch(`${baseUrl}/v1/disbursements`, {
    method: "POST",
    headers: customerHeaders,
    body: JSON.stringify({
      amount: 50,
      referenceNo: "CUSTOMER-PAYOUT-1",
      channel: "INSTAPAY",
      institutionCode: "TESTBANK",
      recipientInformation: { accountNumber: "1234567890", fullName: "Jane Doe" }
    })
  });
  assert.equal(transfer.status, 202);
  assert.equal(Number(await store.getCustomerWalletBalance("customer@example.com")), 75);
  const unfundedTransfer = await fetch(`${baseUrl}/v1/disbursements`, {
    method: "POST",
    headers: secondCustomerHeaders,
    body: JSON.stringify({
      amount: 1,
      referenceNo: "CUSTOMER-PAYOUT-2",
      channel: "INSTAPAY",
      institutionCode: "TESTBANK",
      recipientInformation: { accountNumber: "0987654321", fullName: "John Doe" }
    })
  });
  assert.equal(unfundedTransfer.status, 409);
  assert.deepEqual(await unfundedTransfer.json(), { error: "insufficient_wallet_balance" });

  const hiddenMerchantDeposits = await fetch(`${baseUrl}/v1/wallet/manual-deposits`, {
    headers: customerHeaders
  });
  assert.deepEqual(await hiddenMerchantDeposits.json(), []);
  const blockedPaymentHistory = await fetch(`${baseUrl}/v1/wallet/payment-transactions`, {
    headers: customerHeaders
  });
  assert.equal(blockedPaymentHistory.status, 403);
});
