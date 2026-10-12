import assert from "node:assert/strict";
import { test } from "node:test";
import { loadConfig } from "../src/config.js";

const baseEnv = {
  SWIFTPAY_ACCESS_KEY: "A".repeat(32),
  SWIFTPAY_SECRET_KEY: "S".repeat(32),
  SESSION_SIGNING_KEY: "session-signing-key-that-is-at-least-32-bytes",
  AUTH_ALLOWED_EMAILS: "merchant@example.com",
  SUPER_ADMIN_EMAILS: "support@swiftpay.site",
  DATABASE_URL: "postgresql://localhost/swiftpay",
  RESEND_API_KEY: "resend-test-key",
  OTP_FROM_EMAIL: "SwiftPay <login@example.com>"
};

test("additional allowlisted emails preserve existing operators and normalize duplicates", () => {
  const config = loadConfig({
    ...baseEnv,
    AUTH_ADDITIONAL_ALLOWED_EMAILS: "Support@swiftpay.site, merchant@example.com"
  });

  assert.deepEqual(config.allowedEmails, ["merchant@example.com", "support@swiftpay.site"]);
  assert.deepEqual(config.superAdminEmails, ["support@swiftpay.site"]);
});

test("invalid additional allowlist entries are rejected", () => {
  assert.throws(
    () => loadConfig({ ...baseEnv, AUTH_ADDITIONAL_ALLOWED_EMAILS: "not-an-email" }),
    /AUTH_ALLOWED_EMAILS, AUTH_ADDITIONAL_ALLOWED_EMAILS, and SUPER_ADMIN_EMAILS/
  );
});
