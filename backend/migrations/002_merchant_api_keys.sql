CREATE TABLE IF NOT EXISTS merchant_api_keys (
  id TEXT PRIMARY KEY,
  email TEXT NOT NULL REFERENCES merchant_users(email) ON DELETE CASCADE,
  name TEXT NOT NULL,
  key_prefix TEXT NOT NULL,
  key_hash TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  last_used_at TIMESTAMPTZ,
  revoked_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS merchant_api_keys_email_created
  ON merchant_api_keys (email, created_at DESC);
