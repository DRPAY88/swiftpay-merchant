import express from "express";
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
import { createKrwPhpQuote, FxQuoteError, getLatestKrwPhpRate, verifyKrwPhpQuote } from "./fx-quotes.js";
import { TransakError } from "./transak.js";

const amountSchema = z.number().finite().min(0.01).max(1_000_000);
const referenceSchema = z.string().trim().min(1).max(50);
const currencySchema = z.literal("PHP");
const qrCurrencySchema = z.enum(["PHP", "KRW"]);

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

const paymentLinkSchema = z.object({
  amount: amountSchema,
  currency: currencySchema.default("PHP"),
  title: z.string().max(100).optional(),
  description: z.string().max(500).optional(),
  referenceNo: z.string().trim().max(50).optional(),
  validUntil: z.string().datetime({ offset: true }).optional(),
  customerName: z.string().max(150).optional()
}).strict();

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
  remarks: z.string().max(80).optional()
});
const merchantEmailSchema = z.string().trim().email().max(255).transform((email) => email.toLowerCase());
const merchantStatusSchema = z.object({
  status: z.enum(["active", "suspended", "removed"])
}).strict();
const adjustmentSchema = z.object({
  amount: z.number().finite().min(-1_000_000).max(1_000_000).refine((amount) => amount !== 0),
  reason: z.string().trim().min(3).max(500)
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
      if (!user || user.status !== "active") return res.status(401).json({ error: "unauthorized" });
      req.principal.role = user.role;
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
  cryptoWidgetService,
  userStore
}) {
  if (!verifyAccessToken) throw new Error("A JWT verifier is required");
  if (!requestOtp || !verifyOtp) throw new Error("OTP auth handlers are required");
  const app = express();
  const requestProvider = (path, options) => callSwiftPay(config, fetchImpl, path, options);

  app.disable("x-powered-by");
  app.set("trust proxy", 1);
  app.use(helmet());
  app.use("/admin", express.static(fileURLToPath(new URL("../public/", import.meta.url)), {
    index: "index.html",
    maxAge: "1h"
  }));
  app.use(express.json({ limit: "32kb", strict: true }));
  app.use(rateLimit({
    windowMs: 60_000,
    limit: 120,
    standardHeaders: "draft-8",
    legacyHeaders: false,
    skip: (req) => req.path === "/v1/webhooks/swiftpay"
  }));
  const webhookLimiter = rateLimit({ windowMs: 60_000, limit: 600, standardHeaders: "draft-8", legacyHeaders: false });
  app.get("/healthz", (_req, res) => res.json({ status: "ok" }));
  const authLimiter = rateLimit({ windowMs: 60_000, limit: 5, standardHeaders: "draft-8", legacyHeaders: false });
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
      x_payment_id: z.string().uuid(),
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
    const input = parse(krwPhpQuoteRequestSchema, req.body);
    const rate = await getLatestKrwPhpRate(config.currencyApiKey, fxFetchImpl);
    const quote = createKrwPhpQuote({
      amountKrw: input.amountKrw,
      phpPerKrw: rate.phpPerKrw,
      rateUpdatedAt: rate.updatedAt,
      subject: req.principal.sub,
      signingKey: config.sessionSigningKey
    });
    res.json({
      quoteToken: quote.quoteToken,
      customerAmountKrw: quote.amountKrw,
      settlementAmountPhp: quote.amountPhp,
      ratePhpPerKrw: quote.phpPerKrw,
      rateUpdatedAt: quote.rateUpdatedAt,
      expiresAt: new Date(quote.expiresAt).toISOString(),
      settlementCurrency: "PHP"
    });
  }));

  app.post("/v1/payments/qrph", requireScope("swiftpay:payments:write"), asyncRoute(async (req, res) => {
    if (req.body?.currency === "KRW" && !req.body?.quoteToken) {
      throw new FxQuoteError(400, "KRW-priced QR payments require a locked PHP quote; request a new quote first");
    }
    const request = parse(qrPaymentSchema, req.body);
    const quote = "quoteToken" in request
      ? verifyKrwPhpQuote(request.quoteToken, req.principal.sub, config.sessionSigningKey)
      : null;
    const input = quote
      ? {
          amount: quote.amountPhp,
          referenceNo: `KRW-${quote.quoteId}`,
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
            customerAmountKrw: quote.amountKrw,
            ratePhpPerKrw: quote.phpPerKrw,
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
        customerAmountKrw: quote.amountKrw,
        ratePhpPerKrw: quote.phpPerKrw,
        rateUpdatedAt: quote.rateUpdatedAt,
        settlementCurrency: "PHP"
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
    const input = parse(paymentLinkSchema, req.body);
    const payload = { ...input, referenceNo: input.referenceNo || newReference() };
    const result = await requestProvider("payments/links", { method: "POST", auth: "basic", body: payload });
    let paymentUrl;
    try {
      paymentUrl = new URL(result.data?.paymentUrl);
    } catch {
      throw new SwiftPayError(502, "SwiftPay did not return a valid payment-link URL");
    }
    if (!result.data?.code || paymentUrl.protocol !== "https:") {
      throw new SwiftPayError(502, "SwiftPay did not return a valid payment link");
    }
    res.status(result.status).json(result.data);
  }));

  app.get("/v1/payment-links/:code", requireScope("swiftpay:payments:read"), asyncRoute(async (req, res) => {
    const code = z.string().trim().min(1).max(128).parse(req.params.code);
    const result = await requestProvider(`payments/links/${encodeURIComponent(code)}`, { auth: "basic" });
    res.status(result.status).json(result.data);
  }));

  app.delete("/v1/payment-links/:code", requireScope("swiftpay:payments:write"), asyncRoute(async (req, res) => {
    const code = z.string().trim().min(1).max(128).parse(req.params.code);
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
      creditInformation: { amount: input.amount, ...(input.remarks ? { remarks: input.remarks } : {}) },
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

  app.use((_req, res) => res.status(404).json({ error: "not_found" }));
  app.use((error, _req, res, _next) => {
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
    if (status >= 500) console.error("SwiftPay backend request failed");
    res.status(status).json({
      error: message,
      ...(error instanceof SwiftPayError && error.errorCode ? { errorCode: error.errorCode } : {}),
      ...(error instanceof SwiftPayError && error.supportReference ? { supportReference: error.supportReference } : {})
    });
  });
  return app;
}