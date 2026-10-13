import { createSign } from "node:crypto";

const FCM_SCOPE = "https://www.googleapis.com/auth/firebase.messaging";
const OAUTH_TOKEN_URL = "https://oauth2.googleapis.com/token";

function base64url(value) {
  return Buffer.from(value).toString("base64url");
}

export function createFcmSender(serviceAccountJson, fetchImpl = fetch, now = Date.now) {
  if (!serviceAccountJson) return null;
  let serviceAccount;
  try {
    serviceAccount = typeof serviceAccountJson === "string"
      ? JSON.parse(serviceAccountJson)
      : serviceAccountJson;
  } catch {
    throw new Error("FCM service-account JSON is invalid");
  }
  const { project_id: projectId, client_email: clientEmail, private_key: privateKey } = serviceAccount;
  if (
    typeof projectId !== "string" ||
    !/^[a-z][a-z0-9-]{4,28}[a-z0-9]$/.test(projectId) ||
    typeof clientEmail !== "string" ||
    !/^[^@\s]+@[^@\s]+\.iam\.gserviceaccount\.com$/.test(clientEmail) ||
    typeof privateKey !== "string" ||
    !privateKey.includes("PRIVATE KEY")
  ) {
    throw new Error("FCM service-account JSON has invalid project, email, or private-key fields");
  }

  let accessToken = null;
  let accessTokenExpiresAt = 0;
  let pendingTokenRequest = null;

  async function getAccessToken() {
    if (accessToken && accessTokenExpiresAt > now() + 60_000) return accessToken;
    if (pendingTokenRequest) return pendingTokenRequest;

    pendingTokenRequest = (async () => {
      const issuedAt = Math.floor(now() / 1000);
      const header = base64url(JSON.stringify({ alg: "RS256", typ: "JWT" }));
      const claims = base64url(JSON.stringify({
        iss: clientEmail,
        scope: FCM_SCOPE,
        aud: OAUTH_TOKEN_URL,
        iat: issuedAt,
        exp: issuedAt + 3600
      }));
      const assertionInput = `${header}.${claims}`;
      const signer = createSign("RSA-SHA256");
      signer.update(assertionInput);
      signer.end();
      const assertion = `${assertionInput}.${signer.sign(privateKey).toString("base64url")}`;

      const response = await fetchImpl(OAUTH_TOKEN_URL, {
        method: "POST",
        headers: { "content-type": "application/x-www-form-urlencoded" },
        body: new URLSearchParams({
          grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
          assertion
        }),
        signal: AbortSignal.timeout(10_000)
      });
      const body = await response.json().catch(() => null);
      if (!response.ok || typeof body?.access_token !== "string" || !Number.isFinite(body.expires_in)) {
        throw new Error("FCM authorization failed");
      }
      accessToken = body.access_token;
      accessTokenExpiresAt = now() + body.expires_in * 1000;
      return accessToken;
    })().finally(() => {
      pendingTokenRequest = null;
    });
    return pendingTokenRequest;
  }

  return {
    projectId,
    async send({ token, notification, data }) {
      const bearer = await getAccessToken();
      const response = await fetchImpl(
        `https://fcm.googleapis.com/v1/projects/${encodeURIComponent(projectId)}/messages:send`,
        {
          method: "POST",
          headers: {
            authorization: `Bearer ${bearer}`,
            "content-type": "application/json"
          },
          body: JSON.stringify({
            message: {
              token,
              notification,
              data,
              android: {
                priority: "high",
                notification: { channel_id: "incoming_money" }
              }
            }
          }),
          signal: AbortSignal.timeout(10_000)
        }
      );
      if (response.ok) return { ok: true };
      const body = await response.json().catch(() => null);
      const errorCode = body?.error?.details?.find((detail) =>
        detail["@type"] === "type.googleapis.com/google.firebase.fcm.v1.FcmError"
      )?.errorCode;
      if (errorCode === "UNREGISTERED") return { ok: false, invalidToken: true };
      throw new Error("FCM message delivery failed");
    }
  };
}
