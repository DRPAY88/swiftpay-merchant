import express from "express";
import { randomUUID } from "node:crypto";
import { fileURLToPath } from "node:url";
import rateLimit from "express-rate-limit";
import helmet from "helmet";
import { z } from "zod";
import { principalHasScope } from "./auth.js";
import {
  buildOrderPayload,
  buildQrPayload,
  callSwiftPay,
  newReference,
  SwiftPayError,
  verifySwiftPaySignature
} from "./swiftpay.js";
import { createFxPhpQuote, FxQuoteError, getLatestPhpRate, verifyFxPhpQuote } from "./fx-quotes.js";
import { TransakError } from "./transak.js";

const amountSchema = z.number().finite().min(0.01).max(1_000_000);
const referenceSchema = z.string().trim().min(1).max(50);
const currencySchema = z.enum(["PHP", "USD", "EUR"]);
const qrCurrencySchema = z.enum(["PHP", "KRW", "USD", "EUR"]);

const orderSchema = z.object({
  amount: amountSchema,
  referenceNo: referenceSchema,
  currency: currencySchema.default("PHP"),
  details: z.record(z.string(), z.unknown()).default({}),
  institutionCode: z.string().trim().min(1).max(16).optional(),
  merchantTag: z.string().trim().max(64).optional()
}).strict();

const directQrPaymentSchema = z.object({
  amount: amountSchema,
  referenceNo: referenceSchema,
  currency: z.literal("PHP").default("PHP"),
  rejectDuplicatedPayments: z.boolean().default(true)
}).strict();
const quotedQrPaymentSchema = z.object({
  quoteToken: z.string().min(1).max(2048),
  rejectDuplicatedPayments: z.boolean().default(true)
}).strict();
const qrPaymentSchema = z.union([directQrPaymentSchema, quotedQrPaymentSchema]);
const krwPhpQuoteRequestSchema = z.object({
  amountKrw: z.number().int().min(1).max(1_000_000)
}).strict();
const fxPhpQuoteRequestSchema = z.union([
  z.object({
    amount: z.number().finite().min(0.01).max(1_000_000),
    currency: z.enum(["KRW", "USD", "EUR"])
  }).strict(),
  krwPhpQuoteRequestSchema
]);

const paymentLinkSchema = z.object({
  amount: amountSchema,
  currency: currencySchema.default("PHP"),
  title: z.string().max(100).optional(),
  description: z.string().max(500).optional(),
  referenceNo: z.string().trim().max(50).optional(),
  validUntil: z.string().datetime({ offset: true }).optional(),
  customerName: z.string().max(150).optional()
}).strict();
const paymentLinkCodeSchema = z.string().trim().min(1).max(128).regex(/^[a-zA-Z0-9_-]+$/);
const paymentInstitutionSchema = z.object({
  code: z.string().trim().min(1).max(128),
  name: z.string().trim().min(1).max(200),
  logoUrl: z.string().url().nullish(),
  enabled: z.boolean().optional(),
  live: z.boolean().optional(),
  minAmount: z.number().nonnegative().optional(),
  maxAmount: z.number().nonnegative().optional(),
  currencies: z.array(z.string()).optional()
}).passthrough();

const disbursementSchema = z.object({
  amount: amountSchema,
  referenceNo: referenceSchema,
  channel: z.enum(["INSTAPAY", "PESONET"]),
  type: z.enum(["P2P", "QR_P2P", "QR_P2M", "QR_P2MICRO"]).optional(),
  institutionCode: z.string().trim().min(1).max(64),
  recipientInformation: z.object({
    accountNumber: z.string().trim().min(1).max(64),
    fullName: z.string().trim().min(1).max(128).refine(
      (fullName) => fullName.split(/\s+/u).filter(Boolean).length >= 2,
      "Recipient full legal name must include the given name and surname"
    ),
    mobileNumber: z.string().regex(/^[0-9+-]*$/).max(35).optional(),
    email: z.string().email().max(255).optional()
  }),
  remarks: z.string().trim().max(80).optional()
});
const merchantEmailSchema = z.string().trim().email().max(255).transform((email) => email.toLowerCase());
const merchantStatusSchema = z.object({
  status: z.enum(["active", "suspended", "removed"])
}).strict();
const onboardingSettingSchema = z.object({
  enabled: z.boolean()
}).strict();
const registrationProfileSchema = z.object({
  businessName: z.string().trim().min(2).max(160),
  businessRegistrationNumber: z.string().trim().min(2).max(80),
  businessAddress: z.string().trim().min(5).max(300),
  businessType: z.string().trim().min(2).max(100),
  representativeFullName: z.string().trim().min(3).max(160),
  representativePhone: z.string().trim().min(5).max(35)
}).strict();
const registrationReviewSchema = z.object({
  status: z.enum(["approved", "rejected"]),
  note: z.string().trim().max(500).default("")
}).strict();
const registrationEmailSchema = z.string().trim().email().max(255).transform((email) => email.toLowerCase());
const registrationDocumentKinds = new Set(["business_certificate", "representative_id", "selfie"]);
const registrationDocumentLimit = 8 * 1024 * 1024;

function validRegistrationDocument(data, contentType, kind) {
  if (!Buffer.isBuffer(data) || data.length === 0 || data.length > registrationDocumentLimit) return false;
  if (kind === "selfie" && !["image/jpeg", "image/png"].includes(contentType)) return false;
  if (contentType === "application/pdf") return data.subarray(0, 5).toString("ascii") === "%PDF-";
  if (contentType === "image/png") return data.subarray(0, 8).equals(Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]));
  if (contentType === "image/jpeg") return data[0] === 0xff && data[1] === 0xd8 && data.at(-2) === 0xff && data.at(-1) === 0xd9;
  return false;
}
const adjustmentSchema = z.object({
  amount: z.number().finite().min(-1_000_000).max(1_000_000).refine((amount) => amount !== 0),
  reason: z.string().trim().min(3).max(500)
}).strict();
const manualBankDepositSchema = z.object({
  amount: z.number().finite().min(0.01).max(1_000_000),
  bankName: z.string().trim().min(1).max(100),
  referenceNo: referenceSchema,
  notes: z.string().trim().max(500).default("")
}).strict();

function asyncRoute(handler) {
  return (req, res, next) => Promise.resolve(handler(req, res, next)).catch(next);
}

function requireScope(scope) {
  return (req, res, next) => {
    if (!principalHasScope(req.principal, scope)) {
      return res.status(403).json({ error: "insufficient_scope", requiredScope: scope });
    }
    next();
  };
}

function parse(schema, value) {
  const result = schema.safeParse(value);
  if (!result.success) {
    throw new SwiftPayError(400, result.error.issues.map((issue) => `${issue.path.join(".")}: ${issue.message}`).join("; "));
  }
  return result.data;
}

function escapeHtml(value) {
  return String(value).replace(/[&<>"']/g, (character) => ({
    "&": "&amp;",
    "<": "&lt;",
    ">": "&gt;",
    "\"": "&quot;",
    "'": "&#39;"
  })[character]);
}

function trustedInstitutionLogoUrl(logoUrl) {
  if (!logoUrl) return null;
  try {
    const logo = new URL(logoUrl);
    if (
      logo.protocol === "https:" &&
      (
        logo.hostname === "swiftpay.ph" ||
        logo.hostname.endsWith(".swiftpay.ph") ||
        [
          "swiftpay-qa-gateway-frontend.s3-ap-southeast-1.amazonaws.com",
          "swiftpay-qa-gateway-frontend.s3.ap-southeast-1.amazonaws.com",
          "swiftpay-prod-gateway-frontend.s3.ap-southeast-1.amazonaws.com"
        ].includes(logo.hostname)
      ) &&
      !logo.username &&
      !logo.password
    ) {
      return logo.href;
    }
  } catch {
    return null;
  }
  return null;
}

function checkoutPage({ code, title, amount, currency, paymentUrl, institutions, institutionLoadFailed }) {
  const displayTitle = escapeHtml(title);
  const displayAmount = escapeHtml(new Intl.NumberFormat("en", {
    style: "currency",
    currency
  }).format(amount));
  const safePaymentUrl = escapeHtml(paymentUrl);
  const institutionCards = institutions.map(({ name, logoUrl }) => `
      <li class="institution-card${logoUrl ? "" : " institution-card--text"}">
        ${logoUrl ? `<img src="${escapeHtml(logoUrl)}" alt="${escapeHtml(name)} logo" loading="lazy" decoding="async">` : ""}
        <span>${escapeHtml(name)}</span>
      </li>`).join("");
  const institutionContent = institutionCards
    ? `<ul class="institution-grid" aria-label="Available banks and wallets">${institutionCards}</ul>`
    : `<p class="institution-empty">${institutionLoadFailed
      ? "Bank and wallet options are available on SwiftPay’s secure payment page."
      : "More bank and wallet options are available on SwiftPay’s secure payment page."}</p>`;
  return `<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <meta name="referrer" content="no-referrer">
  <meta name="theme-color" content="#f4f7fb">
  <title>Checkout · DRPAY</title>
  <link rel="stylesheet" href="/checkout-assets/checkout.css">
</head>
<body>
  <main class="checkout-card">
    <header class="checkout-header">
      <div class="brand-mark"><img src="/checkout-assets/drpay_mark.png" alt="DRPAY"></div>
      <div class="brand-copy">
        <p class="eyebrow">DRPAY checkout</p>
        <p class="secure-label"><span aria-hidden="true">✓</span> Secure payment</p>
      </div>
    </header>
    <section class="payment-summary" aria-labelledby="checkout-title">
      <p class="summary-label">Payment request</p>
      <h1 id="checkout-title">${displayTitle}</h1>
      <p class="amount">${displayAmount}</p>
      <p class="summary-note">Select a payment option to continue.</p>
    </section>
    <section class="payment-methods" aria-labelledby="payment-methods-title">
      <div class="section-heading">
        <div>
          <h2 id="payment-methods-title">Banks &amp; wallets</h2>
          <p>Official payment partner logos</p>
        </div>
        <span class="powered-badge">Powered by SwiftPay</span>
      </div>
      ${institutionContent}
      <a class="pay-button" href="${safePaymentUrl}" rel="noopener noreferrer">
        Continue to secure payment
        <span aria-hidden="true">→</span>
      </a>
      <p class="provider-note">You’ll see all supported banks and payment methods on SwiftPay’s secure page.</p>
    </section>
    <div class="checkout-divider"><span>or</span></div>
    <form class="qr-form" action="/checkout/${encodeURIComponent(code)}/qrph" method="post">
      <input type="hidden" name="paymentApp" value="qrph">
      <button class="qr-button" type="submit">
        <span class="qr-icon" aria-hidden="true">QR</span>
        <span class="qr-copy"><strong>Pay with QR Ph</strong><small>Use a banking or wallet app that supports QR Ph</small></span>
        <span class="qr-arrow" aria-hidden="true">→</span>
      </button>
    </form>
    <footer class="checkout-footer">
      <span class="footer-lock" aria-hidden="true">●</span>
      Payment details are securely handled by SwiftPay.
    </footer>
  </main>
</body>
</html>`;
}

function checkoutQrPage({ code, title, amount, currency, paymentApp, phpAmount, phpPerUnit, imageBase64 }) {
  const displayTitle = escapeHtml(title);
  const customerAmount = escapeHtml(new Intl.NumberFormat("en", { style: "currency", currency }).format(amount));
  const settlementAmount = escapeHtml(new Intl.NumberFormat("en-PH", { style: "currency", currency: "PHP" }).format(phpAmount));
  const rateDescription = currency === "PHP"
    ? "No currency conversion was needed."
    : `Rate used: 1 ${escapeHtml(currency)} = ${phpPerUnit.toFixed(6)} PHP.`;
  return `<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <meta name="referrer" content="no-referrer">
  <meta name="theme-color" content="#f4f7fb">
  <title>QR Ph payment · DRPAY</title>
  <link rel="stylesheet" href="/checkout-assets/checkout.css">
</head>
<body>
  <main class="checkout-card">
    <header class="checkout-header">
      <div class="brand-mark"><img src="/checkout-assets/drpay_mark.png" alt="DRPAY"></div>
      <div class="brand-copy">
        <p class="eyebrow">DRPAY checkout</p>
        <p class="secure-label"><span aria-hidden="true">✓</span> Secure payment</p>
      </div>
    </header>
    <section class="payment-summary" aria-labelledby="checkout-title">
      <p class="summary-label">QR Ph payment</p>
      <h1 id="checkout-title">${paymentApp ? `Scan with ${escapeHtml(paymentApp)}` : "Scan to pay"}</h1>
      <p class="amount">${customerAmount}</p>
      <p class="summary-note">${displayTitle}</p>
    </section>
    <p class="qr-instructions">Scan this code with a banking or wallet app that supports QR Ph. The payment settles in PHP at <strong>${settlementAmount}</strong>. Your bank or wallet may apply its own conversion.</p>
    <img class="checkout-qr" src="data:image/png;base64,${imageBase64}" alt="QR Ph payment code">
    <p class="rate-note">${rateDescription}</p>
    <div class="qr-back">
      <a class="pay-button" href="/checkout/${encodeURIComponent(code)}">Back to payment options</a>
    </div>
    <footer class="checkout-footer">
      <span class="footer-lock" aria-hidden="true">●</span>
      Payment details are securely handled by SwiftPay.
    </footer>
  </main>
</body>
</html>`;
}

function checkoutErrorPage(message) {
  return `<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <meta name="theme-color" content="#f4f7fb">
  <title>Checkout unavailable · DRPAY</title>
  <link rel="stylesheet" href="/checkout-assets/checkout.css">
</head>
<body>
  <main class="checkout-card">
    <header class="checkout-header">
      <div class="brand-mark"><img src="/checkout-assets/drpay_mark.png" alt="DRPAY"></div>
      <div class="brand-copy"><p class="eyebrow">DRPAY checkout</p></div>
    </header>
    <section class="payment-summary">
      <p class="summary-label">Payment request</p>
      <h1>Checkout unavailable</h1>
      <p class="summary-note error-message">${escapeHtml(message)}</p>
    </section>
  </main>
</body>
</html>`;
}

function createAuthMiddleware(verifyAccessToken, userStore) {
  return asyncRoute(async (req, res, next) => {
    const match = /^Bearer\s+([^\s]+)$/i.exec(req.get("authorization") || "");
    if (!match) {
      return res.status(401).json({ error: "unauthorized" });
    }
    try {
      req.principal = await verifyAccessToken(match[1]);
    } catch {
      return res.status(401).json({ error: "unauthorized" });
    }
    if (userStore) {
      const user = await userStore.getUser(req.principal.sub.toLowerCase());
      if (user?.status === "active") {
        req.principal.role = user.role;
      } else {
        if (req.principal.role !== "registration_applicant" || !principalHasScope(req.principal, "kyb:submit")) {
          return res.status(401).json({ error: "unauthorized" });
        }
        const registration = await userStore.getRegistrationStatus?.(req.principal.sub);
        if (!registration || registration.status === "approved") {
          return res.status(401).json({ error: "unauthorized" });
        }
        req.principal.role = "registration_applicant";
      }
    }
    next();
  });
}

function requireSuperAdmin(req, res, next) {
  if (req.principal?.role !== "super_admin") {
    return res.status(403).json({ error: "super_admin_required" });
  }
  next();
}

export function createApp({
  config,
  fetchImpl = fetch,
  fxFetchImpl = fetch,
  verifyAccessToken,
  requestOtp,
  verifyOtp,
  requestRegistrationOtp,
  verifyRegistrationOtp,
  cryptoWidgetService,
  pushNotificationService,
  userStore,
  clock = () => Date.now()
}) {
  if (!verifyAccessToken) throw new Error("A JWT verifier is required");
  if (!requestOtp || !verifyOtp) throw new Error("OTP auth handlers are required");
  const app = express();
  const requestProvider = (path, options) => callSwiftPay(config, fetchImpl, path, options);
  let paymentInstitutionCatalogue = null;
  let paymentInstitutionCatalogueLoadedAt = 0;
  let paymentInstitutionCatalogueRetryAt = 0;
  let paymentInstitutionCatalogueRequest = null;

  async function loadPaymentInstitutionCatalogue() {
    const currentTime = clock();
    if (paymentInstitutionCatalogue && currentTime - paymentInstitutionCatalogueLoadedAt < 60_000) {
      return { institutions: paymentInstitutionCatalogue, failed: false };
    }
    if (currentTime < paymentInstitutionCatalogueRetryAt) {
      return { institutions: paymentInstitutionCatalogue || [], failed: true };
    }
    if (paymentInstitutionCatalogueRequest) return paymentInstitutionCatalogueRequest;

    paymentInstitutionCatalogueRequest = (async () => {
      try {
        const result = await requestProvider("institutions");
        const parsedInstitutions = z.array(paymentInstitutionSchema).safeParse(result.data);
        if (!parsedInstitutions.success) {
          throw new SwiftPayError(502, "SwiftPay returned an invalid payment institution catalogue");
        }
        paymentInstitutionCatalogue = parsedInstitutions.data;
        paymentInstitutionCatalogueLoadedAt = clock();
        paymentInstitutionCatalogueRetryAt = 0;
        return { institutions: paymentInstitutionCatalogue, failed: false };
      } catch (error) {
        paymentInstitutionCatalogueRetryAt = clock() + 30_000;
        return {
          institutions: paymentInstitutionCatalogue || [],
          failed: true,
          error
        };
      } finally {
        paymentInstitutionCatalogueRequest = null;
      }
    })();
    return paymentInstitutionCatalogueRequest;
  }

  const loadPublicPaymentLink = async (code) => {
    const result = await requestProvider(`payments/links/${encodeURIComponent(code)}`, { auth: "basic" });
    let paymentUrl;
    try {
      paymentUrl = new URL(result.data?.paymentUrl);
    } catch {
      throw new SwiftPayError(502, "SwiftPay returned invalid payment-link details");
    }
    const amount = Number(result.data?.amount);
    const currency = currencySchema.parse(result.data?.currency || "PHP");
    const status = String(result.data?.linkStatus || result.data?.status || "").toUpperCase();
    if (
      paymentUrl.protocol !== "https:" ||
      paymentUrl.username ||
      paymentUrl.password ||
      !Number.isFinite(amount) ||
      amount <= 0 ||
      ["EXPIRED", "INVALIDATED", "INACTIVE", "CANCELLED"].includes(status)
    ) {
      throw new SwiftPayError(502, "SwiftPay returned invalid payment-link details");
    }
    return {
      ...result.data,
      amount,
      currency,
      paymentUrl
    };
  };
  app.disable("x-powered-by");
  app.set("trust proxy", 1);
  app.use(helmet());
  app.use("/admin", express.static(fileURLToPath(new URL("../public/", import.meta.url)), {
    index: "index.html",
    maxAge: "1h"
  }));
  app.use("/checkout-assets", express.static(fileURLToPath(new URL("../public/checkout-assets/", import.meta.url)), {
    maxAge: "1h",
    fallthrough: false
  }));
  app.use(express.json({ limit: "32kb", strict: true }));
  app.use(express.urlencoded({ extended: false, limit: "4kb" }));
  app.use(rateLimit({
    windowMs: 60_000,
    limit: 120,
    standardHeaders: "draft-8",
    legacyHeaders: false,
    skip: (req) => req.path === "/v1/webhooks/swiftpay"
  }));
  const webhookLimiter = rateLimit({ windowMs: 60_000, limit: 600, standardHeaders: "draft-8", legacyHeaders: false });
  app.get("/healthz", (_req, res) => res.json({ status: "ok" }));
  app.get("/checkout/:code", asyncRoute(async (req, res) => {
    const code = paymentLinkCodeSchema.parse(req.params.code);
    try {
      const link = await loadPublicPaymentLink(code);
      let institutions = [];
      let institutionLoadFailed = false;
      const catalogue = await loadPaymentInstitutionCatalogue();
      institutionLoadFailed = catalogue.failed;
      if (catalogue.error) {
        const error = catalogue.error;
        console.error("Checkout institution catalogue request failed", JSON.stringify({
          errorName: error instanceof Error ? error.name : "UnknownError",
          ...(error instanceof SwiftPayError ? {
            status: error.status,
            ...(error.providerStatus ? { providerStatus: error.providerStatus } : {}),
            ...(error.errorCode ? { errorCode: error.errorCode } : {})
          } : {})
        }));
      }
      institutions = catalogue.institutions
        .filter((institution) =>
          institution.enabled !== false &&
          institution.live !== false &&
          (!institution.currencies || institution.currencies.includes(link.currency)) &&
          (institution.minAmount === undefined || link.amount >= institution.minAmount) &&
          (institution.maxAmount === undefined || link.amount <= institution.maxAmount)
        )
        .map(({ name, logoUrl }) => ({ name, logoUrl: trustedInstitutionLogoUrl(logoUrl) }));
      const title = [link.title, link.description]
        .find((value) => typeof value === "string" && value.trim())
        || "Payment request";
      res.set({
        "Cache-Control": "no-store",
        "Content-Security-Policy": "default-src 'self'; img-src 'self' data: https://swiftpay.ph https://*.swiftpay.ph https://swiftpay-qa-gateway-frontend.s3-ap-southeast-1.amazonaws.com https://swiftpay-qa-gateway-frontend.s3.ap-southeast-1.amazonaws.com https://swiftpay-prod-gateway-frontend.s3.ap-southeast-1.amazonaws.com; style-src 'self'; base-uri 'none'; form-action 'self'; frame-ancestors 'none'"
      });
      res.type("html").send(checkoutPage({
        code,
        title,
        amount: link.amount,
        currency: link.currency,
        paymentUrl: link.paymentUrl.href,
        institutions,
        institutionLoadFailed
      }));
    } catch (error) {
      if (error instanceof SwiftPayError && [404, 410].includes(error.status)) {
        res.status(error.status).set("Cache-Control", "no-store").type("html")
          .send(checkoutErrorPage("This payment link has expired or is no longer available."));
        return;
      }
      throw error;
    }
  }));
  const checkoutPaymentLimiter = rateLimit({ windowMs: 60_000, limit: 10, standardHeaders: "draft-8", legacyHeaders: false });
  app.post("/checkout/:code/qrph", checkoutPaymentLimiter, asyncRoute(async (req, res) => {
    const code = paymentLinkCodeSchema.parse(req.params.code);
    const link = await loadPublicPaymentLink(code);
    const paymentApp = z.literal("qrph").default("qrph")
      .parse(req.body?.paymentApp || "qrph");
    const rate = link.currency === "PHP"
      ? { phpPerUnit: 1, updatedAt: new Date().toISOString() }
      : await getLatestPhpRate(link.currency, config.currencyApiKey, fxFetchImpl);
    const phpAmount = Math.round(link.amount * rate.phpPerUnit * 100) / 100;
    if (!Number.isFinite(phpAmount) || phpAmount < 0.01 || phpAmount > 1_000_000) {
      throw new FxQuoteError(400, "The converted PHP QR amount is outside the supported range");
    }
    const qrInput = {
      amount: phpAmount,
      referenceNo: `CKQR-${newReference()}`,
      currency: "PHP",
      rejectDuplicatedPayments: true
    };
    const bootstrap = await requestProvider("bootstrap/qrph?type=P2M", {
      method: "POST",
      body: buildQrPayload(qrInput, config)
    });
    const payment = z.object({
      paymentId: z.string().min(1),
      paymentStatus: z.string().min(1),
      referenceNo: z.string().min(1),
      amount: z.union([z.number(), z.string()]),
      currency: z.literal("PHP").optional(),
      qrCode: z.string().min(1)
    }).safeParse(bootstrap.data);
    if (!payment.success || !Number.isFinite(Number(payment.data.amount))) {
      throw new SwiftPayError(502, "SwiftPay returned an incomplete QR Ph payment");
    }
    if (Math.round(Number(payment.data.amount) * 100) !== Math.round(phpAmount * 100)) {
      throw new SwiftPayError(502, "SwiftPay returned a QR Ph amount that does not match the quoted PHP amount");
    }
    const image = await requestProvider("payments/qrph/image?type=P2M", {
      paymentToken: payment.data.paymentId,
      responseType: "bytes"
    });
    const title = [link.title, link.description]
      .find((value) => typeof value === "string" && value.trim())
      || "Payment request";
    res.set({
      "Cache-Control": "no-store",
      "Content-Security-Policy": "default-src 'self'; img-src 'self' data:; style-src 'self'; base-uri 'none'; form-action 'self'; frame-ancestors 'none'"
    });
    res.type("html").send(checkoutQrPage({
      code,
      title,
      amount: link.amount,
      currency: link.currency,
      paymentApp: paymentApp === "qrph" ? null : paymentApp,
      phpAmount,
      phpPerUnit: rate.phpPerUnit,
      imageBase64: image.data.toString("base64")
    }));
  }));
  const authLimiter = rateLimit({ windowMs: 60_000, limit: 5, standardHeaders: "draft-8", legacyHeaders: false });
  app.get("/v1/registration/config", asyncRoute(async (_req, res) => {
    if (!userStore?.getRegistrationSettings) {
      return res.json({ enabled: false, secureStorageAvailable: false });
    }
    res.set("Cache-Control", "no-store").json(await userStore.getRegistrationSettings());
  }));
  app.post("/v1/registration/request-otp", authLimiter, asyncRoute(async (req, res) => {
    const input = parse(z.object({ email: z.string().trim().email().max(255) }).strict(), req.body);
    if (!requestRegistrationOtp) throw new SwiftPayError(503, "Registration email verification is unavailable");
    try {
      await requestRegistrationOtp(input.email);
    } catch (error) {
      console.error("Registration verification email delivery failed:", error.message);
      return res.status(503).json({ error: "Verification email could not be sent; try again later" });
    }
    res.status(202).json({ status: "accepted" });
  }));
  app.post("/v1/registration/verify-otp", authLimiter, asyncRoute(async (req, res) => {
    const input = parse(z.object({
      email: z.string().trim().email().max(255),
      code: z.string().regex(/^\d{6}$/)
    }).strict(), req.body);
    if (!verifyRegistrationOtp) throw new SwiftPayError(503, "Registration email verification is unavailable");
    const session = await verifyRegistrationOtp(input.email, input.code);
    if (!session) return res.status(401).json({ error: "invalid_or_expired_code" });
    res.set("Cache-Control", "no-store").json(session);
  }));
  app.post("/v1/auth/request-otp", authLimiter, asyncRoute(async (req, res) => {
    const input = parse(z.object({ email: z.string().trim().email().max(255) }).strict(), req.body);
    try {
      await requestOtp(input.email);
    } catch (error) {
      console.error("OTP email delivery failed:", error.message);
      return res.status(503).json({ error: "Sign-in email could not be sent; try again later" });
    }
    res.status(202).json({ status: "accepted" });
  }));
  app.post("/v1/auth/verify-otp", authLimiter, asyncRoute(async (req, res) => {
    const input = parse(z.object({
      email: z.string().trim().email().max(255),
      code: z.string().regex(/^\d{6}$/)
    }).strict(), req.body);
    const session = await verifyOtp(input.email, input.code);
    if (!session) return res.status(401).json({ error: "invalid_or_expired_code" });
    res.json(session);
  }));
  const pushTokenSchema = z.object({
    token: z.string().trim().min(20).max(4096)
  }).strict();
  app.post("/v1/notifications/push-token", requireScope("swiftpay:payments:read"), asyncRoute(async (req, res) => {
    if (!pushNotificationService || !userStore?.upsertPushToken) {
      throw new SwiftPayError(503, "Incoming payment push notifications are not configured");
    }
    const input = parse(pushTokenSchema, req.body);
    await userStore.upsertPushToken(req.principal.sub, input.token);
    res.status(204).end();
  }));
  app.delete("/v1/notifications/push-token", requireScope("swiftpay:payments:read"), asyncRoute(async (req, res) => {
    if (!userStore?.deletePushToken) {
      throw new SwiftPayError(503, "Push notification token storage is unavailable");
    }
    const input = parse(pushTokenSchema, req.body);
    await userStore.deletePushToken(req.principal.sub, input.token);
    res.status(204).end();
  }));

  app.post("/v1/webhooks/swiftpay", webhookLimiter, asyncRoute(async (req, res) => {
    const entries = Object.entries(req.query);
    if (
      entries.length === 0 ||
      entries.length > 32 ||
      entries.some(([key, value]) => !/^[a-zA-Z0-9_]{1,64}$/.test(key) || typeof value !== "string") ||
      Buffer.byteLength(JSON.stringify(req.query), "utf8") > 8192 ||
      (req.body && Object.keys(req.body).length > 0)
    ) {
      return res.status(400).json({ error: "invalid_webhook_payload" });
    }

    const fields = Object.fromEntries(entries);
    if (
      (fields.x_access_key !== undefined && fields.x_access_key !== config.swiftPayAccessKey) ||
      !verifySwiftPaySignature(fields, fields.signature, config.swiftPaySecretKey)
    ) {
      return res.status(401).json({ error: "invalid_webhook_signature" });
    }
    if (!userStore?.recordWebhookEvent) {
      return res.status(503).json({ error: "webhook_storage_unavailable" });
    }

    const paymentEvent = z.object({
      x_payment_id: z.string().min(1).max(128).regex(/^[a-zA-Z0-9._:-]+$/),
      x_payment_status: z.enum(["EXECUTED", "REJECTED", "CANCELED", "EXPIRED"]),
      x_reference_no: referenceSchema,
      x_amount: z.coerce.number().finite().positive().optional(),
      x_currency: qrCurrencySchema.optional()
    }).refine((event) => (event.x_amount === undefined) === (event.x_currency === undefined))
      .safeParse(fields);
    if (paymentEvent.success) {
      const details = {
        ...(paymentEvent.data.x_amount !== undefined ? { amount: paymentEvent.data.x_amount } : {}),
        ...(paymentEvent.data.x_currency ? { currency: paymentEvent.data.x_currency } : {}),
        ...(fields.institution_reference_no ? { institutionReferenceNo: fields.institution_reference_no } : {}),
        ...(fields.channel_reference_no ? { channelReferenceNo: fields.channel_reference_no } : {})
      };
      await userStore.recordWebhookEvent({
        eventKey: `payment:${paymentEvent.data.x_payment_id}`,
        eventType: "payment",
        paymentId: paymentEvent.data.x_payment_id,
        referenceNo: paymentEvent.data.x_reference_no,
        status: paymentEvent.data.x_payment_status,
        notifyIncoming: Boolean(pushNotificationService),
        details
      });
      return res.status(200).json({ status: "accepted" });
    }

    const disbursementEvent = z.object({
      x_disbursement_status: z.enum(["EXECUTED", "REJECTED", "ERROR"]),
      x_merchant_reference_no: referenceSchema
    }).safeParse(fields);
    if (!disbursementEvent.success) {
      return res.status(400).json({ error: "invalid_webhook_payload" });
    }
    const details = {
      ...(fields.x_channel_reference_no ? { channelReferenceNo: fields.x_channel_reference_no } : {}),
      ...(fields.x_error_message ? { errorMessage: fields.x_error_message.slice(0, 500) } : {}),
      ...(fields.x_settlement_time ? { settlementTime: fields.x_settlement_time.slice(0, 64) } : {})
    };
    await userStore.recordWebhookEvent({
      eventKey: `disbursement:${disbursementEvent.data.x_merchant_reference_no}`,
      eventType: "disbursement",
      referenceNo: disbursementEvent.data.x_merchant_reference_no,
      status: disbursementEvent.data.x_disbursement_status,
      details
    });
    res.status(200).json({ status: "accepted" });
  }));

  app.use("/v1", createAuthMiddleware(verifyAccessToken, userStore));
  const registrationUploadLimiter = rateLimit({
    windowMs: 60_000,
    limit: 12,
    standardHeaders: "draft-8",
    legacyHeaders: false
  });
  const requireRegistrationApplicant = (req, res, next) => {
    if (req.principal?.role !== "registration_applicant" || !principalHasScope(req.principal, "kyb:submit")) {
      return res.status(403).json({ error: "registration_applicant_required" });
    }
    next();
  };
  app.get("/v1/registration/status", requireRegistrationApplicant, asyncRoute(async (req, res) => {
    const status = await userStore.getRegistrationApplication(req.principal.sub);
    if (!status) return res.status(404).json({ error: "registration_not_found" });
    res.set("Cache-Control", "no-store").json(status);
  }));
  app.put("/v1/registration/profile", requireRegistrationApplicant, asyncRoute(async (req, res) => {
    const input = parse(registrationProfileSchema, req.body);
    res.json(await userStore.saveRegistrationProfile(req.principal.sub, input));
  }));
  app.put(
    "/v1/registration/documents/:kind",
    registrationUploadLimiter,
    requireRegistrationApplicant,
    express.raw({ type: ["image/jpeg", "image/png", "application/pdf"], limit: registrationDocumentLimit }),
    asyncRoute(async (req, res) => {
      const kind = req.params.kind;
      const contentType = req.get("content-type")?.split(";")[0].trim().toLowerCase();
      if (!registrationDocumentKinds.has(kind) || !validRegistrationDocument(req.body, contentType, kind)) {
        throw new SwiftPayError(400, "Unsupported, empty, malformed, or oversized KYB document");
      }
      res.json(await userStore.saveRegistrationDocument(req.principal.sub, kind, contentType, req.body));
    })
  );
  app.post("/v1/registration/submit", requireRegistrationApplicant, asyncRoute(async (req, res) => {
    if (Object.keys(req.body || {}).length) throw new SwiftPayError(400, "Submission does not accept a request body");
    res.status(202).json(await userStore.submitRegistration(req.principal.sub));
  }));

  app.post("/v1/crypto/widget-url", requireScope("swiftpay:payments:write"), asyncRoute(async (req, res) => {
    if (!cryptoWidgetService) throw new SwiftPayError(503, "USDT checkout is not configured for this service");
    const tronAddressSchema = z.string().trim().regex(/^T[1-9A-HJ-NP-Za-km-z]{33}$/, "must be a valid TRON mainnet address");
    const input = parse(z.discriminatedUnion("mode", [
      z.object({
        mode: z.literal("BUY"),
        amount: z.number().finite().min(1).max(1_000_000),
        walletAddress: tronAddressSchema
      }).strict(),
      z.object({ mode: z.literal("SELL"), amount: z.number().finite().min(0.01).max(1_000_000) }).strict()
    ]), req.body);
    const session = await cryptoWidgetService.createWidgetUrl({
      ...input,
      userIp: req.ip,
      customerId: req.principal.sub
    });
    res.json(session);
  }));

  app.get("/v1/admin/me", requireSuperAdmin, (req, res) => {
    res.json({ email: req.principal.sub, role: req.principal.role });
  });

  app.get("/v1/admin/onboarding", requireSuperAdmin, asyncRoute(async (_req, res) => {
    res.json(await userStore.getOnboardingSettings());
  }));

  app.patch("/v1/admin/onboarding", requireSuperAdmin, asyncRoute(async (req, res) => {
    const input = parse(onboardingSettingSchema, req.body);
    res.json(await userStore.setOnboardingEnabled(input.enabled, req.principal.sub));
  }));

  app.get("/v1/admin/registration-settings", requireSuperAdmin, asyncRoute(async (_req, res) => {
    res.json(await userStore.getRegistrationSettings());
  }));

  app.patch("/v1/admin/registration-settings", requireSuperAdmin, asyncRoute(async (req, res) => {
    const input = parse(onboardingSettingSchema, req.body);
    res.json(await userStore.setRegistrationEnabled(input.enabled, req.principal.sub));
  }));

  app.get("/v1/admin/registrations", requireSuperAdmin, asyncRoute(async (_req, res) => {
    res.set("Cache-Control", "no-store").json(await userStore.listRegistrations());
  }));

  app.get("/v1/admin/registrations/:email", requireSuperAdmin, asyncRoute(async (req, res) => {
    const email = parse(registrationEmailSchema, req.params.email);
    const details = await userStore.getRegistrationDetails(email);
    await userStore.recordAudit(req.principal.sub, "registration.viewed", email, {});
    res.set("Cache-Control", "no-store").json(details);
  }));

  app.get("/v1/admin/registrations/:email/documents/:kind", requireSuperAdmin, asyncRoute(async (req, res) => {
    const email = parse(registrationEmailSchema, req.params.email);
    const kind = req.params.kind;
    if (!registrationDocumentKinds.has(kind)) throw new SwiftPayError(404, "Document not found");
    const document = await userStore.getRegistrationDocument(email, kind);
    await userStore.recordAudit(req.principal.sub, "registration.document.downloaded", email, { kind });
    res.set({
      "Cache-Control": "no-store, private",
      "X-Content-Type-Options": "nosniff",
      "Content-Disposition": `attachment; filename="${kind}.${document.contentType === "application/pdf" ? "pdf" : document.contentType === "image/png" ? "png" : "jpg"}"`
    });
    res.type(document.contentType).send(document.data);
  }));

  app.patch("/v1/admin/registrations/:email", requireSuperAdmin, asyncRoute(async (req, res) => {
    const email = parse(registrationEmailSchema, req.params.email);
    const input = parse(registrationReviewSchema, req.body);
    res.json(await userStore.reviewRegistration(email, input.status, input.note, req.principal.sub));
  }));

  app.delete("/v1/admin/registrations/:email", requireSuperAdmin, asyncRoute(async (req, res) => {
    const email = parse(registrationEmailSchema, req.params.email);
    res.json(await userStore.deleteRegistration(email, req.principal.sub));
  }));

  app.get("/v1/onboarding/state", requireScope("swiftpay:payments:read"), asyncRoute(async (req, res) => {
    res.json(await userStore.getMerchantOnboardingState(req.principal.sub));
  }));

  app.post("/v1/onboarding/complete", requireScope("swiftpay:payments:write"), asyncRoute(async (req, res) => {
    const completed = await userStore.completeMerchantOnboarding(req.principal.sub);
    if (!completed) throw new SwiftPayError(409, "This merchant account has no pending onboarding");
    res.json({ completed: true });
  }));

  app.get("/v1/wallet/manual-deposits", requireScope("swiftpay:payments:read"), asyncRoute(async (req, res) => {
    const limit = z.coerce.number().int().min(1).max(200).catch(100).parse(req.query.limit);
    const deposits = await userStore.listManualBankDeposits(limit);
    res.json(deposits.map((deposit) => ({
      id: deposit.id,
      amount: Number(deposit.amount),
      bankName: deposit.bank_name,
      referenceNo: deposit.reference_no,
      notes: deposit.notes,
      createdAt: deposit.created_at
    })));
  }));

  app.get("/v1/wallet/payment-transactions", requireScope("swiftpay:payments:read"), asyncRoute(async (req, res) => {
    if (!userStore?.listPaymentWebhookTransactions) {
      throw new SwiftPayError(503, "Payment transaction history is unavailable");
    }
    const limit = z.coerce.number().int().min(1).max(200).catch(100).parse(req.query.limit);
    const transactions = await userStore.listPaymentWebhookTransactions(limit);
    res.json(transactions.map((transaction) => ({
      id: transaction.id,
      referenceNo: transaction.reference_no,
      amount: Number(transaction.amount),
      currency: transaction.currency,
      status: transaction.status,
      createdAt: transaction.received_at
    })));
  }));

  app.get("/v1/admin/users", requireSuperAdmin, asyncRoute(async (_req, res) => {
    res.json(await userStore.listUsers());
  }));

  app.post("/v1/admin/users", requireSuperAdmin, asyncRoute(async (req, res) => {
    const input = parse(z.object({ email: merchantEmailSchema }).strict(), req.body);
    const user = await userStore.createMerchant(input.email, req.principal.sub);
    res.status(201).json(user);
  }));

  app.patch("/v1/admin/users/:email", requireSuperAdmin, asyncRoute(async (req, res) => {
    const email = merchantEmailSchema.parse(req.params.email);
    if (email === req.principal.sub.toLowerCase()) {
      throw new SwiftPayError(400, "You cannot change your own super-admin account");
    }
    const input = parse(merchantStatusSchema, req.body);
    const user = await userStore.setMerchantStatus(email, input.status, req.principal.sub);
    res.json(user);
  }));

  app.get("/v1/admin/audit", requireSuperAdmin, asyncRoute(async (req, res) => {
    const limit = z.coerce.number().int().min(1).max(200).catch(100).parse(req.query.limit);
    res.json(await userStore.listAudit(limit));
  }));

  app.get("/v1/admin/balance-adjustments", requireSuperAdmin, asyncRoute(async (req, res) => {
    const limit = z.coerce.number().int().min(1).max(200).catch(100).parse(req.query.limit);
    const [total, adjustments] = await Promise.all([
      userStore.adjustmentTotal(),
      userStore.listAdjustments(limit)
    ]);
    res.json({ total, adjustments, affectsProviderFunds: false });
  }));

  app.post("/v1/admin/balance-adjustments", requireSuperAdmin, asyncRoute(async (req, res) => {
    const input = parse(adjustmentSchema, req.body);
    const adjustment = await userStore.createAdjustment(input.amount, input.reason, req.principal.sub);
    res.status(201).json({ ...adjustment, affectsProviderFunds: false });
  }));

  app.post("/v1/admin/manual-bank-deposits", requireSuperAdmin, asyncRoute(async (req, res) => {
    const input = parse(manualBankDepositSchema, req.body);
    const deposit = await userStore.createManualBankDeposit({
      id: randomUUID(),
      ...input
    }, req.principal.sub);
    res.status(201).json({
      id: deposit.id,
      amount: Number(deposit.amount),
      bankName: deposit.bank_name,
      referenceNo: deposit.reference_no,
      notes: deposit.notes,
      createdAt: deposit.created_at,
      affectsProviderFunds: false
    });
  }));

  app.post("/v1/payments/orders", requireScope("swiftpay:payments:write"), asyncRoute(async (req, res) => {
    const input = parse(orderSchema, req.body);
    const payload = buildOrderPayload(input, config);
    const result = await requestProvider("orders", { method: "POST", body: payload });
    const redirectUrl = result.data?.customerRedirectUrl;
    if (typeof redirectUrl !== "string" || new URL(redirectUrl).protocol !== "https:") {
      throw new SwiftPayError(502, "SwiftPay did not return a valid hosted checkout URL");
    }
    res.status(result.status).json({
      paymentId: payload.x_payment_id,
      referenceNo: input.referenceNo,
      status: "PENDING",
      redirectUrl
    });
  }));

  app.get("/v1/payments/:paymentToken/status", requireScope("swiftpay:payments:read"), asyncRoute(async (req, res) => {
    const token = z.string().min(1).max(128).parse(req.params.paymentToken);
    const webhookStatus = await userStore?.getPaymentWebhookStatus?.(token);
    if (webhookStatus) {
      return res.json({ paymentId: token, status: webhookStatus.status, source: "webhook" });
    }
    const result = await requestProvider("payments/status", { paymentToken: token });
    res.status(result.status).json({ paymentId: token, status: result.data });
  }));

  app.post("/v1/payments/qrph/quote", requireScope("swiftpay:payments:write"), asyncRoute(async (req, res) => {
    const request = parse(fxPhpQuoteRequestSchema, req.body);
    const currency = "amountKrw" in request ? "KRW" : request.currency;
    const amount = "amountKrw" in request ? request.amountKrw : request.amount;
    const rate = await getLatestPhpRate(currency, config.currencyApiKey, fxFetchImpl);
    const quote = createFxPhpQuote({
      amount,
      currency,
      phpPerUnit: rate.phpPerUnit,
      rateUpdatedAt: rate.updatedAt,
      subject: req.principal.sub,
      signingKey: config.sessionSigningKey
    });
    res.json({
      quoteToken: quote.quoteToken,
      customerAmount: quote.customerAmount,
      customerCurrency: quote.customerCurrency,
      settlementAmountPhp: quote.amountPhp,
      ratePhpPerUnit: quote.phpPerUnit,
      rateUpdatedAt: quote.rateUpdatedAt,
      expiresAt: new Date(quote.expiresAt).toISOString(),
      settlementCurrency: "PHP",
      ...(currency === "KRW" ? {
        customerAmountKrw: quote.customerAmount,
        ratePhpPerKrw: quote.phpPerUnit
      } : {})
    });
  }));

  app.post("/v1/payments/qrph", requireScope("swiftpay:payments:write"), asyncRoute(async (req, res) => {
    if (["KRW", "USD", "EUR"].includes(req.body?.currency) && !req.body?.quoteToken) {
      throw new FxQuoteError(400, "Foreign-currency QR payments require a locked PHP quote; request a new quote first");
    }
    const request = parse(qrPaymentSchema, req.body);
    const quote = "quoteToken" in request
      ? verifyFxPhpQuote(request.quoteToken, req.principal.sub, config.sessionSigningKey)
      : null;
    const input = quote
      ? {
          amount: quote.amountPhp,
          referenceNo: `${quote.customerCurrency}-${quote.quoteId}`,
          currency: "PHP",
          rejectDuplicatedPayments: request.rejectDuplicatedPayments
        }
      : request;
    const bootstrap = await requestProvider("bootstrap/qrph?type=P2M", {
      method: "POST",
      body: buildQrPayload(input, config)
    });
    const providerPayment = z.object({
      paymentId: z.string().min(1),
      paymentStatus: z.string().min(1),
      referenceNo: z.string().min(1),
      amount: z.union([z.number(), z.string()]),
      currency: z.literal("PHP").optional(),
      qrCode: z.string().min(1),
      bankAccountNumber: z.string().nullable().optional(),
      instapayBankCode: z.string().nullable().optional()
    }).safeParse(bootstrap.data);
    if (!providerPayment.success || !Number.isFinite(Number(providerPayment.data.amount))) {
      throw new SwiftPayError(502, "SwiftPay returned an incomplete QR Ph payment");
    }

    const payment = providerPayment.data;
    if (payment.currency && payment.currency !== input.currency) {
      throw new SwiftPayError(502, "SwiftPay returned a QR Ph payment in a different currency");
    }
    if (quote && Math.round(Number(payment.amount) * 100) !== Math.round(quote.amountPhp * 100)) {
      throw new SwiftPayError(502, "SwiftPay returned a PHP QR amount that does not match the locked quote");
    }
    if (userStore?.recordWebhookEvent) {
      await userStore.recordWebhookEvent({
        eventKey: `payment:${payment.paymentId}`,
        eventType: "payment",
        paymentId: payment.paymentId,
        referenceNo: payment.referenceNo,
        status: payment.paymentStatus,
        details: {
          amount: Number(payment.amount),
          currency: payment.currency || input.currency,
          ...(quote ? {
            customerAmount: quote.customerAmount,
            customerCurrency: quote.customerCurrency,
            ratePhpPerUnit: quote.phpPerUnit,
            rateUpdatedAt: quote.rateUpdatedAt
          } : {})
        }
      });
    }
    let qrImageBase64;
    try {
      const image = await requestProvider("payments/qrph/image?type=P2M", {
        paymentToken: payment.paymentId,
        responseType: "bytes"
      });
      qrImageBase64 = image.data.toString("base64");
    } catch {
      qrImageBase64 = null;
    }

    res.status(201).json({
      paymentId: payment.paymentId,
      referenceNo: payment.referenceNo,
      status: payment.paymentStatus,
      amount: Number(payment.amount),
      currency: payment.currency || input.currency,
      qrCode: payment.qrCode,
      qrImageBase64: qrImageBase64,
      bankAccountNumber: payment.bankAccountNumber ?? null,
      instapayBankCode: payment.instapayBankCode ?? null,
      ...(quote ? {
        customerAmount: quote.customerAmount,
        customerCurrency: quote.customerCurrency,
        ratePhpPerUnit: quote.phpPerUnit,
        rateUpdatedAt: quote.rateUpdatedAt,
        settlementCurrency: "PHP",
        ...(quote.customerCurrency === "KRW" ? {
          customerAmountKrw: quote.customerAmount,
          ratePhpPerKrw: quote.phpPerUnit
        } : {})
      } : {})
    });
  }));

  app.get("/v1/wallet/balance", requireScope("swiftpay:disbursements:read"), asyncRoute(async (_req, res) => {
    let result;
    try {
      result = await requestProvider("disbursements/account", { auth: "basic" });
    } catch (error) {
      if (error instanceof SwiftPayError && error.status === 404) {
        return res.status(503).json({
          error: "SwiftPay has no disbursement account configured for this merchant"
        });
      }
      throw error;
    }
    const providerBalance = result.data?.balance;
    const normalizedBalance = typeof providerBalance === "string" && providerBalance.trim() !== ""
      ? Number(providerBalance)
      : providerBalance;
    const balance = z.number().finite().safeParse(normalizedBalance);
    const accountNumber = z.string().trim().min(1).safeParse(result.data?.number);
    if (!balance.success || !accountNumber.success || !accountNumber.data) {
      throw new SwiftPayError(502, "SwiftPay returned an invalid wallet account response");
    }
    const adjustmentTotal = userStore ? Number(await userStore.adjustmentTotal()) : 0;
    res.status(result.status).json({
      balance: balance.data,
      adjustedBalance: balance.data + adjustmentTotal,
      accountNumber: accountNumber.data
    });
  }));

  app.get("/v1/wallet/krw-receipts", requireScope("swiftpay:payments:read"), asyncRoute(async (_req, res) => {
    if (!userStore?.getKrwQrPaymentBalance) {
      return res.status(503).json({ error: "KRW payment receipt storage is unavailable" });
    }
    const ledger = await userStore.getKrwQrPaymentBalance();
    const balance = Number(ledger.balance);
    if (!Number.isFinite(balance) || balance < 0) {
      throw new SwiftPayError(502, "Stored KRW payment receipt balance is invalid");
    }
    res.json({ currency: "KRW", balance, paymentCount: ledger.paymentCount });
  }));

  app.post("/v1/payment-links", requireScope("swiftpay:payments:write"), asyncRoute(async (req, res) => {
    if (!config.publicBaseUrl) {
      throw new SwiftPayError(503, "Public checkout links are not configured. Set PUBLIC_BASE_URL to this backend's HTTPS address.");
    }
    const input = parse(paymentLinkSchema, req.body);
    const payload = { ...input, referenceNo: input.referenceNo || newReference() };
    const result = await requestProvider("payments/links", { method: "POST", auth: "basic", body: payload });
    let paymentUrl;
    try {
      paymentUrl = new URL(result.data?.paymentUrl);
    } catch {
      throw new SwiftPayError(502, "SwiftPay did not return a valid payment-link URL");
    }
    const code = paymentLinkCodeSchema.safeParse(result.data?.code);
    if (!code.success || paymentUrl.protocol !== "https:" || paymentUrl.username || paymentUrl.password) {
      throw new SwiftPayError(502, "SwiftPay did not return a valid payment link");
    }
    const publicPaymentUrl = new URL(`checkout/${encodeURIComponent(code.data)}`, config.publicBaseUrl);
    res.status(result.status).json({
      ...result.data,
      paymentUrl: publicPaymentUrl.href
    });
  }));

  app.get("/v1/payment-links/:code", requireScope("swiftpay:payments:read"), asyncRoute(async (req, res) => {
    const code = paymentLinkCodeSchema.parse(req.params.code);
    const result = await requestProvider(`payments/links/${encodeURIComponent(code)}`, { auth: "basic" });
    res.status(result.status).json(result.data);
  }));

  app.delete("/v1/payment-links/:code", requireScope("swiftpay:payments:write"), asyncRoute(async (req, res) => {
    const code = paymentLinkCodeSchema.parse(req.params.code);
    const result = await requestProvider(`payments/links/${encodeURIComponent(code)}/invalidate`, { method: "DELETE", auth: "basic" });
    res.status(result.status).json(result.data);
  }));

  app.post("/v1/disbursements", requireScope("swiftpay:disbursements:write"), asyncRoute(async (req, res) => {
    const input = parse(disbursementSchema, req.body);
    if (input.channel === "PESONET" && input.type && input.type !== "P2P") {
      throw new SwiftPayError(400, "PESONET only supports P2P transfers");
    }
    if (input.channel === "INSTAPAY" && input.amount > 50_000) {
      throw new SwiftPayError(400, "InstaPay transfers are limited to PHP 50,000 per transaction");
    }
    const payload = {
      merchantReferenceNo: input.referenceNo,
      channel: input.channel,
      ...(input.type ? { type: input.type } : {}),
      institutionCode: input.institutionCode,
      creditInformation: { amount: input.amount, remarks: input.remarks || "Bank transfer" },
      recipientInformation: input.recipientInformation
    };
    const auditId = userStore
      ? await userStore.createTransferIntent(req.principal.sub, input.referenceNo, {
        referenceNo: input.referenceNo,
        amount: input.amount,
        channel: input.channel,
        institutionCode: input.institutionCode
      })
      : null;
    let result;
    try {
      result = await requestProvider("disbursements/send", { method: "POST", auth: "basic", body: payload });
    } catch (error) {
      if (auditId !== null) {
        await userStore.finishTransferIntent(
          auditId,
          error instanceof SwiftPayError && error.status < 500 ? "rejected" : "unknown",
          input.referenceNo,
          { error: error instanceof SwiftPayError && error.status < 500 ? error.message : "Provider outcome requires reconciliation" }
        );
      }
      throw error;
    }
    if (typeof result.data?.id !== "string" || !z.string().uuid().safeParse(result.data.id).success) {
      if (auditId !== null) {
        await userStore.finishTransferIntent(auditId, "unknown", input.referenceNo, {
          error: "Provider accepted a response with an invalid transfer ID; reconcile before retrying"
        });
      }
      throw new SwiftPayError(502, "SwiftPay did not return a valid disbursement ID");
    }
    if (auditId !== null) {
      try {
        await userStore.finishTransferIntent(auditId, "accepted", result.data.id, {
          referenceNo: input.referenceNo,
          providerStatus: "PENDING"
        });
      } catch (error) {
        console.error("Transfer was accepted but its audit outcome could not be saved");
        return res.status(202).json({
          id: result.data.id,
          referenceNo: input.referenceNo,
          status: "PENDING",
          auditStatus: "reconciliation_required"
        });
      }
    }
    res.status(202).json({ id: result.data?.id, referenceNo: input.referenceNo, status: "PENDING" });
  }));

  app.get("/v1/disbursements", requireScope("swiftpay:disbursements:read"), asyncRoute(async (_req, res) => {
    const result = await requestProvider("disbursements?pageNo=0&pageSize=100", { auth: "basic" });
    const records = z.array(z.record(z.string(), z.unknown())).safeParse(result.data?.result);
    if (!records.success) {
      throw new SwiftPayError(502, "SwiftPay returned an invalid disbursement page");
    }
    res.status(result.status).json(records.data);
  }));

  app.get("/v1/disbursements/:id", requireScope("swiftpay:disbursements:read"), asyncRoute(async (req, res) => {
    const id = z.string().uuid().parse(req.params.id);
    const result = await requestProvider(`disbursements/${encodeURIComponent(id)}`, { auth: "basic" });
    res.status(result.status).json(result.data);
  }));

  app.get("/v1/disbursement-institutions", requireScope("swiftpay:disbursements:read"), asyncRoute(async (_req, res) => {
    const result = await requestProvider("disbursements/institutions");
    res.status(result.status).json(result.data);
  }));

  app.get("/v1/payment-institutions", requireScope("swiftpay:payments:read"), asyncRoute(async (_req, res) => {
    const result = await requestProvider("institutions");
    const institutions = z.array(paymentInstitutionSchema).safeParse(result.data);
    if (!institutions.success) {
      throw new SwiftPayError(502, "SwiftPay returned an invalid payment institution catalogue");
    }
    res.status(result.status).json(
      institutions.data
        .filter((institution) => institution.enabled !== false)
        .map(({ code, name, logoUrl }) => ({ code, name, logoUrl: trustedInstitutionLogoUrl(logoUrl) }))
    );
  }));

  app.use((_req, res) => res.status(404).json({ error: "not_found" }));
  app.use((error, req, res, _next) => {
    const status = error instanceof SwiftPayError || error instanceof TransakError
      ? error.status
      : Number.isInteger(error.status) && error.status >= 400 && error.status < 500
        ? error.status
        : 500;
    const message = status >= 500 &&
      !(error instanceof TransakError) &&
      !(error instanceof FxQuoteError)
      ? "Internal server error"
      : error.message;
    if (status >= 500 || (error instanceof SwiftPayError && error.providerStatus)) {
      const secrets = [
        config.swiftPayAccessKey,
        config.swiftPaySecretKey,
        config.sessionSigningKey,
        config.resendApiKey,
        config.currencyApiKey,
        config.fcmServiceAccountJson,
        config.transak?.apiKey,
        config.transak?.apiSecret
      ].filter((secret) => typeof secret === "string" && secret.length >= 8);
      let detail = error instanceof Error ? error.message : String(error);
      for (const secret of secrets) detail = detail.replaceAll(secret, "[REDACTED]");
      detail = detail
        .replace(/\bBearer\s+\S+/gi, "Bearer [REDACTED]")
        .replace(/\bBasic\s+\S+/gi, "Basic [REDACTED]")
        .slice(0, 500);
      console.error("Backend request failed", JSON.stringify({
        method: req.method,
        path: req.path,
        status,
        errorName: error instanceof Error ? error.name : "UnknownError",
        ...(error instanceof SwiftPayError && error.providerStatus
          ? { providerStatus: error.providerStatus }
          : {}),
        ...(error instanceof SwiftPayError && error.providerContentType
          ? { providerContentType: error.providerContentType }
          : {}),
        ...(error instanceof SwiftPayError && error.errorCode ? { errorCode: error.errorCode } : {}),
        ...(error instanceof SwiftPayError && error.supportReference ? { supportReference: error.supportReference } : {}),
        detail
      }));
    }
    res.status(status).json({
      error: message,
      ...(error instanceof SwiftPayError && error.errorCode ? { errorCode: error.errorCode } : {}),
      ...(error instanceof SwiftPayError && error.supportReference ? { supportReference: error.supportReference } : {})
    });
  });
  return app;
}