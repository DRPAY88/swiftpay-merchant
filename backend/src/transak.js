import { createHmac, randomUUID } from "node:crypto";
import { isIP } from "node:net";

const TRANSAK_PACKAGE_ID = "com.swiftpay.site";

export class TransakError extends Error {
  constructor(status, message) {
    super(message);
    this.status = status;
  }
}

export function createTransakWidgetService(config, fetchImpl = fetch, now = Date.now) {
  const settings = config.transak;
  let cachedAccessToken = null;
  let cachedTokenExpiresAt = 0;

  const tokenUrl = settings?.environment === "production"
    ? "https://api.transak.com/partners/api/v2/refresh-token"
    : "https://api-stg.transak.com/partners/api/v2/refresh-token";
  const widgetUrl = settings?.environment === "production"
    ? "https://api-gateway.transak.com/api/v2/auth/session"
    : "https://api-gateway-stg.transak.com/api/v2/auth/session";
  const expectedWidgetHost = settings?.environment === "production"
    ? "global.transak.com"
    : "global-stg.transak.com";

  async function requestJson(url, options, failureMessage) {
    let response;
    try {
      response = await fetchImpl(url, {
        ...options,
        signal: AbortSignal.timeout(15000)
      });
    } catch {
      throw new TransakError(502, "Transak is temporarily unavailable");
    }
    let body;
    try {
      body = await response.json();
    } catch {
      throw new TransakError(502, "Transak returned an invalid response");
    }
    if (!response.ok) {
      throw new TransakError(502, failureMessage);
    }
    return body;
  }

  async function getAccessToken() {
    if (!settings?.apiKey || !settings.apiSecret) {
      throw new TransakError(503, "USDT checkout is not configured for this service");
    }
    if (cachedAccessToken && cachedTokenExpiresAt - now() > 60_000) {
      return cachedAccessToken;
    }

    const response = await requestJson(tokenUrl, {
      method: "POST",
      headers: {
        "x-api-key": settings.apiKey,
        "api-secret": settings.apiSecret,
        "content-type": "application/json"
      },
      body: JSON.stringify({ apiKey: settings.apiKey })
    }, "Transak authentication is unavailable");
    const accessToken = response.data?.accessToken;
    const expiresAt = Number(response.data?.expiresAt) * 1000;
    if (typeof accessToken !== "string" || !Number.isFinite(expiresAt) || expiresAt <= now()) {
      throw new TransakError(502, "Transak returned an invalid access token");
    }
    cachedAccessToken = accessToken;
    cachedTokenExpiresAt = expiresAt;
    return accessToken;
  }

  async function createWidgetUrl({ mode, amount, walletAddress, userIp, customerId }) {
    if (!settings?.apiKey || !settings.apiSecret) {
      throw new TransakError(503, "USDT checkout is not configured for this service");
    }
    if (isIP(userIp) === 0) {
      throw new TransakError(400, "A valid customer IP address is required to open Transak checkout");
    }

    const accessToken = await getAccessToken();
    const partnerOrderId = randomUUID();
    const partnerCustomerId = createHmac("sha256", config.sessionSigningKey)
      .update(customerId)
      .digest("hex");
    const widgetParams = {
      apiKey: settings.apiKey,
      referrerDomain: TRANSAK_PACKAGE_ID,
      productsAvailed: mode,
      countryCode: "PH",
      fiatCurrency: "PHP",
      cryptoCurrencyCode: "USDT",
      network: "tron",
      partnerOrderId,
      partnerCustomerId,
      ...(mode === "BUY" ? { walletAddress: walletAddress.trim() } : {}),
      ...(mode === "BUY"
        ? { fiatAmount: Number(amount.toFixed(2)) }
        : { cryptoAmount: Number(amount.toFixed(2)) })
    };
    const response = await requestJson(widgetUrl, {
      method: "POST",
      headers: {
        "x-api-key": settings.apiKey,
        "access-token": accessToken,
        "x-user-ip": userIp,
        "content-type": "application/json"
      },
      body: JSON.stringify({ widgetParams })
    }, "Could not start Transak checkout");

    let checkoutUrl;
    try {
      checkoutUrl = new URL(response.data?.widgetUrl);
    } catch {
      throw new TransakError(502, "Transak did not return a valid checkout URL");
    }
    if (checkoutUrl.protocol !== "https:" || checkoutUrl.hostname !== expectedWidgetHost) {
      throw new TransakError(502, "Transak did not return a valid checkout URL");
    }
    return { widgetUrl: checkoutUrl.toString(), partnerOrderId };
  }

  return { createWidgetUrl };
}