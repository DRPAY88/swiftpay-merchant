const SANDBOX_URL = "https://api.pay.sandbox.live.swiftpay.ph/api/";

export function loadConfig(env = process.env) {
  const required = [
    "SWIFTPAY_ACCESS_KEY",
    "SWIFTPAY_SECRET_KEY",
    "SESSION_SIGNING_KEY",
    "AUTH_ALLOWED_EMAILS",
    "RESEND_API_KEY",
    "OTP_FROM_EMAIL"
  ];
  const missing = required.filter((key) => !env[key]?.trim());
  if (missing.length) {
    throw new Error(`Missing required environment variables: ${missing.join(", ")}`);
  }

  const accessKey = env.SWIFTPAY_ACCESS_KEY.trim();
  const secretKey = env.SWIFTPAY_SECRET_KEY.trim();
  if (!/^[\x21-\x7e]{32}$/.test(accessKey) || !/^[\x21-\x7e]{32}$/.test(secretKey)) {
    throw new Error("SwiftPay access and secret keys must each be 32 printable ASCII characters");
  }

  const baseUrl = new URL(env.SWIFTPAY_BASE_URL?.trim() || SANDBOX_URL);
  if (baseUrl.protocol !== "https:") {
    throw new Error("SwiftPay API must use HTTPS");
  }
  if (baseUrl.hostname === "api.pay.live.swiftpay.ph" && env.SWIFTPAY_ALLOW_LIVE !== "true") {
    throw new Error("Live SwiftPay access requires SWIFTPAY_ALLOW_LIVE=true");
  }
  baseUrl.pathname = `${baseUrl.pathname.replace(/\/+$/, "")}/`;
  const sessionSigningKey = env.SESSION_SIGNING_KEY;
  if (Buffer.byteLength(sessionSigningKey, "utf8") < 32) {
    throw new Error("SESSION_SIGNING_KEY must contain at least 32 bytes");
  }
  const allowedEmails = [
    ...env.AUTH_ALLOWED_EMAILS.split(","),
    ...(env.AUTH_ADDITIONAL_ALLOWED_EMAILS || "").split(","),
    ...(env.SUPER_ADMIN_EMAILS || "").split(",")
  ].map((email) => email.trim().toLowerCase()).filter(Boolean);
  const superAdminEmails = [...new Set((env.SUPER_ADMIN_EMAILS || "").split(",").map((email) => email.trim().toLowerCase()).filter(Boolean))];
  if (allowedEmails.some((email) => !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email))) {
    throw new Error("AUTH_ALLOWED_EMAILS, AUTH_ADDITIONAL_ALLOWED_EMAILS, and SUPER_ADMIN_EMAILS must contain valid comma-separated email addresses");
  }

  return {
    port: Number(env.PORT || 3000),
    swiftPayBaseUrl: baseUrl,
    swiftPayAccessKey: accessKey,
    swiftPaySecretKey: secretKey,
    sessionSigningKey,
    allowedEmails: [...new Set(allowedEmails)],
    superAdminEmails,
    databaseUrl: env.DATABASE_URL?.trim() || null,
    resendApiKey: env.RESEND_API_KEY,
    otpFromEmail: env.OTP_FROM_EMAIL
  };
}