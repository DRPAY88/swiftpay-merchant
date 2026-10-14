import assert from "node:assert/strict";
import { test } from "node:test";
import { decryptKyb, encryptKyb } from "../src/kyb-crypto.js";

test("KYB encryption round-trips without storing plaintext and rejects tampering", () => {
  const key = Buffer.alloc(32, 9);
  const plaintext = Buffer.from("sensitive business and identity data", "utf8");
  const encrypted = encryptKyb(plaintext, key, "applicant@example.com:selfie");

  assert.notEqual(encrypted.includes(plaintext), true);
  assert.deepEqual(decryptKyb(encrypted, key, "applicant@example.com:selfie"), plaintext);
  assert.throws(() => decryptKyb(encrypted, key, "other@example.com:selfie"));

  const tampered = Buffer.from(encrypted);
  tampered[tampered.length - 1] ^= 1;
  assert.throws(() => decryptKyb(tampered, key));
  assert.throws(() => decryptKyb(encrypted, Buffer.alloc(32, 8)));
});

test("KYB encryption fails closed without a 32-byte key", () => {
  assert.throws(() => encryptKyb(Buffer.from("sensitive"), null), /not configured/);
  assert.throws(() => encryptKyb(Buffer.from("sensitive"), Buffer.alloc(16)), /not configured/);
});
