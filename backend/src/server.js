import { createJwtVerifier, createOtpAuth, sendOtpEmail } from "./auth.js";
import { createAdminStore } from "./admin-store.js";
import { createApp } from "./app.js";
import { loadConfig } from "./config.js";
import { createFcmSender } from "./fcm.js";
import { createTransakWidgetService } from "./transak.js";

const config = loadConfig();
const userStore = config.databaseUrl ? createAdminStore(config.databaseUrl) : null;
const pushNotificationService = createFcmSender(config.fcmServiceAccountJson);
if (userStore) {
  await userStore.initialize(config.allowedEmails, config.superAdminEmails);
} else {
  console.error("Admin backoffice is disabled until DATABASE_URL is configured");
}
const otpAuth = createOtpAuth(config, {
  sendEmail: (email, code) => sendOtpEmail(config, email, code),
  userStore
});
const app = createApp({
  config,
  verifyAccessToken: createJwtVerifier(config),
  requestOtp: otpAuth.requestOtp,
  verifyOtp: otpAuth.verifyOtp,
  cryptoWidgetService: createTransakWidgetService(config),
  pushNotificationService,
  userStore
});
const server = app.listen(config.port, "0.0.0.0", () => {
  console.log(`SwiftPay backend listening on port ${config.port}`);
});

async function deliverIncomingPaymentPushes() {
  if (!pushNotificationService || !userStore?.claimIncomingPaymentPushes) return;
  const pending = await userStore.claimIncomingPaymentPushes(10);
  for (const event of pending) {
    try {
      const tokens = await userStore.listActivePushTokens();
      if (tokens.length === 0) {
        await userStore.retryIncomingPaymentPush(event.event_key, event.attempts);
        continue;
      }
      const hasAmount = Number.isFinite(Number(event.amount)) &&
        Number(event.amount) > 0 &&
        typeof event.currency === "string";
      const notification = {
        title: "Money received",
        body: hasAmount
          ? `Received ${new Intl.NumberFormat("en", {
              style: "currency",
              currency: event.currency
            }).format(Number(event.amount))}`
          : "A payment has been received"
      };
      const data = {
        type: "incoming_payment",
        paymentId: event.payment_id,
        referenceNo: event.reference_no,
        ...(event.amount !== null ? { amount: String(event.amount) } : {}),
        ...(event.currency ? { currency: event.currency } : {})
      };
      let failed = false;
      for (const token of tokens) {
        try {
          const result = await pushNotificationService.send({
            token,
            notification,
            data
          });
          if (result.invalidToken) await userStore.removePushToken(token);
        } catch {
          failed = true;
        }
      }
      if (failed) {
        await userStore.retryIncomingPaymentPush(event.event_key, event.attempts);
      } else {
        await userStore.completeIncomingPaymentPush(event.event_key);
      }
    } catch {
      console.error("Incoming payment push delivery failed");
      await userStore.retryIncomingPaymentPush(event.event_key, event.attempts);
    }
  }
}

let pushDeliveryRunning = false;
const pushDeliveryTimer = pushNotificationService && userStore
  ? setInterval(() => {
      if (pushDeliveryRunning) return;
      pushDeliveryRunning = true;
      deliverIncomingPaymentPushes()
        .catch(() => console.error("Incoming payment push queue processing failed"))
        .finally(() => { pushDeliveryRunning = false; });
    }, 15_000)
  : null;
pushDeliveryTimer?.unref();

for (const signal of ["SIGINT", "SIGTERM"]) {
  process.once(signal, () => {
    if (pushDeliveryTimer) clearInterval(pushDeliveryTimer);
    server.close(async () => {
      await userStore?.close();
      process.exit(0);
    });
  });
}