import { createCipheriv, createDecipheriv, randomBytes } from "node:crypto";

const VERSION = 1;
const NONCE_LENGTH = 12;
const TAG_LENGTH = 16;

export function encryptKyb(value, key, context = "") {
  if (!Buffer.isBuffer(key) || key.length !== 32) {
    throw new Error("KYB encryption key is not configured");
  }
  const nonce = randomBytes(NONCE_LENGTH);
  const cipher = createCipheriv("aes-256-gcm", key, nonce);
  cipher.setAAD(Buffer.from(context, "utf8"));
  const encrypted = Buffer.concat([cipher.update(value), cipher.final()]);
  return Buffer.concat([Buffer.from([VERSION]), nonce, cipher.getAuthTag(), encrypted]);
}

export function decryptKyb(value, key, context = "") {
  if (!Buffer.isBuffer(key) || key.length !== 32) {
    throw new Error("KYB encryption key is not configured");
  }
  if (!Buffer.isBuffer(value) || value.length < 1 + NONCE_LENGTH + TAG_LENGTH || value[0] !== VERSION) {
    throw new Error("Stored KYB data has an unsupported format");
  }
  const nonceStart = 1;
  const tagStart = nonceStart + NONCE_LENGTH;
  const dataStart = tagStart + TAG_LENGTH;
  const decipher = createDecipheriv("aes-256-gcm", key, value.subarray(nonceStart, tagStart));
  decipher.setAAD(Buffer.from(context, "utf8"));
  decipher.setAuthTag(value.subarray(tagStart, dataStart));
  return Buffer.concat([decipher.update(value.subarray(dataStart)), decipher.final()]);
}
