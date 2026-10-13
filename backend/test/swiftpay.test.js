import assert from "node:assert/strict";
import { createHmac } from "node:crypto";
import { after, before, test } from "node:test";
import { createApp } from "../src/app.js";
import { canonicalOrderMessage, buildOrderPayload, buildQrPayload, verifySwiftPaySignature } from "../src/swiftpay.js";
import { TransakError } from "../src/transak.js";

const config = {
  swiftPayBaseUrl: new URL("https://provider.example/api/"),
  publicBaseUrl: new URL("https://merchant.example/"),
  swiftPayAccessKey: "A".repeat(32),
  swiftPaySecretKey: "S".repeat(32),
  sessionSigningKey: "session-signing-key-for-merchant-tests-123",
  currencyApiKey: "currencyapi-test-key"
};
const tokenPrincipal = {
  sub: "merchant-user-1",
  scope: "swiftpay:payments:write swiftpay:payments:read swiftpay:disbursements:write swiftpay:disbursements:read"
};
let server;
let baseUrl;
let requests;
let fxRequests;
let failOtpRequest = false;
let accountResponse;
let accountResponseStatus;
let accountResponseText;
let accountResponseContentType = "application/json";
let qrBootstrapStatus;
let institutionFailureStatus = 0;
let orderFailureStatus = 0;
let testClock = Date.now();
let disbursementPage;
let disbursementFailure;
let cryptoWidgetRequest;
let cryptoWidgetFailure = false;
const storedPaymentEvents = new Map();

before(async () => {
  requests = [];
  fxRequests = [];
  accountResponse = { number: "001002003004", balance: 12500.75 };
  accountResponseStatus = 200;
  qrBootstrapStatus = 200;
  disbursementPage = { pageNo: 0, result: [], totalCount: 0, resultCount: 0 };
  storedPaymentEvents.clear();
  let app;
  app = createApp({
    config,
    fxFetchImpl: async (url, options) => {
      fxRequests.push({ url: url.toString(), options });
      const baseCurrency = new URL(url).searchParams.get("base_currency");
      return new Response(JSON.stringify({
        meta: { base_currency: baseCurrency, last_updated_at: new Date().toISOString() },
        data: { PHP: { code: "PHP", value: ({ KRW: 0.0425, USD: 57, EUR: 62 })[baseCurrency] } }
      }), {
        status: 200,
        headers: { "content-type": "application/json" }
      });
    },
    requestOtp: async () => {
      if (failOtpRequest) throw new Error("OTP email provider rejected request (HTTP 403)");
    },
    verifyOtp: async () => null,
    userStore: {
      getUser: async () => ({ status: "active", role: "merchant" }),
      recordWebhookEvent: async (event) => storedPaymentEvents.set(event.eventKey, event),
      adjustmentTotal: async () => 0,
      createTransferIntent: async () => null,
      finishTransferIntent: async () => {}
    },
    verifyAccessToken: async (token) => {
      if (!new Set(["valid-test-token", "read-only-test-token"]).has(token)) throw new Error("invalid token");
      if (token === "read-only-test-token") return { sub: "reader-1", scope: "swiftpay:payments:read" };
      return tokenPrincipal;
    },
    cryptoWidgetService: {
      createWidgetUrl: async (request) => {
        if (cryptoWidgetFailure) throw new TransakError(503, "USDT checkout is not configured for this service");
        cryptoWidgetRequest = request;
        return {
          widgetUrl: "https://global-stg.transak.com/?sessionId=single-use-session",
          partnerOrderId: "partner-order-1"
        };
      }
    },
    clock: () => testClock,
    fetchImpl: async (url, options) => {
      requests.push({ url: url.toString(), options });
      if (url.pathname.endsWith("/payments/qrph/image")) {
        return new Response(Buffer.from([137, 80, 78, 71]), {
          status: 200,
          headers: { "content-type": "image/png" }
        });
      }
      if (url.pathname.endsWith("/bootstrap/qrph") && qrBootstrapStatus !== 200) {
        return new Response(JSON.stringify({ code: "NOT_FOUND" }), {
          status: qrBootstrapStatus,
          headers: { "content-type": "application/json" }
        });
      }
      if (url.pathname === "/api/institutions" && institutionFailureStatus !== 0) {
        return new Response(JSON.stringify({ code: "UPSTREAM_UNAVAILABLE" }), {
          status: institutionFailureStatus,
          headers: { "content-type": "application/json" }
        });
      }
      const paymentLinkData = {
        code: "PL-1",
        merchantId: 42,
        paymentUrl: "https://pay.example/link/PL-1",
        amount: 125.5,
        currency: "PHP",
        title: "Invoice <script>alert(1)</script>",
        description: "Invoice & order"
      };
      const paymentInstitutions = [
        {
          code: "BPI",
          name: "Bank of the Philippine Islands",
          logoUrl: "https://assets.swiftpay.ph/institutions/bpi.png",
          enabled: true,
          currencies: ["PHP"]
        },
        {
          code: "DISABLED",
          name: "Disabled institution",
          logoUrl: "https://assets.swiftpay.ph/institutions/disabled.png",
          enabled: false,
          currencies: ["PHP"]
        },
        {
          code: "UNTRUSTED",
          name: "Untrusted logo host",
          logoUrl: "https://logos.example/bank.png",
          enabled: true,
          currencies: ["PHP"]
        },
        {
          code: "VBANK",
          name: "VBank",
          logoUrl: "https://swiftpay-qa-gateway-frontend.s3-ap-southeast-1.amazonaws.com/static/media/bank-icons/vbank.png",
          enabled: true,
          currencies: ["PHP"]
        },
        {
          code: "NO_LOGO",
          name: "No logo institution",
          logoUrl: null,
          enabled: true,
          currencies: ["PHP"]
        },
        {
          code: "ALIPAY",
          name: "Alipay",
          logoUrl: "https://assets.swiftpay.ph/institutions/alipay.png",
          enabled: true,
          live: true,
          currencies: ["PHP", "USD", "EUR"],
          minAmount: 1,
          maxAmount: 100000
        },
        {
          code: "NAVERPAY",
          name: "Naver Pay",
          logoUrl: "https://assets.swiftpay.ph/institutions/naver-pay.png",
          enabled: true,
          live: true,
          currencies: ["PHP", "USD", "EUR"]
        },
        {
          code: "KAKAOPAY",
          name: "Kakao Pay",
          logoUrl: "https://assets.swiftpay.ph/institutions/kakao-pay.png",
          enabled: true,
          live: true,
          currencies: ["PHP", "USD", "EUR"]
        },
        {
          code: "TOSSBANK",
          name: "Toss Bank",
          logoUrl: "https://assets.swiftpay.ph/institutions/toss-bank.png",
          enabled: true,
          live: true,
          currencies: ["PHP", "USD", "EUR"]
        }
      ];
      const response = url.pathname.endsWith("/payments/links")
        ? { status: 200, body: paymentLinkData }
        : url.pathname.endsWith("/payments/links/PL-USD")
          ? {
              status: 200,
              body: {
                ...paymentLinkData,
                code: "PL-USD",
                amount: 20,
                currency: "USD",
                paymentUrl: "https://pay.example/link/PL-USD"
              }
            }
        : url.pathname.endsWith("/payments/links/PL-1")
          ? { status: 200, body: paymentLinkData }
          : url.pathname === "/api/institutions"
            ? { status: 200, body: paymentInstitutions }
          : url.pathname.endsWith("/bootstrap/qrph")
            ? {
                status: 200,
                body: {
                  paymentId: "5008f066-d68a-4ef2-b794-8596bac268f6",
                  paymentStatus: "PENDING",
                  referenceNo: "QR-1",
                  amount: JSON.parse(options.body).x_amount,
                  currency: JSON.parse(options.body).x_currency,
                  bankAccountNumber: "120000234109",
                  instapayBankCode: "CUOBPHM2XXX",
                  qrCode: "000201010212"
                }
              }
            : url.pathname.endsWith("/payments/status")
              ? { status: 200, body: "EXECUTED" }
              : url.pathname.endsWith("/disbursements/account")
                ? { status: accountResponseStatus, body: accountResponse }
                : url.pathname.endsWith("/disbursements/send")
                  ? disbursementFailure || { status: 200, body: { id: "9d51c8e2-3211-4c01-a9a1-19a9a8bd5c01" } }
                  : url.pathname.endsWith("/disbursements")
                    ? { status: 200, body: disbursementPage }
                    : url.pathname.endsWith("/orders") && orderFailureStatus !== 0
                      ? { status: orderFailureStatus, body: { code: "NOT_FOUND" } }
                      : { status: 202, body: { customerRedirectUrl: "https://pay.live.swiftpay.ph/checkout" } };
      const accountResponseRequested = url.pathname.endsWith("/disbursements/account");
      return new Response(
        accountResponseRequested && accountResponseText !== undefined
          ? accountResponseText
          : JSON.stringify(response.body),
        {
        status: response.status,
        headers: { "content-type": accountResponseRequested ? accountResponseContentType : "application/json" }
        }
      );
    }
  });
  assert.equal(app.get("trust proxy"), 1);
  server = app.listen(0, "127.0.0.1");
  await new Promise((resolve) => server.once("listening", resolve));
  baseUrl = `http://127.0.0.1:${server.address().port}`;
});

after(async () => {
  await new Promise((resolve, reject) => server.close((error) => error ? reject(error) : resolve()));
});

test("crypto checkout requires a merchant session and creates pinned buy and sell widget sessions", async () => {
  const unauthorized = await fetch(`${baseUrl}/v1/crypto/widget-url`, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ mode: "BUY", amount: 2000, walletAddress: "TLa2f6VPqDgRE67v1736s7bJ8Ray5wYjU7" })
  });
  assert.equal(unauthorized.status, 401);

  const headers = {
    authorization: "Bearer valid-test-token",
    "content-type": "application/json",
    "x-forwarded-for": "203.0.113.10"
  };
  const buy = await fetch(`${baseUrl}/v1/crypto/widget-url`, {
    method: "POST",
    headers,
    body: JSON.stringify({ mode: "BUY", amount: 2000, walletAddress: "TLa2f6VPqDgRE67v1736s7bJ8Ray5wYjU7" })
  });
  assert.equal(buy.status, 200);
  assert.deepEqual(await buy.json(), {
    widgetUrl: "https://global-stg.transak.com/?sessionId=single-use-session",
    partnerOrderId: "partner-order-1"
  });
  assert.equal(cryptoWidgetRequest.mode, "BUY");
  assert.equal(cryptoWidgetRequest.amount, 2000);
  assert.equal(cryptoWidgetRequest.walletAddress, "TLa2f6VPqDgRE67v1736s7bJ8Ray5wYjU7");
  assert.equal(cryptoWidgetRequest.userIp, "203.0.113.10");

  const sell = await fetch(`${baseUrl}/v1/crypto/widget-url`, {
    method: "POST",
    headers,
    body: JSON.stringify({ mode: "SELL", amount: 12.5 })
  });
  assert.equal(sell.status, 200);
  assert.equal(cryptoWidgetRequest.mode, "SELL");
  assert.equal(cryptoWidgetRequest.amount, 12.5);
  assert.equal(cryptoWidgetRequest.walletAddress, undefined);
});

test("crypto buy requires a TRON address and rejects unsupported fields", async () => {
  const response = await fetch(`${baseUrl}/v1/crypto/widget-url`, {
    method: "POST",
    headers: {
      authorization: "Bearer valid-test-token",
      "content-type": "application/json"
    },
    body: JSON.stringify({ mode: "BUY", amount: 2000, walletAddress: "not-a-tron-address", apiSecret: "never-accepted" })
  });
  assert.equal(response.status, 400);
});

test("crypto checkout reports when Transak credentials are not configured", async () => {
  cryptoWidgetFailure = true;
  try {
    const response = await fetch(`${baseUrl}/v1/crypto/widget-url`, {
      method: "POST",
      headers: {
        authorization: "Bearer valid-test-token",
        "content-type": "application/json"
      },
      body: JSON.stringify({ mode: "SELL", amount: 12 })
    });
    assert.equal(response.status, 503);
    assert.deepEqual(await response.json(), { error: "USDT checkout is not configured for this service" });
  } finally {
    cryptoWidgetFailure = false;
  }
});

test("failed OTP delivery returns a retryable error without exposing provider details", async () => {
  failOtpRequest = true;
  try {
    const response = await fetch(`${baseUrl}/v1/auth/request-otp`, {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({ email: "merchant@example.com" })
    });
    assert.equal(response.status, 503);
    assert.deepEqual(await response.json(), { error: "Sign-in email could not be sent; try again later" });
  } finally {
    failOtpRequest = false;
  }
});

test("order signature uses sorted x-fields and two-decimal amount", () => {
  const fields = {
    x_reference_no: "R-1",
    details: { note: "ignored" },
    x_amount: 100,
    x_access_key: "access"
  };
  const canonical = "x_access_keyaccessx_amount100.00x_reference_noR-1";
  assert.equal(canonicalOrderMessage(fields), canonical);

  const payload = buildOrderPayload({
    amount: 100,
    referenceNo: "R-1",
    currency: "PHP",
    details: {}
  }, config, "00000000-0000-4000-8000-000000000001");
  const expected = createHmac("sha256", config.swiftPaySecretKey).update(
    "x_access_key" + config.swiftPayAccessKey +
    "x_amount100.00" +
    "x_currencyPHP" +
    "x_payment_id00000000-0000-4000-8000-000000000001" +
    "x_reference_noR-1"
  ).digest("hex");
  assert.equal(payload.signature, expected);
  assert.equal(payload.generate_customer_redirect_url, true);
  assert.equal(payload.details, "{}");
});

test("QR Ph bootstrap signs amount and returns the provider PNG", async () => {
  const response = await fetch(`${baseUrl}/v1/payments/qrph`, {
    method: "POST",
    headers: {
      authorization: "Bearer valid-test-token",
      "content-type": "application/json"
    },
    body: JSON.stringify({ amount: 500.75, referenceNo: "QR-1" })
  });
  assert.equal(response.status, 201);
  const payment = await response.json();
  assert.equal(payment.paymentId, "5008f066-d68a-4ef2-b794-8596bac268f6");
  assert.equal(payment.amount, 500.75);
  assert.equal(payment.currency, "PHP");
  assert.equal(payment.qrCode, "000201010212");
  assert.equal(payment.bankAccountNumber, "120000234109");
  assert.equal(payment.instapayBankCode, "CUOBPHM2XXX");
  assert.equal(payment.qrImageBase64, Buffer.from([137, 80, 78, 71]).toString("base64"));

  const bootstrapRequest = requests.findLast(({ url }) => url.endsWith("/bootstrap/qrph?type=P2M"));
  const payload = JSON.parse(bootstrapRequest.options.body);
  assert.equal(payload.x_amount, 500.75);
  assert.equal(payload.x_currency, "PHP");
  assert.equal(payload.x_reference_no, "QR-1");
  assert.equal(payload.signature, buildQrPayload({
    amount: 500.75,
    referenceNo: "QR-1",
    rejectDuplicatedPayments: true
  }, config).signature);
  const imageRequest = requests.findLast(({ url }) => url.endsWith("/payments/qrph/image?type=P2M"));
  assert.equal(imageRequest.options.headers["X-Swiftpay-Payment-Token"], payment.paymentId);
  assert.equal(imageRequest.options.headers.Accept, "image/png");
});

test("KRW customer price is quoted and QR bootstrap charges the locked PHP amount", async () => {
  fxRequests = [];
  const quoteResponse = await fetch(`${baseUrl}/v1/payments/qrph/quote`, {
    method: "POST",
    headers: {
      authorization: "Bearer valid-test-token",
      "content-type": "application/json"
    },
    body: JSON.stringify({ amountKrw: 12000 })
  });
  assert.equal(quoteResponse.status, 200);
  const quote = await quoteResponse.json();
  assert.equal(quote.customerAmountKrw, 12000);
  assert.equal(quote.settlementAmountPhp, 510);
  assert.equal(quote.ratePhpPerKrw, 0.0425);
  assert.equal(quote.settlementCurrency, "PHP");
  assert.ok(quote.quoteToken);
  assert.ok(new Date(quote.expiresAt).getTime() - Date.now() <= 5 * 60_000);
  assert.equal(fxRequests.length, 1);
  const fxRequest = new URL(fxRequests[0].url);
  assert.equal(fxRequest.href, "https://api.currencyapi.com/v3/latest?base_currency=KRW&currencies=PHP");
  assert.equal(fxRequests[0].options.headers.apikey, "currencyapi-test-key");
  assert.equal(fxRequest.searchParams.has("apikey"), false);

  const response = await fetch(`${baseUrl}/v1/payments/qrph`, {
    method: "POST",
    headers: {
      authorization: "Bearer valid-test-token",
      "content-type": "application/json"
    },
    body: JSON.stringify({ quoteToken: quote.quoteToken })
  });
  assert.equal(response.status, 201);
  const payment = await response.json();
  assert.equal(payment.amount, 510);
  assert.equal(payment.currency, "PHP");
  assert.equal(payment.customerAmountKrw, 12000);
  assert.equal(payment.settlementCurrency, "PHP");

  const bootstrapRequest = requests.findLast(({ url }) => url.endsWith("/bootstrap/qrph?type=P2M"));
  const payload = JSON.parse(bootstrapRequest.options.body);
  assert.equal(payload.x_amount, 510);
  assert.equal(payload.x_currency, "PHP");
  assert.equal(verifySwiftPaySignature(payload, payload.signature, config.swiftPaySecretKey), true);
  assert.deepEqual(storedPaymentEvents.get(`payment:${payment.paymentId}`).details, {
    amount: 510,
    currency: "PHP",
    customerAmount: 12000,
    customerCurrency: "KRW",
    ratePhpPerUnit: 0.0425,
    rateUpdatedAt: quote.rateUpdatedAt
  });
});

test("USD and EUR QR quotes use the customer currency and lock PHP settlement", async () => {
  for (const [currency, amount, rate, expectedPhp] of [["USD", 20, 57, 1140], ["EUR", 10.5, 62, 651]]) {
    const response = await fetch(`${baseUrl}/v1/payments/qrph/quote`, {
      method: "POST",
      headers: {
        authorization: "Bearer valid-test-token",
        "content-type": "application/json"
      },
      body: JSON.stringify({ amount, currency })
    });
    assert.equal(response.status, 200);
    const quote = await response.json();
    assert.equal(quote.customerAmount, amount);
    assert.equal(quote.customerCurrency, currency);
    assert.equal(quote.ratePhpPerUnit, rate);
    assert.equal(quote.settlementAmountPhp, expectedPhp);
    assert.equal(quote.settlementCurrency, "PHP");

    const paymentResponse = await fetch(`${baseUrl}/v1/payments/qrph`, {
      method: "POST",
      headers: {
        authorization: "Bearer valid-test-token",
        "content-type": "application/json"
      },
      body: JSON.stringify({ quoteToken: quote.quoteToken })
    });
    assert.equal(paymentResponse.status, 201);
    const payment = await paymentResponse.json();
    assert.equal(payment.amount, expectedPhp);
    assert.equal(payment.currency, "PHP");
    assert.equal(payment.customerAmount, amount);
    assert.equal(payment.customerCurrency, currency);
    assert.equal(JSON.parse(requests.findLast(({ url }) => url.endsWith("/bootstrap/qrph?type=P2M")).options.body).x_amount, expectedPhp);
  }
});

test("QR Ph rejects direct KRW amounts instead of sending an unsupported currency", async () => {
  const previousBootstrapCount = requests.filter(({ url }) => url.endsWith("/bootstrap/qrph?type=P2M")).length;
  const response = await fetch(`${baseUrl}/v1/payments/qrph`, {
    method: "POST",
    headers: {
      authorization: "Bearer valid-test-token",
      "content-type": "application/json"
    },
    body: JSON.stringify({ amount: 12000, referenceNo: "QR-KRW-OLD", currency: "KRW" })
  });
  assert.equal(response.status, 400);
  assert.match((await response.json()).error, /locked PHP quote/);
  assert.equal(
    requests.filter(({ url }) => url.endsWith("/bootstrap/qrph?type=P2M")).length,
    previousBootstrapCount
  );
});

test("QR Ph signature verification rejects altered fields and malformed signatures", () => {
  const payload = buildQrPayload({ amount: 12.3, referenceNo: "QR-2" }, config);
  assert.equal(verifySwiftPaySignature(payload, payload.signature, config.swiftPaySecretKey), true);
  assert.equal(verifySwiftPaySignature({ ...payload, x_amount: 99 }, payload.signature, config.swiftPaySecretKey), false);
  assert.equal(verifySwiftPaySignature(payload, "not-a-signature", config.swiftPaySecretKey), false);
});

test("QR Ph collection does not fabricate a QR when bootstrap is unavailable", async () => {
  qrBootstrapStatus = 404;
  try {
    const response = await fetch(`${baseUrl}/v1/payments/qrph`, {
      method: "POST",
      headers: {
        authorization: "Bearer valid-test-token",
        "content-type": "application/json"
      },
      body: JSON.stringify({ amount: 25, referenceNo: "QR-FALLBACK-1" })
    });
    assert.equal(response.status, 404);
    assert.deepEqual(await response.json(), {
      error: "SwiftPay request failed (NOT_FOUND)",
      errorCode: "NOT_FOUND"
    });
    assert.equal(requests.some(({ url }) => url.endsWith("/orders")), false);
  } finally {
    qrBootstrapStatus = 200;
  }
});

test("payment order requires a verified scoped bearer token and signs upstream", async () => {
  const denied = await fetch(`${baseUrl}/v1/payments/orders`, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ amount: 15, referenceNo: "ORDER-1", details: {} })
  });
  assert.equal(denied.status, 401);

  const response = await fetch(`${baseUrl}/v1/payments/orders`, {
    method: "POST",
    headers: {
      authorization: "Bearer valid-test-token",
      "content-type": "application/json"
    },
    body: JSON.stringify({ amount: 15, referenceNo: "ORDER-1", details: {} })
  });
  assert.equal(response.status, 202);
  const result = await response.json();
  assert.equal(result.status, "PENDING");
  assert.equal(result.redirectUrl, "https://pay.live.swiftpay.ph/checkout");
  assert.equal(requests.at(-1).url, "https://provider.example/api/orders");
  const providerBody = JSON.parse(requests.at(-1).options.body);
  assert.equal(providerBody.x_amount, 15);
  assert.equal(providerBody.x_reference_no, "ORDER-1");
  assert.equal(typeof providerBody.details, "string");
  assert.deepEqual(JSON.parse(providerBody.details), {});
  assert.match(providerBody.signature, /^[a-f0-9]{64}$/);
});

test("payment order preserves a provider 404 code for diagnosis", async () => {
  orderFailureStatus = 404;
  try {
    const response = await fetch(`${baseUrl}/v1/payments/orders`, {
      method: "POST",
      headers: {
        authorization: "Bearer valid-test-token",
        "content-type": "application/json"
      },
      body: JSON.stringify({ amount: 15, referenceNo: "ORDER-404", details: {} })
    });
    assert.equal(response.status, 404);
    assert.deepEqual(await response.json(), {
      error: "SwiftPay request failed (NOT_FOUND)",
      errorCode: "NOT_FOUND"
    });
    assert.equal(requests.at(-1).url, "https://provider.example/api/orders");
  } finally {
    orderFailureStatus = 0;
  }
});

test("read-only scope can poll payment status using the provider payment token", async () => {
  const response = await fetch(`${baseUrl}/v1/payments/payment-token-1/status`, {
    headers: { authorization: "Bearer read-only-test-token" }
  });
  assert.equal(response.status, 200);
  assert.deepEqual(await response.json(), { paymentId: "payment-token-1", status: "EXECUTED" });
  assert.equal(requests.at(-1).url, "https://provider.example/api/payments/status");
  assert.equal(requests.at(-1).options.headers["X-Swiftpay-Payment-Token"], "payment-token-1");

  const forbidden = await fetch(`${baseUrl}/v1/payments/orders`, {
    method: "POST",
    headers: {
      authorization: "Bearer read-only-test-token",
      "content-type": "application/json"
    },
    body: JSON.stringify({ amount: 10, referenceNo: "DENIED-1", details: {} })
  });
  assert.equal(forbidden.status, 403);
});

test("wallet balance reads the documented account number and balance using Basic auth", async () => {
  const response = await fetch(`${baseUrl}/v1/wallet/balance`, {
    headers: { authorization: "Bearer valid-test-token" }
  });
  assert.equal(response.status, 200);
  assert.deepEqual(await response.json(), {
    balance: 12500.75,
    adjustedBalance: 12500.75,
    accountNumber: "001002003004"
  });
  assert.equal(requests.at(-1).url, "https://provider.example/api/disbursements/account");
  assert.match(requests.at(-1).options.headers.Authorization, /^Basic /);
});

test("wallet balance reads only the documented account number and balance fields", async () => {
  accountResponse = { number: "009988776655", balance: "9876.50", available_balance: 0 };
  try {
    const response = await fetch(`${baseUrl}/v1/wallet/balance`, {
      headers: { authorization: "Bearer valid-test-token" }
    });
    assert.equal(response.status, 200);
    assert.deepEqual(await response.json(), {
      balance: 9876.5,
      adjustedBalance: 9876.5,
      accountNumber: "009988776655"
    });
  } finally {
    accountResponse = { number: "001002003004", balance: 12500.75 };
  }
});

test("wallet balance rejects a provider response without its documented account number", async () => {
  accountResponse = { balance: 9876.5 };
  try {
    const response = await fetch(`${baseUrl}/v1/wallet/balance`, {
      headers: { authorization: "Bearer valid-test-token" }
    });
    assert.equal(response.status, 502);
    assert.deepEqual(await response.json(), { error: "Internal server error" });
  } finally {
    accountResponse = { number: "001002003004", balance: 12500.75 };
  }
});

test("wallet balance rejects the undocumented available_balance field", async () => {
  accountResponse = { number: "001002003004", available_balance: 9876.5 };
  try {
    const response = await fetch(`${baseUrl}/v1/wallet/balance`, {
      headers: { authorization: "Bearer valid-test-token" }
    });
    assert.equal(response.status, 502);
    assert.deepEqual(await response.json(), { error: "Internal server error" });
  } finally {
    accountResponse = { number: "001002003004", balance: 12500.75 };
  }
});

test("wallet balance reports when SwiftPay has no configured disbursement account", async () => {
  accountResponseStatus = 404;
  try {
    const response = await fetch(`${baseUrl}/v1/wallet/balance`, {
      headers: { authorization: "Bearer valid-test-token" }
    });
    assert.equal(response.status, 503);
    assert.deepEqual(await response.json(), {
      error: "SwiftPay has no disbursement account configured for this merchant"
    });
  } finally {
    accountResponseStatus = 200;
  }
});

test("wallet balance failure logs SwiftPay HTTP status and safe provider diagnostics", async () => {
  accountResponseStatus = 502;
  accountResponse = {
    code: "UPSTREAM_FAILURE",
    requestUuid: "provider-request-123",
    errorMessage: "Sensitive provider response"
  };
  const originalConsoleError = console.error;
  const logged = [];
  console.error = (...values) => logged.push(values.join(" "));
  try {
    const response = await fetch(`${baseUrl}/v1/wallet/balance`, {
      headers: { authorization: "Bearer valid-test-token" }
    });
    assert.equal(response.status, 502);
    assert.deepEqual(await response.json(), {
      error: "Internal server error",
      errorCode: "UPSTREAM_FAILURE",
      supportReference: "provider-request-123"
    });
    assert.equal(logged.length, 1);
    assert.match(logged[0], /"providerStatus":502/);
    assert.match(logged[0], /"errorCode":"UPSTREAM_FAILURE"/);
    assert.match(logged[0], /"supportReference":"provider-request-123"/);
    assert.equal(logged[0].includes("Sensitive provider response"), false);
  } finally {
    console.error = originalConsoleError;
    accountResponseStatus = 200;
    accountResponse = { number: "001002003004", balance: 12500.75 };
  }
});

test("wallet balance non-JSON failure logs only the upstream status and content type", async () => {
  accountResponseStatus = 403;
  accountResponseText = "<html>provider gateway error</html>";
  accountResponseContentType = "text/html; charset=utf-8";
  const originalConsoleError = console.error;
  const logged = [];
  console.error = (...values) => logged.push(values.join(" "));
  try {
    const response = await fetch(`${baseUrl}/v1/wallet/balance`, {
      headers: { authorization: "Bearer valid-test-token" }
    });
    assert.equal(response.status, 502);
    assert.deepEqual(await response.json(), { error: "Internal server error" });
    assert.equal(logged.length, 1);
    assert.match(logged[0], /"providerStatus":403/);
    assert.match(logged[0], /"providerContentType":"text\/html"/);
    assert.equal(logged[0].includes("provider gateway error"), false);
  } finally {
    console.error = originalConsoleError;
    accountResponseStatus = 200;
    accountResponseText = undefined;
    accountResponseContentType = "application/json";
  }
});

test("payment links use Basic auth and require a fixed supported-currency amount", async () => {
  const response = await fetch(`${baseUrl}/v1/payment-links`, {
    method: "POST",
    headers: {
      authorization: "Bearer valid-test-token",
      "content-type": "application/json"
    },
    body: JSON.stringify({ amount: 125.5, currency: "PHP", description: "Invoice" })
  });
  assert.equal(response.status, 200);
  const paymentLink = await response.json();
  assert.equal(paymentLink.code, "PL-1");
  assert.equal(paymentLink.paymentUrl, "https://merchant.example/checkout/PL-1");
  assert.equal(requests.at(-1).url, "https://provider.example/api/payments/links");
  assert.match(requests.at(-1).options.headers.Authorization, /^Basic /);
  assert.equal(JSON.parse(requests.at(-1).options.body).amount, 125.5);
  assert.equal(JSON.parse(requests.at(-1).options.body).currency, "PHP");

  const euroLink = await fetch(`${baseUrl}/v1/payment-links`, {
    method: "POST",
    headers: {
      authorization: "Bearer valid-test-token",
      "content-type": "application/json"
    },
    body: JSON.stringify({ amount: 20, currency: "EUR", description: "Euro invoice" })
  });
  assert.equal(euroLink.status, 200);
  assert.equal(JSON.parse(requests.at(-1).options.body).currency, "EUR");

  const checkoutPageResponse = await fetch(`${baseUrl}${new URL(paymentLink.paymentUrl).pathname}`);
  assert.equal(checkoutPageResponse.status, 200);
  assert.match(checkoutPageResponse.headers.get("content-type"), /text\/html/);
  assert.match(checkoutPageResponse.headers.get("cache-control"), /no-store/);
  const checkoutPage = await checkoutPageResponse.text();
  assert.match(checkoutPage, /DRPAY/);
  assert.match(checkoutPage, /125\.50/);
  assert.match(checkoutPage, /Invoice &lt;script&gt;alert\(1\)&lt;\/script&gt;/);
  assert.doesNotMatch(checkoutPage, /<script>/);
  assert.match(checkoutPage, /https:\/\/pay\.example\/link\/PL-1/);
  assert.match(checkoutPage, /Official payment partner logos/);
  assert.match(checkoutPage, /src="https:\/\/assets\.swiftpay\.ph\/institutions\/bpi\.png" alt="Bank of the Philippine Islands logo"/);
  assert.match(checkoutPage, /src="https:\/\/swiftpay-qa-gateway-frontend\.s3-ap-southeast-1\.amazonaws\.com\/static\/media\/bank-icons\/vbank\.png" alt="VBank logo"/);
  assert.match(checkoutPage, /No logo institution/);
  assert.match(checkoutPage, /institution-card institution-card--text/);
  assert.doesNotMatch(checkoutPage, /logos\.example|disabled\.png|method-logo-fallback/);
  assert.doesNotMatch(checkoutPage, /Continue with SwiftPay QR Ph|Other payment methods/);
  assert.match(checkoutPage, /action="\/checkout\/PL-1\/qrph"/);
  assert.match(checkoutPage, /Continue to secure payment/);
  assert.match(checkoutPage, /Pay with QR Ph/);

  const checkoutCss = await fetch(`${baseUrl}/checkout-assets/checkout.css`);
  assert.equal(checkoutCss.status, 200);
  assert.match(await checkoutCss.text(), /institution-grid/);

  const invalid = await fetch(`${baseUrl}/v1/payment-links`, {
    method: "POST",
    headers: {
      authorization: "Bearer valid-test-token",
      "content-type": "application/json"
    },
    body: JSON.stringify({ amount: 0, currency: "PHP" })
  });
  assert.equal(invalid.status, 400);
});

test("self-hosted QR Ph checkout presents a valid generic scan flow", async () => {
  const response = await fetch(`${baseUrl}/checkout/PL-1/qrph`, {
    method: "POST",
    headers: { "content-type": "application/x-www-form-urlencoded" },
    body: "paymentApp=qrph"
  });
  assert.equal(response.status, 200);
  const html = await response.text();
  assert.match(html, /Scan to pay/);
  assert.match(html, /supports QR Ph/);
  assert.match(html, /class="checkout-qr"/);
  const qrRequest = JSON.parse(requests.findLast(({ url }) => url.endsWith("/bootstrap/qrph?type=P2M")).options.body);
  assert.equal(qrRequest.x_currency, "PHP");
  assert.equal(qrRequest.x_amount, 125.5);
});

test("self-hosted checkout QR Ph converts USD link prices to a PHP settlement QR", async () => {
  fxRequests = [];
  const response = await fetch(`${baseUrl}/checkout/PL-USD/qrph`, {
    method: "POST",
    headers: { "content-type": "application/x-www-form-urlencoded" },
    body: ""
  });
  assert.equal(response.status, 200);
  assert.match(response.headers.get("cache-control"), /no-store/);
  const html = await response.text();
  assert.match(html, /\$20\.00/);
  assert.match(html, /₱1,140\.00/);
  assert.match(html, /data:image\/png;base64,/);
  assert.match(html, /Rate used: 1 USD = 57\.000000 PHP/);
  assert.equal(new URL(fxRequests[0].url).searchParams.get("base_currency"), "USD");
  const bootstrapPayload = JSON.parse(requests.findLast(({ url }) => url.endsWith("/bootstrap/qrph?type=P2M")).options.body);
  assert.equal(bootstrapPayload.x_currency, "PHP");
  assert.equal(bootstrapPayload.x_amount, 1140);
});

test("payment institution catalogue returns enabled institutions with trusted official logos", async () => {
  const response = await fetch(`${baseUrl}/v1/payment-institutions`, {
    headers: { authorization: "Bearer valid-test-token" }
  });
  assert.equal(response.status, 200);
  assert.deepEqual(await response.json(), [
    {
      code: "BPI",
      name: "Bank of the Philippine Islands",
      logoUrl: "https://assets.swiftpay.ph/institutions/bpi.png"
    },
    {
      code: "UNTRUSTED",
      name: "Untrusted logo host",
      logoUrl: null
    },
    {
      code: "VBANK",
      name: "VBank",
      logoUrl: "https://swiftpay-qa-gateway-frontend.s3-ap-southeast-1.amazonaws.com/static/media/bank-icons/vbank.png"
    },
    {
      code: "NO_LOGO",
      name: "No logo institution",
      logoUrl: null
    },
    {
      code: "ALIPAY",
      name: "Alipay",
      logoUrl: "https://assets.swiftpay.ph/institutions/alipay.png"
    },
    {
      code: "NAVERPAY",
      name: "Naver Pay",
      logoUrl: "https://assets.swiftpay.ph/institutions/naver-pay.png"
    },
    {
      code: "KAKAOPAY",
      name: "Kakao Pay",
      logoUrl: "https://assets.swiftpay.ph/institutions/kakao-pay.png"
    },
    {
      code: "TOSSBANK",
      name: "Toss Bank",
      logoUrl: "https://assets.swiftpay.ph/institutions/toss-bank.png"
    }
  ]);
  assert.equal(requests.at(-1).url, "https://provider.example/api/institutions");
});

test("self-hosted checkout retains the last supported bank list when SwiftPay catalogue is temporarily unavailable", async () => {
  testClock += 61_000;
  institutionFailureStatus = 502;
  try {
    const response = await fetch(`${baseUrl}/checkout/PL-1`);
    assert.equal(response.status, 200);
    const html = await response.text();
    assert.match(html, /Bank of the Philippine Islands/);
    assert.match(html, /VBank/);
    assert.match(html, /No logo institution/);
    assert.match(html, /Continue to secure payment/);

    const requestCount = requests.filter(({ url }) => url.endsWith("/institutions")).length;
    await fetch(`${baseUrl}/checkout/PL-1`);
    assert.equal(requests.filter(({ url }) => url.endsWith("/institutions")).length, requestCount);
  } finally {
    institutionFailureStatus = 0;
  }
});

test("disbursement is represented as pending after provider acceptance", async () => {
  const appResponse = await fetch(`${baseUrl}/v1/disbursements`, {
    method: "POST",
    headers: {
      authorization: "Bearer valid-test-token",
      "content-type": "application/json"
    },
    body: JSON.stringify({
      amount: 75,
      referenceNo: "TRANSFER-1",
      channel: "INSTAPAY",
      institutionCode: "TESTBANK",
      recipientInformation: { accountNumber: "123456", fullName: "Test Recipient" }
    })
  });
  assert.equal(appResponse.status, 202);
  assert.equal((await appResponse.json()).status, "PENDING");
  assert.equal(requests.at(-1).url, "https://provider.example/api/disbursements/send");
  assert.match(requests.at(-1).options.headers.Authorization, /^Basic /);
  assert.equal(JSON.parse(requests.at(-1).options.body).creditInformation.remarks, "Bank transfer");
});

test("disbursement trims supplied remarks and replaces blank remarks with a nonblank default", async () => {
  const headers = {
    authorization: "Bearer valid-test-token",
    "content-type": "application/json"
  };
  const body = {
    amount: 75,
    referenceNo: "TRANSFER-REMARKS-1",
    channel: "INSTAPAY",
    institutionCode: "TESTBANK",
    recipientInformation: { accountNumber: "123456", fullName: "Test Recipient" }
  };

  const supplied = await fetch(`${baseUrl}/v1/disbursements`, {
    method: "POST",
    headers,
    body: JSON.stringify({ ...body, remarks: "  Rent payment  " })
  });
  assert.equal(supplied.status, 202);
  assert.equal(JSON.parse(requests.at(-1).options.body).creditInformation.remarks, "Rent payment");

  const blank = await fetch(`${baseUrl}/v1/disbursements`, {
    method: "POST",
    headers,
    body: JSON.stringify({ ...body, referenceNo: "TRANSFER-REMARKS-2", remarks: "   " })
  });
  assert.equal(blank.status, 202);
  assert.equal(JSON.parse(requests.at(-1).options.body).creditInformation.remarks, "Bank transfer");
});

test("disbursement rejects a one-part recipient name before contacting SwiftPay", async () => {
  const callCount = requests.length;
  const response = await fetch(`${baseUrl}/v1/disbursements`, {
    method: "POST",
    headers: {
      authorization: "Bearer valid-test-token",
      "content-type": "application/json"
    },
    body: JSON.stringify({
      amount: 75,
      referenceNo: "TRANSFER-INCOMPLETE-NAME",
      channel: "INSTAPAY",
      institutionCode: "TESTBANK",
      recipientInformation: { accountNumber: "123456", fullName: "Teodoro" }
    })
  });
  assert.equal(response.status, 400);
  assert.equal(requests.length, callCount);
  assert.match((await response.json()).error, /full legal name must include the given name and surname/i);
});

test("transfer errors preserve SwiftPay error code and safe support reference", async () => {
  disbursementFailure = {
    status: 400,
    body: { errorCode: "INVALID_INPUT", errorUuid: "support-ref-123", errorMessage: "Sensitive provider detail" }
  };
  try {
    const response = await fetch(`${baseUrl}/v1/disbursements`, {
      method: "POST",
      headers: {
        authorization: "Bearer valid-test-token",
        "content-type": "application/json"
      },
      body: JSON.stringify({
        amount: 75,
        referenceNo: "TRANSFER-INVALID-1",
        channel: "INSTAPAY",
        institutionCode: "TESTBANK",
        recipientInformation: { accountNumber: "123456", fullName: "Test Recipient" }
      })
    });
    assert.equal(response.status, 400);
    assert.deepEqual(await response.json(), {
      error: "SwiftPay request failed (INVALID_INPUT)",
      errorCode: "INVALID_INPUT",
      supportReference: "support-ref-123"
    });
  } finally {
    disbursementFailure = null;
  }
});

test("disbursement history requests a provider page and returns its records as a list", async () => {
  disbursementPage = {
    pageNo: 0,
    result: [{
      id: "9d51c8e2-3211-4c01-a9a1-19a9a8bd5c01",
      merchantReferenceNo: "TRANSFER-HISTORY-1",
      status: "EXECUTED",
      registrationTime: "2026-10-12T00:00:00Z",
      creditInformation: { amount: 75 }
    }],
    totalCount: 1,
    resultCount: 1
  };
  try {
    const response = await fetch(`${baseUrl}/v1/disbursements`, {
      headers: { authorization: "Bearer valid-test-token" }
    });
    assert.equal(response.status, 200);
    assert.deepEqual(await response.json(), disbursementPage.result);
    const request = requests.at(-1);
    const providerUrl = new URL(request.url);
    assert.equal(providerUrl.pathname, "/api/disbursements");
    assert.equal(providerUrl.searchParams.get("pageNo"), "0");
    assert.equal(providerUrl.searchParams.get("pageSize"), "100");
    assert.match(request.options.headers.Authorization, /^Basic /);
  } finally {
    disbursementPage = { pageNo: 0, result: [], totalCount: 0, resultCount: 0 };
  }
});

test("QR_P2P InstaPay disbursements pass the documented transfer type upstream", async () => {
  const response = await fetch(`${baseUrl}/v1/disbursements`, {
    method: "POST",
    headers: {
      authorization: "Bearer valid-test-token",
      "content-type": "application/json"
    },
    body: JSON.stringify({
      amount: 75,
      referenceNo: "QR-TRANSFER-1",
      channel: "INSTAPAY",
      type: "QR_P2P",
      institutionCode: "TESTBANK",
      recipientInformation: { accountNumber: "123456", fullName: "Test Recipient" }
    })
  });
  assert.equal(response.status, 202);
  assert.equal(JSON.parse(requests.at(-1).options.body).type, "QR_P2P");
});

test("PESONET rejects QR disbursement types", async () => {
  const callCount = requests.length;
  const response = await fetch(`${baseUrl}/v1/disbursements`, {
    method: "POST",
    headers: {
      authorization: "Bearer valid-test-token",
      "content-type": "application/json"
    },
    body: JSON.stringify({
      amount: 75,
      referenceNo: "QR-TRANSFER-2",
      channel: "PESONET",
      type: "QR_P2P",
      institutionCode: "TESTBANK",
      recipientInformation: { accountNumber: "123456", fullName: "Test Recipient" }
    })
  });
  assert.equal(response.status, 400);
  assert.equal(requests.length, callCount);
});

test("rejects invalid transfer amount before contacting provider", async () => {
  const callCount = requests.length;
  const response = await fetch(`${baseUrl}/v1/disbursements`, {
    method: "POST",
    headers: {
      authorization: "Bearer valid-test-token",
      "content-type": "application/json"
    },
    body: JSON.stringify({
      amount: 50001,
      referenceNo: "TRANSFER-2",
      channel: "INSTAPAY",
      institutionCode: "TESTBANK",
      recipientInformation: { accountNumber: "123456", fullName: "Test Recipient" }
    })
  });
  assert.equal(response.status, 400);
  assert.equal(requests.length, callCount);
});