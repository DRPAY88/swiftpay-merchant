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
  } finally {
    await store.close();
  }
});
