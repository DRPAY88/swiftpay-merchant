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

test("Transak is disabled without credentials and defaults to staging", () => {
  const config = loadConfig(baseEnv);
  assert.deepEqual(config.transak, {
    apiKey: null,
    apiSecret: null,
    environment: "staging"
  });
});

test("Transak credentials must be configured together and environment is constrained", () => {
  assert.throws(
    () => loadConfig({ ...baseEnv, TRANSAK_API_KEY: "partner-key" }),
    /TRANSAK_API_KEY and TRANSAK_API_SECRET must be configured together/
  );
  assert.throws(
    () => loadConfig({ ...baseEnv, TRANSAK_ENVIRONMENT: "live" }),
    /TRANSAK_ENVIRONMENT must be staging or production/
  );
});

test("Transak production environment retains private paired credentials", () => {
  const config = loadConfig({
    ...baseEnv,
    TRANSAK_ENVIRONMENT: "production",
    TRANSAK_API_KEY: "partner-key",
    TRANSAK_API_SECRET: "partner-secret"
  });
  assert.deepEqual(config.transak, {
    apiKey: "partner-key",
    apiSecret: "partner-secret",
    environment: "production"
  });
});

test("KYB encryption key is optional but must decode to exactly 32 bytes", () => {
  assert.equal(loadConfig(baseEnv).kybEncryptionKey, null);
  const key = Buffer.alloc(32, 5);
  assert.deepEqual(loadConfig({ ...baseEnv, KYB_ENCRYPTION_KEY: key.toString("base64") }).kybEncryptionKey, key);
  assert.throws(
    () => loadConfig({ ...baseEnv, KYB_ENCRYPTION_KEY: Buffer.alloc(16).toString("base64") }),
    /KYB_ENCRYPTION_KEY must be a base64-encoded 32-byte key/
  );
});

test("public checkout origin must use HTTPS and cannot include a path", () => {
  const config = loadConfig({ ...baseEnv, PUBLIC_BASE_URL: "https://merchant.example" });
  assert.equal(config.publicBaseUrl.href, "https://merchant.example/");
  assert.throws(
    () => loadConfig({ ...baseEnv, PUBLIC_BASE_URL: "http://merchant.example" }),
    /PUBLIC_BASE_URL must be an HTTPS origin/
  );
  assert.throws(
    () => loadConfig({ ...baseEnv, PUBLIC_BASE_URL: "https://merchant.example/checkout" }),
    /PUBLIC_BASE_URL must be an HTTPS origin/
  );
});

test("FCM service-account configuration is optional but must be complete JSON", () => {
  assert.equal(loadConfig(baseEnv).fcmServiceAccountJson, null);
  assert.throws(
    () => loadConfig({ ...baseEnv, FCM_SERVICE_ACCOUNT_JSON: "not-json" }),
    /FCM_SERVICE_ACCOUNT_JSON must contain valid JSON/
  );
  assert.throws(
    () => loadConfig({
      ...baseEnv,
      FCM_SERVICE_ACCOUNT_JSON: JSON.stringify({ project_id: "swiftpay-push" })
    }),
    /must contain project_id, client_email, and private_key/
  );
});
