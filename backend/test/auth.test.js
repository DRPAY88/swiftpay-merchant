import assert from "node:assert/strict";
import { test } from "node:test";
import { createJwtVerifier, createOtpAuth, sendOtpEmail } from "../src/auth.js";

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