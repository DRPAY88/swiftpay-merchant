import assert from "node:assert/strict";
import { test } from "node:test";
import { newDb } from "pg-mem";
import { createAdminStore } from "../src/admin-store.js";

test("admin store persists merchant status, internal adjustments, and atomic audit events", async () => {
  const database = newDb();
  const { Pool } = database.adapters.createPg();
  const pool = new Pool();
  const store = createAdminStore("postgresql://test", pool);
  try {
    await store.initialize(["merchant@example.com", "support@swiftpay.site"], ["support@swiftpay.site"]);

    assert.equal((await store.getUser("support@swiftpay.site")).role, "super_admin");
    assert.equal((await store.getUser("merchant@example.com")).status, "active");

    await store.createMerchant("new@example.com", "support@swiftpay.site");
    await store.setMerchantStatus("new@example.com", "suspended", "support@swiftpay.site");
    assert.equal((await store.getUser("new@example.com")).status, "suspended");
    await store.createMerchant("new@example.com", "support@swiftpay.site");
    assert.equal((await store.getUser("new@example.com")).status, "active");

    const adjustment = await store.createAdjustment(-125.5, "Reconciliation note", "support@swiftpay.site");
    assert.equal(Number(adjustment.amount), -125.5);
    assert.equal(Number(await store.adjustmentTotal()), -125.5);
    assert.equal((await store.listAdjustments(10)).length, 1);

    const transferId = await store.createTransferIntent("support@swiftpay.site", "REF-1", {
      referenceNo: "REF-1",
      amount: 50
    });
    await store.finishTransferIntent(transferId, "accepted", "transfer-1", { providerStatus: "PENDING" });
    const audit = await store.listAudit(10);
    assert.deepEqual(audit.map((entry) => entry.action).sort(), [
      "balance.adjusted",
      "merchant.created",
      "merchant.created",
      "merchant.suspended",
      "transfer.accepted"
    ].sort());

    const webhook = {
      eventKey: "payment:5008f066-d68a-4ef2-b794-8596bac268f6",
      eventType: "payment",
      paymentId: "5008f066-d68a-4ef2-b794-8596bac268f6",
      referenceNo: "ORDER-1",
      status: "EXECUTED",
      details: { institutionReferenceNo: "INST-1" }
    };
    await store.recordWebhookEvent(webhook);
    await store.recordWebhookEvent(webhook);
    assert.deepEqual(
      await store.getPaymentWebhookStatus(webhook.paymentId),
      { status: "EXECUTED" }
    );

    await store.recordWebhookEvent({
      eventKey: "payment:00000000-0000-4000-8000-000000000003",
      eventType: "payment",
      paymentId: "00000000-0000-4000-8000-000000000003",
      referenceNo: "ORDER-PHP-1",
      status: "EXECUTED",
      amount: 125.5,
      currency: "PHP"
    });
    const paymentTransactions = await store.listPaymentWebhookTransactions(10);
    assert.equal(paymentTransactions.length, 1);
    assert.equal(paymentTransactions[0].reference_no, "ORDER-PHP-1");
    assert.equal(Number(paymentTransactions[0].amount), 125.5);
    assert.equal(paymentTransactions[0].currency, "PHP");

    const krwPayment = {
      eventKey: "payment:00000000-0000-4000-8000-000000000002",
      eventType: "payment",
      paymentId: "00000000-0000-4000-8000-000000000002",
      referenceNo: "QR-KRW-1",
      status: "PENDING",
      details: {
        amount: 510,
        currency: "PHP",
        customerAmountKrw: 12000,
        ratePhpPerKrw: 0.0425
      }
    };
    await store.recordWebhookEvent(krwPayment);
    await store.recordWebhookEvent({
      ...krwPayment,
      status: "EXECUTED",
      details: { amount: 510, currency: "PHP", institutionReferenceNo: "INST-2" }
    });
    assert.deepEqual(await store.getKrwQrPaymentBalance(), { balance: "0", paymentCount: 0 });
    const persistedPayment = await pool.query(
      "SELECT details FROM swiftpay_webhook_events WHERE event_key = $1",
      [krwPayment.eventKey]
    );
    assert.deepEqual(persistedPayment.rows[0].details, {
      amount: 510,
      currency: "PHP",
      customerAmountKrw: 12000,
      ratePhpPerKrw: 0.0425,
      institutionReferenceNo: "INST-2"
    });

    await store.upsertPushToken("merchant@example.com", "device-push-token-1234567890");
    assert.deepEqual(await store.listActivePushTokens(), ["device-push-token-1234567890"]);
    await store.recordWebhookEvent({
      eventKey: "payment:00000000-0000-4000-8000-000000000004",
      eventType: "payment",
      paymentId: "00000000-0000-4000-8000-000000000004",
      referenceNo: "INCOMING-1",
      status: "EXECUTED",
      amount: 88.25,
      currency: "PHP",
      notifyIncoming: true
    });
    await store.recordWebhookEvent({
      eventKey: "payment:00000000-0000-4000-8000-000000000004",
      eventType: "payment",
      paymentId: "00000000-0000-4000-8000-000000000004",
      referenceNo: "INCOMING-1",
      status: "EXECUTED",
      amount: 88.25,
      currency: "PHP",
      notifyIncoming: true
    });
    const queued = await store.claimIncomingPaymentPushes(10);
    assert.equal(queued.length, 1);
    assert.equal(queued[0].reference_no, "INCOMING-1");
    assert.equal(Number(queued[0].amount), 88.25);
    assert.equal(queued[0].currency, "PHP");
    await store.completeIncomingPaymentPush(queued[0].event_key);
    assert.deepEqual(await store.claimIncomingPaymentPushes(10), []);
    await store.recordWebhookEvent({
      eventKey: "payment:00000000-0000-4000-8000-000000000005",
      eventType: "payment",
      paymentId: "00000000-0000-4000-8000-000000000005",
      referenceNo: "INCOMING-2",
      status: "EXECUTED",
      notifyIncoming: true
    });
    const paymentWithoutAmount = await store.claimIncomingPaymentPushes(10);
    assert.equal(paymentWithoutAmount.length, 1);
    assert.equal(paymentWithoutAmount[0].reference_no, "INCOMING-2");
    assert.equal(paymentWithoutAmount[0].amount, null);
    assert.equal(paymentWithoutAmount[0].currency, null);
    await store.deletePushToken("merchant@example.com", "device-push-token-1234567890");
    assert.deepEqual(await store.listActivePushTokens(), []);
  } finally {
    await store.close();
  }
});

test("email customer wallet balances are isolated, credited once on settlement, and reserve transfers", async () => {
  const database = newDb();
  const { Pool } = database.adapters.createPg();
  const pool = new Pool();
  const store = createAdminStore("postgresql://test", pool);
  try {
    await store.initialize(["existing-merchant@example.com"], []);
    const firstEmail = "first@example.com";
    const secondEmail = "second@example.com";
    assert.equal((await store.ensureEmailCustomer(firstEmail)).role, "customer");
    assert.equal((await store.getUser(firstEmail)).role, "customer");
    assert.equal((await store.getUser("existing-merchant@example.com")).role, "merchant");
    assert.equal(await store.ensureEmailCustomer("existing-merchant@example.com"), null);
    await store.ensureEmailCustomer(secondEmail);
    await store.createCustomerWalletTopup(firstEmail, "WLT-FIRST", 100);
    assert.equal(Number(await store.getCustomerWalletBalance(firstEmail)), 0);

    const settledTopup = {
      eventKey: "payment:wallet-topup-1",
      eventType: "payment",
      paymentId: "wallet-topup-1",
      referenceNo: "WLT-FIRST",
      status: "EXECUTED",
      amount: 100,
      currency: "PHP"
    };
    await store.recordWebhookEvent(settledTopup);
    await store.recordWebhookEvent(settledTopup);
    assert.equal(Number(await store.getCustomerWalletBalance(firstEmail)), 100);
    assert.equal(Number(await store.getCustomerWalletBalance(secondEmail)), 0);
    assert.equal((await store.listCustomerWalletTransactions(secondEmail, 10)).length, 0);

    await store.createCustomerWalletTopup(firstEmail, "WLT-MISMATCH", 50);
    await store.recordWebhookEvent({
      eventKey: "payment:wallet-topup-mismatch",
      eventType: "payment",
      paymentId: "wallet-topup-mismatch",
      referenceNo: "WLT-MISMATCH",
      status: "EXECUTED",
      amount: 51,
      currency: "PHP"
    });
    assert.equal(Number(await store.getCustomerWalletBalance(firstEmail)), 100);
    assert.equal((await store.getCustomerWalletTopup(firstEmail, "WLT-MISMATCH")).status, "ERROR");

    await store.reserveCustomerWalletDisbursement(firstEmail, "PAYOUT-FIRST", 60);
    assert.equal(Number(await store.getCustomerWalletBalance(firstEmail)), 40);
    await assert.rejects(
      store.reserveCustomerWalletDisbursement(firstEmail, "PAYOUT-TOO-LARGE", 41),
      (error) => error.status === 409 && error.message === "insufficient_wallet_balance"
    );

    await store.recordWebhookEvent({
      eventKey: "disbursement:PAYOUT-FIRST",
      eventType: "disbursement",
      referenceNo: "PAYOUT-FIRST",
      status: "REJECTED"
    });
    assert.equal(Number(await store.getCustomerWalletBalance(firstEmail)), 100);
    await store.reserveCustomerWalletDisbursement(firstEmail, "PAYOUT-ERROR", 10);
    await store.recordWebhookEvent({
      eventKey: "disbursement:PAYOUT-ERROR",
      eventType: "disbursement",
      referenceNo: "PAYOUT-ERROR",
      status: "ERROR"
    });
    assert.equal(Number(await store.getCustomerWalletBalance(firstEmail)), 100);
    const activity = await store.listCustomerWalletTransactions(firstEmail, 10);
    assert.equal(activity.length, 4);
    assert.ok(activity.some(({ kind, status }) => kind === "disbursement" && status === "REJECTED"));
    assert.ok(activity.some(({ kind, status }) => kind === "topup" && status === "EXECUTED"));
    assert.ok(activity.some(({ kind, status }) => kind === "topup" && status === "ERROR"));
  } finally {
    await store.close();
  }
});

test("self-service merchant ledgers isolate collections and reserve payouts", async () => {
  const database = newDb();
  const { Pool } = database.adapters.createPg();
  const pool = new Pool();
  const store = createAdminStore("postgresql://test", pool);
  try {
    await store.initialize([], []);
    const firstEmail = "merchant-one@example.com";
    const secondEmail = "merchant-two@example.com";
    const firstMerchant = await store.ensureSelfServiceMerchant(firstEmail);
    await store.ensureSelfServiceMerchant(secondEmail);
    assert.equal(firstMerchant.role, "merchant");
    assert.equal(firstMerchant.wallet_mode, "internal");

    await store.createMerchantWalletCollection(firstEmail, "COLLECT-ONE", 100);
    await store.recordMerchantWalletCollectionOrder(firstEmail, "COLLECT-ONE", "payment-one");
    const settledCollection = {
      eventKey: "payment:merchant-collection-one",
      eventType: "payment",
      paymentId: "payment-one",
      referenceNo: "COLLECT-ONE",
      status: "EXECUTED",
      amount: 100,
      currency: "PHP",
      verifiedWebhook: true
    };
    await store.recordWebhookEvent({ ...settledCollection, verifiedWebhook: false });
    assert.equal(Number(await store.getMerchantWalletBalance(firstEmail)), 0);
    await store.recordWebhookEvent(settledCollection);
    await store.recordWebhookEvent(settledCollection);
    assert.equal(Number(await store.getMerchantWalletBalance(firstEmail)), 100);
    assert.equal(Number(await store.getMerchantWalletBalance(secondEmail)), 0);

    await store.createMerchantWalletCollection(secondEmail, "COLLECT-TWO", 35);
    await store.recordMerchantWalletCollectionOrder(secondEmail, "COLLECT-TWO", "payment-two");
    await store.recordWebhookEvent({
      eventKey: "payment:merchant-collection-two",
      eventType: "payment",
      paymentId: "payment-two",
      referenceNo: "COLLECT-TWO",
      status: "EXECUTED",
      amount: 36,
      currency: "PHP",
      verifiedWebhook: true
    });
    assert.equal(Number(await store.getMerchantWalletBalance(secondEmail)), 0);
    assert.equal((await store.listMerchantWalletTransactions(secondEmail, 10))[0].status, "ERROR");

    await store.createMerchantWalletCollection(secondEmail, "COLLECT-THREE", 20);
    await store.recordMerchantWalletCollectionOrder(secondEmail, "COLLECT-THREE", "payment-three");
    await store.recordWebhookEvent({
      eventKey: "payment:merchant-collection-three",
      eventType: "payment",
      paymentId: "payment-three",
      referenceNo: "COLLECT-THREE",
      status: "EXECUTED",
      amount: 20,
      currency: "USD",
      verifiedWebhook: true
    });
    assert.equal(Number(await store.getMerchantWalletBalance(secondEmail)), 0);
    assert.equal(
      (await store.listMerchantWalletTransactions(secondEmail, 10))
        .find(({ reference_no }) => reference_no === "COLLECT-THREE").status,
      "ERROR"
    );

    await store.reserveMerchantWalletDisbursement(firstEmail, "PAYOUT-ONE", 40);
    assert.equal(Number(await store.getMerchantWalletBalance(firstEmail)), 60);
    await assert.rejects(
      store.reserveMerchantWalletDisbursement(firstEmail, "PAYOUT-TOO-LARGE", 61),
      (error) => error.status === 409 && error.message === "insufficient_wallet_balance"
    );
    await store.recordWebhookEvent({
      eventKey: "disbursement:PAYOUT-ONE",
      eventType: "disbursement",
      referenceNo: "PAYOUT-ONE",
      status: "REJECTED",
      verifiedWebhook: true
    });
    assert.equal(
      (await store.listMerchantWalletTransactions(firstEmail, 10))
        .find(({ kind }) => kind === "disbursement").status,
      "REJECTED"
    );
    assert.equal(Number(await store.getMerchantWalletBalance(firstEmail)), 100);
    await store.reserveMerchantWalletDisbursement(firstEmail, "PAYOUT-ERROR", 10);
    await store.recordWebhookEvent({
      eventKey: "disbursement:PAYOUT-ERROR",
      eventType: "disbursement",
      referenceNo: "PAYOUT-ERROR",
      status: "ERROR",
      verifiedWebhook: true
    });
    assert.equal(
      (await store.listMerchantWalletTransactions(firstEmail, 10))
        .find(({ reference_no }) => reference_no === "PAYOUT-ERROR").status,
      "REJECTED"
    );
    assert.equal(Number(await store.getMerchantWalletBalance(firstEmail)), 100);
    await store.createMerchantWalletCollection(firstEmail, "COLLECT-POLL", 15);
    await store.recordMerchantWalletCollectionOrder(firstEmail, "COLLECT-POLL", "payment-poll");
    assert.equal(
      await store.reconcileMerchantWalletCollection(firstEmail, "payment-poll", "EXECUTED"),
      "EXECUTED"
    );
    assert.equal(Number(await store.getMerchantWalletBalance(firstEmail)), 115);
    assert.equal(Number(await store.getMerchantWalletBalance(secondEmail)), 0);
    assert.equal((await store.listMerchantWalletTransactions(firstEmail, 10)).length, 4);
  } finally {
    await store.close();
  }
});

test("onboarding is enabled for new merchant accounts and completion is account-wide", async () => {
  const database = newDb();
  const { Pool } = database.adapters.createPg();
  const store = createAdminStore("postgresql://test", new Pool());
  try {
    await store.initialize(["existing@example.com"], []);
    assert.deepEqual(await store.getOnboardingSettings(), { enabled: true });
    assert.deepEqual(await store.getMerchantOnboardingState("existing@example.com"), {
      required: false
    });

    await store.createMerchant("new@example.com", "support@swiftpay.site");
    assert.deepEqual(await store.getMerchantOnboardingState("new@example.com"), {
      required: true
    });
    await store.setMerchantStatus("new@example.com", "removed", "support@swiftpay.site");
    await store.createMerchant("new@example.com", "support@swiftpay.site");
    assert.deepEqual(await store.getMerchantOnboardingState("new@example.com"), {
      required: true
    });

    await store.setOnboardingEnabled(false, "support@swiftpay.site");
    assert.deepEqual(await store.getOnboardingSettings(), { enabled: false });
    assert.deepEqual(await store.getMerchantOnboardingState("new@example.com"), {
      required: false
    });

    await store.setOnboardingEnabled(true, "support@swiftpay.site");
    await store.completeMerchantOnboarding("new@example.com");
    assert.deepEqual(await store.getMerchantOnboardingState("new@example.com"), {
      required: false
    });
    await store.setMerchantStatus("new@example.com", "removed", "support@swiftpay.site");
    await store.createMerchant("new@example.com", "support@swiftpay.site");
    await store.setOnboardingEnabled(false, "support@swiftpay.site");
    assert.deepEqual(await store.getMerchantOnboardingState("new@example.com"), {
      required: false
    });
    await store.setOnboardingEnabled(true, "support@swiftpay.site");
    assert.deepEqual(await store.getMerchantOnboardingState("new@example.com"), {
      required: false
    });
  } finally {
    await store.close();
  }
});

test("legacy registration approval cannot change a personal-wallet account into a merchant", async () => {
  const database = newDb();
  const { Pool } = database.adapters.createPg();
  const pool = new Pool();
  const store = createAdminStore("postgresql://test", pool, {
    kybEncryptionKey: Buffer.alloc(32, 1)
  });
  try {
    await store.initialize([], ["support@swiftpay.site"]);
    const email = "customer@example.com";
    await store.createRegistrationDraft(email);
    await pool.query(
      "UPDATE merchant_registrations SET status = 'submitted' WHERE email = $1",
      [email]
    );
    await pool.query("INSERT INTO customer_users (email) VALUES ($1)", [email]);

    await assert.rejects(
      store.reviewRegistration(email, "approved", "Legacy review", "support@swiftpay.site"),
      (error) => error.status === 409
    );
    assert.equal((await store.getUser(email)).role, "customer");
  } finally {
    await store.close();
  }
});

test("registration fails closed without encryption and defaults off", async () => {
  const database = newDb();
  const { Pool } = database.adapters.createPg();
  const store = createAdminStore("postgresql://test", new Pool());
  try {
    await store.initialize([], []);
    assert.deepEqual(await store.getRegistrationSettings(), {
      enabled: false,
      secureStorageAvailable: false
    });
    await assert.rejects(
      store.setRegistrationEnabled(true, "support@swiftpay.site"),
      (error) => error.status === 503
    );
  } finally {
    await store.close();
  }
});
