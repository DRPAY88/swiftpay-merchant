import { createJwtVerifier, createOtpAuth, sendOtpEmail } from "./auth.js";
import { createAdminStore } from "./admin-store.js";
import { createApp } from "./app.js";
import { loadConfig } from "./config.js";

const config = loadConfig();
const userStore = config.databaseUrl ? createAdminStore(config.databaseUrl) : null;
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
  userStore
});
const server = app.listen(config.port, "0.0.0.0", () => {
  console.log(`SwiftPay backend listening on port ${config.port}`);
});

for (const signal of ["SIGINT", "SIGTERM"]) {
  process.once(signal, () => {
    server.close(async () => {
      await userStore?.close();
      process.exit(0);
    });
  });
}