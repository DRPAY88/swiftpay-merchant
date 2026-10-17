import assert from "node:assert/strict";
import { test } from "node:test";
import {
  createJwtVerifier,
  createOtpAuth,
  sendOtpEmail
} from "../src/auth.js";

test("OTP is allowlisted, one-time, and issues a scoped verifiable session", async () => {
  let now = 1_000_000;
  const sentMessages = [];
  const config = {
    allowedEmails: ["merchant@example.com"],
    sessionSigningKey: "session-signing-key-that-is-at-least-32-bytes"
  };
  const auth = createOtpAuth(config, {
    now: () => now,
    codeGenerator: () => "204681",
    sendEmail: async (email, code) => sentMessages.push({ email, code })
  });

  await auth.requestOtp("other@example.com");
  assert.equal(sentMessages.length, 0);

  await auth.requestOtp("MERCHANT@example.com");
  assert.deepEqual(sentMessages, [{ email: "merchant@example.com", code: "204681" }]);
  assert.equal(await auth.verifyOtp("merchant@example.com", "000000"), null);

  const session = await auth.verifyOtp("merchant@example.com", "204681");
  assert.equal(session.email, "merchant@example.com");
  assert.equal(session.expiresInSeconds, 8 * 60 * 60);

  const verifyToken = createJwtVerifier(config);
  const principal = await verifyToken(session.accessToken);
  assert.equal(principal.sub, "merchant@example.com");
  assert.match(principal.scope, /swiftpay:disbursements:write/);
  assert.equal(await auth.verifyOtp("merchant@example.com", "204681"), null);

  await auth.requestOtp("merchant@example.com");
  assert.equal(sentMessages.length, 1);
  now += 60_001;
  await auth.requestOtp("merchant@example.com");
  assert.equal(sentMessages.length, 2);
});

test("expired OTPs cannot create sessions", async () => {
  let now = 1_000_000;
  const auth = createOtpAuth({
    allowedEmails: ["merchant@example.com"],
    sessionSigningKey: "session-signing-key-that-is-at-least-32-bytes"
  }, {
    now: () => now,
    codeGenerator: () => "123456",
    sendEmail: async () => {}
  });
  await auth.requestOtp("merchant@example.com");
  now += 10 * 60_000;
  assert.equal(await auth.verifyOtp("merchant@example.com", "123456"), null);
});

test("email OTP provisions new signups as internal merchants while preserving existing customer roles", async () => {
  const config = {
    allowedEmails: [],
    sessionSigningKey: "session-signing-key-that-is-at-least-32-bytes"
  };
  const provisioned = [];
  const sentCodes = [];
  const auth = createOtpAuth(config, {
    userStore: {
      getUser: async (email) => email === "existing-customer@example.com"
        ? { email, role: "customer", status: "active" }
        : null,
      ensureSelfServiceMerchant: async (email) => {
        provisioned.push(email);
        return { email, role: "merchant", status: "active", wallet_mode: "internal" };
      }
    },
    codeGenerator: () => "123456",
    sendEmail: async (email, code) => sentCodes.push({ email, code })
  });

  await auth.requestOtp("NEW-MERCHANT@example.com");
  assert.deepEqual(sentCodes, [{ email: "new-merchant@example.com", code: "123456" }]);
  assert.deepEqual(provisioned, []);

  assert.equal(await auth.verifyOtp("new-merchant@example.com", "000000"), null);
  assert.deepEqual(provisioned, []);
  const session = await auth.verifyOtp("new-merchant@example.com", "123456");
  assert.equal(session.email, "new-merchant@example.com");
  assert.equal(session.role, "merchant");
  assert.deepEqual(provisioned, ["new-merchant@example.com"]);
  const principal = await createJwtVerifier(config)(session.accessToken);
  assert.equal(principal.sub, "new-merchant@example.com");
  assert.equal(principal.role, "merchant");
  assert.match(principal.scope, /swiftpay:disbursements:write/);

  await auth.requestOtp("existing-customer@example.com");
  const existingCustomer = await auth.verifyOtp("existing-customer@example.com", "123456");
  assert.equal(existingCustomer.role, "customer");
  assert.deepEqual(provisioned, ["new-merchant@example.com"]);
});

test("OTP sessions receive roles from the active persistent merchant record", async () => {
  let now = 1_000_000;
  let record = { email: "support@swiftpay.site", role: "super_admin", status: "active" };
  const sentCodes = [];
  const config = {
    allowedEmails: ["support@swiftpay.site"],
    sessionSigningKey: "session-signing-key-that-is-at-least-32-bytes"
  };
  const auth = createOtpAuth(config, {
    userStore: { getUser: async () => record },
    now: () => now,
    codeGenerator: () => "864209",
    sendEmail: async (_email, code) => sentCodes.push(code)
  });

  await auth.requestOtp("support@swiftpay.site");
  assert.deepEqual(sentCodes, ["864209"]);
  const session = await auth.verifyOtp("support@swiftpay.site", "864209");
  assert.equal(session.role, "super_admin");
  assert.equal(session.expiresInSeconds, 60 * 60);
  assert.equal((await createJwtVerifier(config)(session.accessToken)).role, "super_admin");

  now += 60_001;
  record = { ...record, status: "suspended" };
  await auth.requestOtp("support@swiftpay.site");
  assert.equal(sentCodes.length, 1);
  assert.equal(await auth.verifyOtp("support@swiftpay.site", "864209"), null);
});

test("registration OTP issues only an applicant-scoped token after email verification", async () => {
  const sent = [];
  const config = {
    allowedEmails: [],
    sessionSigningKey: "session-signing-key-that-is-at-least-32-bytes"
  };
  const userStore = {
    getRegistrationSettings: async () => ({ enabled: true, secureStorageAvailable: true }),
    getUser: async () => null,
    createRegistrationDraft: async (email) => ({ email, status: "draft" })
  };
  const auth = createOtpAuth(config, {
    userStore,
    codeGenerator: () => "314159",
    sendEmail: async (email, code, purpose) => sent.push({ email, code, purpose })
  });

  await auth.requestRegistrationOtp("Applicant@example.com");
  assert.deepEqual(sent, [{
    email: "applicant@example.com",
    code: "314159",
    purpose: "registration"
  }]);
  assert.equal(await auth.verifyRegistrationOtp("applicant@example.com", "000000"), null);
  const session = await auth.verifyRegistrationOtp("applicant@example.com", "314159");
  assert.equal(session.role, "registration_applicant");
  const principal = await createJwtVerifier(config)(session.accessToken);
  assert.equal(principal.role, "registration_applicant");
  assert.equal(principal.scope, "kyb:submit");
  assert.equal(await auth.verifyOtp("applicant@example.com", "314159"), null);
});

test("failed OTP delivery clears the challenge and allows an immediate retry", async () => {
  let shouldFail = true;
  const deliveredCodes = [];
  const auth = createOtpAuth({
    allowedEmails: ["merchant@example.com"],
    sessionSigningKey: "session-signing-key-that-is-at-least-32-bytes"
  }, {
    now: () => 1_000_000,
    codeGenerator: () => "204681",
    sendEmail: async (_email, code) => {
      if (shouldFail) throw new Error("email provider unavailable");
      deliveredCodes.push(code);
    }
  });

  await assert.rejects(auth.requestOtp("merchant@example.com"), /email provider unavailable/);
  assert.equal(await auth.verifyOtp("merchant@example.com", "204681"), null);

  shouldFail = false;
  await auth.requestOtp("merchant@example.com");
  assert.deepEqual(deliveredCodes, ["204681"]);
  assert.equal((await auth.verifyOtp("merchant@example.com", "204681")).email, "merchant@example.com");
});

test("OTP delivery diagnostics retain provider messages while redacting sensitive values", async () => {
  await assert.rejects(
    sendOtpEmail(
      { resendApiKey: "test-key", otpFromEmail: "SwiftPay <login@example.com>" },
      "merchant@example.com",
      "204681",
      async () => new Response(JSON.stringify({
        name: "validation_error",
        message: "The sender login@example.com domain is not verified for merchant@example.com",
        recipient: "merchant@example.com"
      }), { status: 403 })
    ),
    (error) => {
      assert.match(error.message, /HTTP 403, validation_error\): The sender \[redacted-email\] domain is not verified for \[redacted-email\]/);
      assert.doesNotMatch(error.message, /merchant@example\.com/);
      return true;
    }
  );
});

test("OTP delivery diagnostics redact API keys and cap provider message length", async () => {
  await assert.rejects(
    sendOtpEmail(
      { resendApiKey: "test-key", otpFromEmail: "SwiftPay <login@example.com>" },
      "merchant@example.com",
      "204681",
      async () => new Response(JSON.stringify({
        name: "validation_error",
        message: `Key re_${"A".repeat(40)} invalid. ${"x".repeat(300)}`
      }), { status: 403 })
    ),
    (error) => {
      assert.match(error.message, /\[redacted-key\]/);
      assert.ok(error.message.length < 300);
      return true;
    }
  );
});