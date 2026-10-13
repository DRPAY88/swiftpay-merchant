import assert from "node:assert/strict";
import { generateKeyPairSync } from "node:crypto";
import { test } from "node:test";
import { createFcmSender } from "../src/fcm.js";

const { privateKey } = generateKeyPairSync("rsa", {
  modulusLength: 2048,
  privateKeyEncoding: { type: "pkcs8", format: "pem" },
  publicKeyEncoding: { type: "spki", format: "pem" }
});
const serviceAccount = JSON.stringify({
  project_id: "test-project",
  client_email: "push@test-project.iam.gserviceaccount.com",
  private_key: privateKey
});

test("FCM sender obtains and caches service-account access tokens", async () => {
  const requests = [];
  const sender = createFcmSender(serviceAccount, async (url, options) => {
    requests.push({ url: url.toString(), options });
    if (url.toString() === "https://oauth2.googleapis.com/token") {
      return Response.json({ access_token: "oauth-access-token", expires_in: 3600 });
    }
    return Response.json({ name: "projects/test-project/messages/1" });
  }, () => 1_800_000_000_000);

  const first = await sender.send({
    token: "firebase-device-token",
    notification: { title: "Money received", body: "Received ₱25.00" },
    data: { type: "incoming_payment", paymentId: "payment-1" }
  });
  const second = await sender.send({
    token: "another-device-token",
    notification: { title: "Money received", body: "Received ₱25.00" },
    data: { type: "incoming_payment", paymentId: "payment-1" }
  });

  assert.deepEqual(first, { ok: true });
  assert.deepEqual(second, { ok: true });
  assert.equal(requests.filter(({ url }) => url === "https://oauth2.googleapis.com/token").length, 1);
  const pushRequest = requests.find(({ url }) => url.includes("messages:send"));
  assert.equal(pushRequest.options.headers.authorization, "Bearer oauth-access-token");
  assert.equal(JSON.parse(pushRequest.options.body).message.data.paymentId, "payment-1");
  assert.equal(JSON.parse(pushRequest.options.body).message.android.priority, "high");
});

test("FCM sender identifies invalid device tokens and rejects incomplete credentials", async () => {
  const sender = createFcmSender(serviceAccount, async (url) => url.toString().includes("oauth2")
    ? Response.json({ access_token: "oauth-access-token", expires_in: 3600 })
    : new Response(JSON.stringify({
        error: {
          details: [{
            "@type": "type.googleapis.com/google.firebase.fcm.v1.FcmError",
            errorCode: "UNREGISTERED"
          }]
        }
      }), { status: 404, headers: { "content-type": "application/json" } })
  );
  assert.deepEqual(await sender.send({
    token: "expired-token",
    notification: { title: "Money received", body: "Received ₱25.00" },
    data: { type: "incoming_payment" }
  }), { ok: false, invalidToken: true });
  assert.throws(
    () => createFcmSender(JSON.stringify({ project_id: "missing-fields" })),
    /invalid project, email, or private-key/
  );
  assert.equal(createFcmSender(null), null);
});
