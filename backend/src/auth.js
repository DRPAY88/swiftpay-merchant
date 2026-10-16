import { createHmac, randomInt, timingSafeEqual } from "node:crypto";
import { SignJWT, jwtVerify } from "jose";

export function createJwtVerifier(config) {
  const key = new TextEncoder().encode(config.sessionSigningKey);
  return async (token) => {
    const { payload } = await jwtVerify(token, key, {
      algorithms: ["HS256"],
      issuer: "swiftpay-merchant-backend",
      audience: "swiftpay-merchant-api"
    });
    if (typeof payload.sub !== "string" || payload.sub.length === 0) {
      throw new Error("JWT subject is required");
    }
    return payload;
  };
}

export function createOtpAuth(config, {
  sendEmail,
  userStore,
  now = Date.now,
  codeGenerator = () => String(randomInt(0, 1_000_000)).padStart(6, "0")
}) {
  const allowedEmails = new Set(config.allowedEmails.map((email) => email.toLowerCase()));
  const challenges = new Map();
  const lastSentAt = new Map();
  const registrationChallenges = new Map();
  const registrationLastSentAt = new Map();
  const key = new TextEncoder().encode(config.sessionSigningKey);

  async function getActiveUser(email) {
    if (userStore) {
      const user = await userStore.getUser(email);
      if (user) return user.status === "active" ? user : null;
      return userStore.ensureEmailCustomer
        ? { email, role: "customer", status: "active", isNewCustomer: true }
        : null;
    }
    return allowedEmails.has(email) ? { email, role: "merchant", status: "active" } : null;
  }

  async function requestOtp(emailValue) {
    const email = emailValue.trim().toLowerCase();
    if (!await getActiveUser(email)) return;

    const previousSentAt = lastSentAt.get(email) || 0;
    if (now() - previousSentAt < 60_000) return;

    const code = codeGenerator();
    const codeHash = createHmac("sha256", key).update(`${email}:${code}`).digest();
    const sentAt = now();
    const challenge = { codeHash, expiresAt: sentAt + 10 * 60_000, attempts: 0 };
    challenges.set(email, challenge);
    lastSentAt.set(email, sentAt);
    try {
      await sendEmail(email, code);
    } catch (error) {
      if (challenges.get(email) === challenge) {
        challenges.delete(email);
        lastSentAt.delete(email);
      }
      throw error;
    }
  }

  async function verifyOtp(emailValue, code) {
    const email = emailValue.trim().toLowerCase();
    const challenge = challenges.get(email);
    let user = await getActiveUser(email);
    if (!user || !challenge || challenge.expiresAt <= now() || challenge.attempts >= 5) {
      challenges.delete(email);
      return null;
    }

    challenge.attempts += 1;
    const submittedHash = createHmac("sha256", key).update(`${email}:${code}`).digest();
    if (!timingSafeEqual(challenge.codeHash, submittedHash)) return null;
    challenges.delete(email);
    if (user.isNewCustomer) {
      user = await userStore.ensureEmailCustomer(email);
      if (!user || user.status !== "active") return null;
    }

    const expiresInSeconds = user.role === "super_admin" ? 60 * 60 : 8 * 60 * 60;
    const accessToken = await new SignJWT({
      role: user.role,
      scope: "swiftpay:payments:read swiftpay:payments:write swiftpay:disbursements:read swiftpay:disbursements:write"
    })
      .setProtectedHeader({ alg: "HS256", typ: "JWT" })
      .setSubject(email)
      .setIssuer("swiftpay-merchant-backend")
      .setAudience("swiftpay-merchant-api")
      .setIssuedAt()
      .setExpirationTime(`${expiresInSeconds}s`)
      .sign(key);

    return { accessToken, expiresInSeconds, email, role: user.role };
  }

  async function requestRegistrationOtp(emailValue) {
    const email = emailValue.trim().toLowerCase();
    if (!userStore?.getRegistrationSettings || !userStore?.createRegistrationDraft) return;
    const settings = await userStore.getRegistrationSettings();
    if (!settings.enabled || !settings.secureStorageAvailable || await userStore.getUser(email)) return;

    const sentAtNow = now();
    for (const [candidate, sentAt] of registrationLastSentAt) {
      if (sentAtNow - sentAt >= 10 * 60_000) {
        registrationLastSentAt.delete(candidate);
        registrationChallenges.delete(candidate);
      }
    }
    const previousSentAt = registrationLastSentAt.get(email) || 0;
    if (sentAtNow - previousSentAt < 60_000 || registrationLastSentAt.size >= 10_000) return;

    const code = codeGenerator();
    const codeHash = createHmac("sha256", key).update(`registration:${email}:${code}`).digest();
    const challenge = { codeHash, expiresAt: sentAtNow + 10 * 60_000, attempts: 0 };
    registrationChallenges.set(email, challenge);
    registrationLastSentAt.set(email, sentAtNow);
    try {
      await sendEmail(email, code, "registration");
    } catch (error) {
      if (registrationChallenges.get(email) === challenge) {
        registrationChallenges.delete(email);
        registrationLastSentAt.delete(email);
      }
      throw error;
    }
  }

  async function verifyRegistrationOtp(emailValue, code) {
    const email = emailValue.trim().toLowerCase();
    const challenge = registrationChallenges.get(email);
    if (
      !challenge ||
      challenge.expiresAt <= now() ||
      challenge.attempts >= 5 ||
      await getActiveUser(email) ||
      !userStore?.createRegistrationDraft
    ) {
      registrationChallenges.delete(email);
      return null;
    }
    challenge.attempts += 1;
    const submittedHash = createHmac("sha256", key).update(`registration:${email}:${code}`).digest();
    if (!timingSafeEqual(challenge.codeHash, submittedHash)) return null;
    registrationChallenges.delete(email);
    const settings = await userStore.getRegistrationSettings();
    if (!settings.enabled || !settings.secureStorageAvailable) return null;
    const draft = await userStore.createRegistrationDraft(email);
    if (draft.status === "approved") return null;

    const expiresInSeconds = 60 * 60;
    const accessToken = await new SignJWT({ role: "registration_applicant", scope: "kyb:submit" })
      .setProtectedHeader({ alg: "HS256", typ: "JWT" })
      .setSubject(email)
      .setIssuer("swiftpay-merchant-backend")
      .setAudience("swiftpay-merchant-api")
      .setIssuedAt()
      .setExpirationTime(`${expiresInSeconds}s`)
      .sign(key);
    return { accessToken, expiresInSeconds, email, role: "registration_applicant" };
  }

  return { requestOtp, verifyOtp, requestRegistrationOtp, verifyRegistrationOtp };
}

export async function sendOtpEmail(config, email, code, fetchImpl = fetch, purpose = "sign-in") {
  const registration = purpose === "registration";
  const subject = registration ? "Verify your SwiftPay merchant application" : "Your DRPAY verification code";
  const message = registration
    ? `Your SwiftPay merchant registration verification code is ${code}. It expires in 10 minutes.`
    : `Your DRPAY verification code is ${code}. Use it to sign in or create your account. It expires in 10 minutes.`;
  const response = await fetchImpl("https://api.resend.com/emails", {
    method: "POST",
    headers: {
      Authorization: `Bearer ${config.resendApiKey}`,
      "Content-Type": "application/json"
    },
    body: JSON.stringify({
      from: config.otpFromEmail,
      to: [email],
      subject,
      text: message
    }),
    signal: AbortSignal.timeout(10000)
  });
  if (!response.ok) {
    const responseBody = await response.text();
    const providerError = (() => {
      try {
        return JSON.parse(responseBody);
      } catch {
        return null;
      }
    })();
    const providerCode = [providerError?.name, providerError?.code]
      .find((value) => typeof value === "string" && /^[a-zA-Z0-9_.-]{1,64}$/.test(value));
    const codeDetail = providerCode ? `, ${providerCode}` : "";
    const providerMessage = typeof providerError?.message === "string"
      ? providerError.message
        .replace(/[\w.+-]+@[\w.-]+\.[a-zA-Z]{2,}/g, "[redacted-email]")
        .replace(/\b(?:re|rnd)_[a-zA-Z0-9_-]+\b/g, "[redacted-key]")
        .replace(/\bBearer\s+\S+/gi, "Bearer [redacted]")
        .replace(/[\r\n\t]+/g, " ")
        .slice(0, 200)
      : "";
    const messageDetail = providerMessage ? `: ${providerMessage}` : "";
    throw new Error(`OTP email provider rejected request (HTTP ${response.status}${codeDetail})${messageDetail}`);
  }
}

export function principalHasScope(principal, requiredScope) {
  const scopes = [
    ...(typeof principal.scope === "string" ? principal.scope.split(/\s+/) : []),
    ...(Array.isArray(principal.scopes) ? principal.scopes : []),
    ...(Array.isArray(principal.permissions) ? principal.permissions : [])
  ];
  return scopes.includes(requiredScope);
}