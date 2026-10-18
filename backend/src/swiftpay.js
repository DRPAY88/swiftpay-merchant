import { createHmac, randomUUID, timingSafeEqual } from "node:crypto";

export function canonicalSignedMessage(fields) {
  return Object.entries(fields)
    .filter(([key, value]) => key.startsWith("x_") && value !== null && value !== undefined)
    .sort(([left], [right]) => left < right ? -1 : left > right ? 1 : 0)
    .map(([key, value]) => `${key}${key === "x_amount" ? Number(value).toFixed(2) : String(value)}`)
    .join("");
}

export const canonicalOrderMessage = canonicalSignedMessage;

export function signOrder(fields, secretKey) {
  return createHmac("sha256", Buffer.from(secretKey, "utf8"))
    .update(canonicalSignedMessage(fields), "utf8")
    .digest("hex");
}

export function verifySwiftPaySignature(fields, signature, secretKey) {
  if (typeof signature !== "string" || !/^[\da-f]{64}$/i.test(signature)) return false;
  const submitted = Buffer.from(signature, "hex");
  const expected = Buffer.from(signOrder(fields, secretKey), "hex");
  return submitted.length === expected.length && timingSafeEqual(submitted, expected);
}

export function buildOrderPayload(input, config, paymentId = randomUUID()) {
  const fields = {
    x_access_key: config.swiftPayAccessKey,
    x_amount: Number(input.amount.toFixed(2)),
    x_reference_no: input.referenceNo,
    x_payment_id: paymentId,
    x_currency: input.currency
  };
  return {
    ...fields,
    signature: signOrder(fields, config.swiftPaySecretKey),
    details: JSON.stringify(input.details),
    generate_customer_redirect_url: true,
    ...(input.institutionCode ? { institution_code: input.institutionCode } : {}),
    ...(input.merchantTag ? { merchant_tag: input.merchantTag } : {})
  };
}

export function buildQrPayload(input, config) {
  const fields = {
    x_access_key: config.swiftPayAccessKey,
    x_amount: Number(input.amount.toFixed(2)),
    x_currency: input.currency || "PHP",
    x_reference_no: input.referenceNo
  };
  return {
    ...fields,
    signature: signOrder(fields, config.swiftPaySecretKey),
    ...(input.rejectDuplicatedPayments ? { reject_qrph_duplicated_payments: true } : {})
  };
}

export class SwiftPayError extends Error {
  constructor(status, message, { errorCode, supportReference, providerStatus, providerContentType } = {}) {
    super(message);
    this.status = status;
    this.errorCode = errorCode;
    this.supportReference = supportReference;
    this.providerStatus = providerStatus;
    this.providerContentType = providerContentType;
  }
}

function safeProviderErrorCode(data) {
  const code = data?.errorCode || data?.code;
  return typeof code === "string" && /^[a-zA-Z0-9_-]{1,64}$/.test(code) ? code : undefined;
}

function getProviderContentType(response) {
  const contentType = response.headers.get("content-type")?.split(";")[0].trim().toLowerCase();
  return contentType && /^[a-z0-9!#$&^_.+-]+\/[a-z0-9!#$&^_.+-]+$/.test(contentType)
    ? contentType
    : "unknown";
}

export async function callSwiftPay(config, fetchImpl, path, options = {}) {
  const headers = { Accept: options.responseType === "bytes" ? "image/png" : "application/json" };
  if (options.auth === "basic") {
    const credentials = Buffer.from(`${config.swiftPayAccessKey}:${config.swiftPaySecretKey}`).toString("base64");
    headers.Authorization = `Basic ${credentials}`;
  }
  if (options.paymentToken) {
    headers["X-Swiftpay-Payment-Token"] = options.paymentToken;
  }
  if (options.body !== undefined) {
    headers["Content-Type"] = "application/json";
  }

  let response;
  try {
    response = await fetchImpl(new URL(path.replace(/^\//, ""), config.swiftPayBaseUrl), {
      method: options.method || "GET",
      headers,
      ...(options.body !== undefined ? { body: JSON.stringify(options.body) } : {}),
      signal: AbortSignal.timeout(15000)
    });
  } catch {
    throw new SwiftPayError(502, "The payment provider is temporarily unavailable");
  }

  if (options.responseType === "bytes") {
    if (!response.ok) {
      const responseText = await response.text();
      let data = null;
      try {
        data = responseText ? JSON.parse(responseText) : null;
      } catch {
        throw new SwiftPayError(502, "The payment provider returned an invalid response", {
          providerStatus: response.status,
          providerContentType: getProviderContentType(response)
        });
      }
      const code = safeProviderErrorCode(data);
      const message = code ? `Payment provider request failed (${code})` : "Payment provider request failed";
      const supportReference = [data?.errorUuid, data?.requestUuid]
        .find((value) => typeof value === "string" && /^[a-zA-Z0-9-]{1,80}$/.test(value));
      throw new SwiftPayError(response.status >= 500 ? 502 : response.status, message, {
        errorCode: typeof code === "string" ? code : undefined,
        supportReference,
        providerStatus: response.status
      });
    }
    const contentType = response.headers.get("content-type")?.split(";")[0].trim().toLowerCase();
    if (contentType !== "image/png") {
      throw new SwiftPayError(502, "The payment provider did not return a PNG image");
    }
    const data = Buffer.from(await response.arrayBuffer());
    if (data.length === 0 || data.length > 1_048_576) {
      throw new SwiftPayError(502, "The payment provider returned an invalid QR image");
    }
    return { status: response.status, data, contentType };
  }

  const responseText = await response.text();
  let data = null;
  if (responseText) {
    try {
      data = JSON.parse(responseText);
    } catch {
      throw new SwiftPayError(502, "The payment provider returned an invalid response", {
        providerStatus: response.status,
        providerContentType: getProviderContentType(response)
      });
    }
  }
  if (!response.ok) {
    const code = safeProviderErrorCode(data);
    const message = code ? `Payment provider request failed (${code})` : "Payment provider request failed";
    const supportReference = [data?.errorUuid, data?.requestUuid]
      .find((value) => typeof value === "string" && /^[a-zA-Z0-9-]{1,80}$/.test(value));
    throw new SwiftPayError(response.status >= 500 ? 502 : response.status, message, {
      errorCode: typeof code === "string" ? code : undefined,
      supportReference,
      providerStatus: response.status
    });
  }
  return { status: response.status, data };
}

export function newReference() {
  return randomUUID();
}