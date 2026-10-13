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
  const publicBaseUrl = env.PUBLIC_BASE_URL?.trim()
    ? new URL(env.PUBLIC_BASE_URL.trim())
    : null;
  if (publicBaseUrl && (
    publicBaseUrl.protocol !== "https:" ||
    publicBaseUrl.username ||
    publicBaseUrl.password ||
    publicBaseUrl.pathname !== "/" ||
    publicBaseUrl.search ||
    publicBaseUrl.hash
  )) {
    throw new Error("PUBLIC_BASE_URL must be an HTTPS origin without a path or embedded credentials");
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
  const transakApiKey = env.TRANSAK_API_KEY?.trim() || null;
  const transakApiSecret = env.TRANSAK_API_SECRET?.trim() || null;
  if (Boolean(transakApiKey) !== Boolean(transakApiSecret)) {
    throw new Error("TRANSAK_API_KEY and TRANSAK_API_SECRET must be configured together");
  }
  const transakEnvironment = (env.TRANSAK_ENVIRONMENT || "staging").trim().toLowerCase();
  if (!["staging", "production"].includes(transakEnvironment)) {
    throw new Error("TRANSAK_ENVIRONMENT must be staging or production");
  }
  const fcmServiceAccountJson = env.FCM_SERVICE_ACCOUNT_JSON?.trim() || null;
  if (fcmServiceAccountJson) {
    let serviceAccount;
    try {
      serviceAccount = JSON.parse(fcmServiceAccountJson);
    } catch {
      throw new Error("FCM_SERVICE_ACCOUNT_JSON must contain valid JSON");
    }
    if (
      typeof serviceAccount.project_id !== "string" ||
      typeof serviceAccount.client_email !== "string" ||
      typeof serviceAccount.private_key !== "string"
    ) {
      throw new Error("FCM_SERVICE_ACCOUNT_JSON must contain project_id, client_email, and private_key");
    }
  }

  return {
    port: Number(env.PORT || 3000),
    swiftPayBaseUrl: baseUrl,
    publicBaseUrl,
    swiftPayAccessKey: accessKey,
    swiftPaySecretKey: secretKey,
    sessionSigningKey,
    allowedEmails: [...new Set(allowedEmails)],
    superAdminEmails,
    databaseUrl: env.DATABASE_URL?.trim() || null,
    resendApiKey: env.RESEND_API_KEY,
    otpFromEmail: env.OTP_FROM_EMAIL,
    currencyApiKey: env.CURRENCYAPI_KEY?.trim() || null,
    fcmServiceAccountJson,
    transak: {
      apiKey: transakApiKey,
      apiSecret: transakApiSecret,
      environment: transakEnvironment
    }
  };
}