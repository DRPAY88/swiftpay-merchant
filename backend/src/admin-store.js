import pg from "pg";
import { decryptKyb, encryptKyb } from "./kyb-crypto.js";

const { Pool } = pg;

export class AdminStoreError extends Error {
  constructor(status, message) {
    super(message);
    this.status = status;
  }
}

export function createAdminStore(
  databaseUrl,
  pool = new Pool({ connectionString: databaseUrl, max: 5 }),
  { kybEncryptionKey = null } = {}
) {
  async function initialize(merchantEmails, superAdminEmails) {
    await pool.query(`
      CREATE TABLE IF NOT EXISTS merchant_users (
        email TEXT PRIMARY KEY,
        role TEXT NOT NULL CHECK (role IN ('merchant', 'super_admin')),
        status TEXT NOT NULL CHECK (status IN ('active', 'suspended', 'removed')),
        created_by TEXT NOT NULL,
        created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
        updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
        onboarding_eligible BOOLEAN NOT NULL DEFAULT FALSE,
        onboarding_completed_at TIMESTAMPTZ,
        wallet_mode TEXT NOT NULL DEFAULT 'pooled' CHECK (wallet_mode IN ('pooled', 'internal'))
      );
      ALTER TABLE merchant_users ADD COLUMN IF NOT EXISTS onboarding_eligible BOOLEAN NOT NULL DEFAULT FALSE;
      ALTER TABLE merchant_users ADD COLUMN IF NOT EXISTS onboarding_completed_at TIMESTAMPTZ;
      ALTER TABLE merchant_users ADD COLUMN IF NOT EXISTS wallet_mode TEXT NOT NULL DEFAULT 'pooled';
      CREATE TABLE IF NOT EXISTS app_feature_settings (
        id SMALLINT PRIMARY KEY CHECK (id = 1),
        onboarding_enabled BOOLEAN NOT NULL DEFAULT TRUE,
        registration_enabled BOOLEAN NOT NULL DEFAULT TRUE,
        onboarding_eligible_since TIMESTAMPTZ NOT NULL DEFAULT NOW(),
        updated_by TEXT,
        updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
      );
      ALTER TABLE app_feature_settings ADD COLUMN IF NOT EXISTS registration_enabled BOOLEAN NOT NULL DEFAULT TRUE;
      INSERT INTO app_feature_settings (id) VALUES (1) ON CONFLICT (id) DO NOTHING;
      CREATE TABLE IF NOT EXISTS merchant_registrations (
        email TEXT PRIMARY KEY,
        status TEXT NOT NULL CHECK (status IN ('draft', 'submitted', 'approved', 'rejected')),
        encrypted_profile TEXT NOT NULL,
        encrypted_review_note TEXT NOT NULL DEFAULT '',
        generation INTEGER NOT NULL DEFAULT 1,
        reviewed_by TEXT,
        reviewed_at TIMESTAMPTZ,
        created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
        updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
      );
      CREATE TABLE IF NOT EXISTS merchant_registration_documents (
        email TEXT NOT NULL REFERENCES merchant_registrations(email) ON DELETE CASCADE,
        kind TEXT NOT NULL CHECK (kind IN ('business_certificate', 'representative_id', 'selfie')),
        content_type TEXT NOT NULL CHECK (content_type IN ('image/jpeg', 'image/png', 'application/pdf')),
        encrypted_data TEXT NOT NULL,
        generation INTEGER NOT NULL DEFAULT 1,
        created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
        PRIMARY KEY (email, kind)
      );
      ALTER TABLE merchant_registrations
        ADD COLUMN IF NOT EXISTS generation INTEGER NOT NULL DEFAULT 1;
      ALTER TABLE merchant_registrations
        ADD COLUMN IF NOT EXISTS encrypted_review_note TEXT NOT NULL DEFAULT '';
      ALTER TABLE merchant_registration_documents
        ADD COLUMN IF NOT EXISTS generation INTEGER NOT NULL DEFAULT 1;
      CREATE TABLE IF NOT EXISTS customer_users (
        email TEXT PRIMARY KEY,
        status TEXT NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'suspended')),
        created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
        updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
      );
      CREATE TABLE IF NOT EXISTS customer_wallet_transactions (
        id TEXT PRIMARY KEY,
        email TEXT NOT NULL REFERENCES customer_users(email),
        reference_no TEXT NOT NULL,
        payment_id TEXT,
        kind TEXT NOT NULL CHECK (kind IN ('topup', 'disbursement')),
        direction TEXT NOT NULL CHECK (direction IN ('credit', 'debit')),
        amount NUMERIC(14, 2) NOT NULL CHECK (amount > 0),
        currency TEXT NOT NULL DEFAULT 'PHP' CHECK (currency = 'PHP'),
        status TEXT NOT NULL CHECK (status IN ('PENDING', 'EXECUTED', 'REJECTED', 'CANCELED', 'EXPIRED', 'ERROR')),
        details JSONB NOT NULL DEFAULT '{}'::jsonb,
        created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
        updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
        UNIQUE (email, reference_no)
      );
      CREATE UNIQUE INDEX IF NOT EXISTS customer_wallet_payment_id
        ON customer_wallet_transactions (payment_id)
        WHERE payment_id IS NOT NULL AND kind = 'topup';
      CREATE INDEX IF NOT EXISTS customer_wallet_transactions_history
        ON customer_wallet_transactions (email, created_at DESC);
      CREATE TABLE IF NOT EXISTS merchant_wallet_transactions (
        id TEXT PRIMARY KEY,
        email TEXT NOT NULL REFERENCES merchant_users(email),
        reference_no TEXT NOT NULL,
        payment_id TEXT,
        kind TEXT NOT NULL CHECK (kind IN ('collection', 'disbursement')),
        direction TEXT NOT NULL CHECK (direction IN ('credit', 'debit')),
        amount NUMERIC(14, 2) NOT NULL CHECK (amount > 0),
        currency TEXT NOT NULL DEFAULT 'PHP' CHECK (currency = 'PHP'),
        status TEXT NOT NULL CHECK (status IN ('PENDING', 'EXECUTED', 'REJECTED', 'CANCELED', 'EXPIRED', 'ERROR')),
        details JSONB NOT NULL DEFAULT '{}'::jsonb,
        created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
        updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
        UNIQUE (email, reference_no)
      );
      CREATE UNIQUE INDEX IF NOT EXISTS merchant_wallet_collection_reference
        ON merchant_wallet_transactions (reference_no) WHERE kind = 'collection';
      CREATE UNIQUE INDEX IF NOT EXISTS merchant_wallet_disbursement_reference
        ON merchant_wallet_transactions (reference_no) WHERE kind = 'disbursement';
      CREATE UNIQUE INDEX IF NOT EXISTS merchant_wallet_payment_id
        ON merchant_wallet_transactions (payment_id)
        WHERE payment_id IS NOT NULL AND kind = 'collection';
      CREATE INDEX IF NOT EXISTS merchant_wallet_transactions_history
        ON merchant_wallet_transactions (email, created_at DESC);
      UPDATE merchant_users
      SET wallet_mode = 'internal'
      WHERE role = 'merchant' AND created_by <> 'bootstrap';
      UPDATE merchant_users
      SET onboarding_eligible = TRUE
      WHERE onboarding_eligible = FALSE
        AND role = 'merchant'
        AND created_by <> 'bootstrap'
        AND created_at >= (SELECT onboarding_eligible_since FROM app_feature_settings WHERE id = 1);
      CREATE TABLE IF NOT EXISTS admin_audit (
        id BIGSERIAL PRIMARY KEY,
        actor_email TEXT NOT NULL,
        action TEXT NOT NULL,
        target TEXT NOT NULL,
        details JSONB NOT NULL DEFAULT '{}'::jsonb,
        created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
      );
      CREATE TABLE IF NOT EXISTS balance_adjustments (
        id BIGSERIAL PRIMARY KEY,
        amount NUMERIC(14, 2) NOT NULL CHECK (amount <> 0),
        currency TEXT NOT NULL DEFAULT 'PHP' CHECK (currency = 'PHP'),
        reason TEXT NOT NULL,
        actor_email TEXT NOT NULL,
        created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
      );
      CREATE TABLE IF NOT EXISTS manual_bank_deposits (
        id TEXT PRIMARY KEY,
        amount NUMERIC(14, 2) NOT NULL CHECK (amount > 0),
        bank_name TEXT NOT NULL,
        reference_no TEXT NOT NULL,
        notes TEXT NOT NULL DEFAULT '',
        actor_email TEXT NOT NULL,
        created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
      );
      CREATE TABLE IF NOT EXISTS swiftpay_webhook_events (
        event_key TEXT PRIMARY KEY,
        event_type TEXT NOT NULL CHECK (event_type IN ('payment', 'disbursement')),
        payment_id TEXT,
        reference_no TEXT NOT NULL,
        status TEXT NOT NULL,
        details JSONB NOT NULL DEFAULT '{}'::jsonb,
        amount NUMERIC(14, 2),
        currency TEXT,
        received_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
      );
      CREATE TABLE IF NOT EXISTS merchant_push_tokens (
        token TEXT PRIMARY KEY,
        email TEXT NOT NULL REFERENCES merchant_users(email) ON DELETE CASCADE,
        created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
        updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
      );
      CREATE TABLE IF NOT EXISTS customer_push_tokens (
        token TEXT PRIMARY KEY,
        email TEXT NOT NULL REFERENCES customer_users(email) ON DELETE CASCADE,
        created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
        updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
      );
      CREATE INDEX IF NOT EXISTS merchant_push_tokens_email ON merchant_push_tokens (email);
      CREATE TABLE IF NOT EXISTS incoming_payment_push_outbox (
        event_key TEXT PRIMARY KEY,
        payment_id TEXT NOT NULL,
        reference_no TEXT NOT NULL,
        amount NUMERIC(14, 2),
        currency TEXT,
        attempts INTEGER NOT NULL DEFAULT 0,
        available_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
        locked_until TIMESTAMPTZ,
        sent_at TIMESTAMPTZ,
        last_error TEXT,
        created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
      );
      CREATE INDEX IF NOT EXISTS incoming_payment_push_outbox_due
        ON incoming_payment_push_outbox (available_at, created_at)
        WHERE sent_at IS NULL;
      ALTER TABLE swiftpay_webhook_events ADD COLUMN IF NOT EXISTS amount NUMERIC(14, 2);
      ALTER TABLE swiftpay_webhook_events ADD COLUMN IF NOT EXISTS currency TEXT;
      ALTER TABLE incoming_payment_push_outbox ALTER COLUMN amount DROP NOT NULL;
      ALTER TABLE incoming_payment_push_outbox ALTER COLUMN currency DROP NOT NULL;
      CREATE INDEX IF NOT EXISTS swiftpay_webhook_events_payment_id
        ON swiftpay_webhook_events (payment_id)
        WHERE payment_id IS NOT NULL;
      CREATE UNIQUE INDEX IF NOT EXISTS unique_transfer_reference
        ON admin_audit ((details->>'referenceNo'))
        WHERE action LIKE 'transfer.%';
    `);
    if (!Buffer.isBuffer(kybEncryptionKey) || kybEncryptionKey.length !== 32) {
      await pool.query("UPDATE app_feature_settings SET registration_enabled = FALSE WHERE id = 1");
    }

    for (const email of new Set(merchantEmails)) {
      await pool.query(
        `INSERT INTO merchant_users (email, role, status, created_by)
         VALUES ($1, 'merchant', 'active', 'bootstrap')
         ON CONFLICT (email) DO NOTHING`,
        [email]
      );
    }
    for (const email of new Set(superAdminEmails)) {
      await pool.query(
        `INSERT INTO merchant_users (email, role, status, created_by)
         VALUES ($1, 'super_admin', 'active', 'bootstrap')
         ON CONFLICT (email) DO UPDATE
         SET role = 'super_admin', updated_at = NOW()`,
        [email]
      );
    }
  }

  async function getUser(email) {
    const result = await pool.query(
      "SELECT email, role, status, created_by, created_at, updated_at, wallet_mode FROM merchant_users WHERE email = $1",
      [email.toLowerCase()]
    );
    if (result.rows[0]) return result.rows[0];
    const customer = await pool.query(
      `SELECT email, 'customer' AS role, status, 'email_verification' AS created_by,
              created_at, updated_at
       FROM customer_users WHERE email = $1`,
      [email.toLowerCase()]
    );
    return customer.rows[0] || null;
  }

  async function ensureEmailCustomer(email) {
    const result = await pool.query(
      `INSERT INTO customer_users (email)
       SELECT $1
       WHERE NOT EXISTS (SELECT 1 FROM merchant_users WHERE email = $1)
         AND NOT EXISTS (SELECT 1 FROM merchant_registrations WHERE email = $1)
       ON CONFLICT (email) DO UPDATE SET updated_at = customer_users.updated_at
       RETURNING email, 'customer' AS role, status, created_at, updated_at`,
      [email.toLowerCase()]
    );
    return result.rows[0] || null;
  }

  async function ensureSelfServiceMerchant(email) {
    const result = await pool.query(
      `INSERT INTO merchant_users (email, role, status, created_by, wallet_mode)
       SELECT $1, 'merchant', 'active', 'email_verification', 'internal'
       WHERE NOT EXISTS (SELECT 1 FROM customer_users WHERE email = $1)
         AND NOT EXISTS (SELECT 1 FROM merchant_registrations WHERE email = $1)
       ON CONFLICT (email) DO NOTHING
       RETURNING email, role, status, created_by, wallet_mode, created_at, updated_at`,
      [email.toLowerCase()]
    );
    return result.rows[0] || null;
  }

  async function getMerchantWalletBalance(email) {
    const result = await pool.query(
      `SELECT COALESCE(SUM(
         CASE
           WHEN direction = 'credit' AND status = 'EXECUTED' THEN amount
           WHEN direction = 'debit' AND status IN ('PENDING', 'EXECUTED') THEN -amount
           ELSE 0
         END
       ), 0)::text AS balance
       FROM merchant_wallet_transactions
       WHERE email = $1`,
      [email]
    );
    return result.rows[0]?.balance ?? "0";
  }

  async function listMerchantWalletTransactions(email, limit, offset = 0) {
    const result = await pool.query(
      `SELECT COALESCE(payment_id, id) AS id, reference_no, payment_id, amount, currency, status, direction, kind, details, created_at
       FROM merchant_wallet_transactions
       WHERE email = $1
       ORDER BY created_at DESC, id DESC
       LIMIT $2 OFFSET $3`,
      [email, limit, offset]
    );
    return result.rows;
  }

  async function listMerchantWalletDisbursements(email, limit, offset = 0) {
    const result = await pool.query(
      `SELECT id, reference_no, amount, currency, status, created_at
       FROM merchant_wallet_transactions
       WHERE email = $1 AND kind = 'disbursement'
       ORDER BY created_at DESC, id DESC
       LIMIT $2 OFFSET $3`,
      [email, limit, offset]
    );
    return result.rows;
  }

  async function getMerchantWalletPaymentStatus(email, paymentId) {
    const result = await pool.query(
      `SELECT status
       FROM merchant_wallet_transactions
       WHERE email = $1 AND kind = 'collection' AND (payment_id = $2 OR id = $2)`,
      [email, paymentId]
    );
    return result.rows[0] || null;
  }

  async function createMerchantWalletCollection(email, referenceNo, amount, details = {}) {
    const result = await pool.query(
      `INSERT INTO merchant_wallet_transactions
         (id, email, reference_no, kind, direction, amount, status, details)
       VALUES ($1, $2, $3, 'collection', 'credit', $4, 'PENDING', $5::jsonb)
       RETURNING id`,
      [`collection:${email}:${referenceNo}`, email, referenceNo, amount, JSON.stringify(details)]
    );
    return result.rows[0];
  }

  async function recordMerchantWalletCollectionOrder(email, referenceNo, paymentId) {
    const client = await pool.connect();
    try {
      await client.query("BEGIN");
      const result = await client.query(
        `SELECT payment_id FROM merchant_wallet_transactions
         WHERE email = $1 AND reference_no = $2 AND kind = 'collection'
         FOR UPDATE`,
        [email, referenceNo]
      );
      if (!result.rowCount) throw new AdminStoreError(404, "merchant_wallet_collection_not_found");
      if (result.rows[0].payment_id && result.rows[0].payment_id !== paymentId) {
        throw new AdminStoreError(409, "merchant_wallet_payment_mismatch");
      }
      await client.query(
        `UPDATE merchant_wallet_transactions
         SET payment_id = COALESCE(payment_id, $3), updated_at = NOW()
         WHERE email = $1 AND reference_no = $2 AND kind = 'collection'`,
        [email, referenceNo, paymentId]
      );
      await client.query("COMMIT");
    } catch (error) {
      await client.query("ROLLBACK");
      throw error;
    } finally {
      client.release();
    }
  }

  async function finishMerchantWalletCollection(email, referenceNo, status, details = {}) {
    await pool.query(
      `UPDATE merchant_wallet_transactions
       SET status = CASE
             WHEN status IN ('EXECUTED', 'REJECTED', 'CANCELED', 'EXPIRED', 'ERROR') THEN status
             ELSE $2
           END,
           details = details || $3::jsonb,
           updated_at = NOW()
       WHERE id = $1`,
      [`collection:${email}:${referenceNo}`, status, JSON.stringify(details)]
    );
  }

  async function reserveMerchantWalletDisbursement(email, referenceNo, amount) {
    const client = await pool.connect();
    try {
      await client.query("BEGIN");
      const user = await client.query(
        "SELECT status, wallet_mode FROM merchant_users WHERE email = $1 AND role = 'merchant' FOR UPDATE",
        [email]
      );
      if (!user.rowCount || user.rows[0].status !== "active" || user.rows[0].wallet_mode !== "internal") {
        throw new AdminStoreError(401, "unauthorized");
      }
      const balance = await client.query(
        `SELECT COALESCE(SUM(
           CASE
             WHEN direction = 'credit' AND status = 'EXECUTED' THEN amount
             WHEN direction = 'debit' AND status IN ('PENDING', 'EXECUTED') THEN -amount
             ELSE 0
           END
         ), 0) >= $2::numeric AS sufficient
         FROM merchant_wallet_transactions
         WHERE email = $1`,
        [email, amount]
      );
      if (!balance.rows[0]?.sufficient) {
        throw new AdminStoreError(409, "insufficient_wallet_balance");
      }
      await client.query(
        `INSERT INTO merchant_wallet_transactions
           (id, email, reference_no, kind, direction, amount, status)
         VALUES ($1, $2, $3, 'disbursement', 'debit', $4, 'PENDING')`,
        [`merchant-disbursement:${referenceNo}`, email, referenceNo, amount]
      );
      await client.query("COMMIT");
    } catch (error) {
      await client.query("ROLLBACK");
      throw error;
    } finally {
      client.release();
    }
  }

  async function finishMerchantWalletDisbursement(email, referenceNo, status, details = {}) {
    await pool.query(
      `UPDATE merchant_wallet_transactions
       SET status = CASE
             WHEN status = 'EXECUTED' THEN status
             WHEN $3 = 'REJECTED' THEN 'REJECTED'
             ELSE status
           END,
           details = $4::jsonb,
           updated_at = NOW()
       WHERE id = $1 AND email = $2`,
      [`merchant-disbursement:${referenceNo}`, email, status, JSON.stringify(details)]
    );
  }

  async function getCustomerWalletBalance(email) {
    const result = await pool.query(
      `SELECT COALESCE(SUM(
         CASE
           WHEN direction = 'credit' AND status = 'EXECUTED' THEN amount
           WHEN direction = 'debit' AND status IN ('PENDING', 'EXECUTED') THEN -amount
           ELSE 0
         END
       ), 0)::text AS balance
       FROM customer_wallet_transactions
       WHERE email = $1`,
      [email]
    );
    return result.rows[0]?.balance ?? "0";
  }

  async function listCustomerWalletTransactions(email, limit, offset = 0) {
    const result = await pool.query(
      `SELECT COALESCE(payment_id, id) AS id, reference_no, payment_id, amount, currency, status, direction, kind, details, created_at
       FROM customer_wallet_transactions
       WHERE email = $1
       ORDER BY created_at DESC, id DESC
       LIMIT $2 OFFSET $3`,
      [email, limit, offset]
    );
    return result.rows;
  }

  async function listCustomerWalletDisbursements(email, limit, offset = 0) {
    const result = await pool.query(
      `SELECT id, reference_no, amount, currency, status, created_at
       FROM customer_wallet_transactions
       WHERE email = $1 AND kind = 'disbursement'
       ORDER BY created_at DESC, id DESC
       LIMIT $2 OFFSET $3`,
      [email, limit, offset]
    );
    return result.rows;
  }

  async function getCustomerWalletTopup(email, referenceNo) {
    const result = await pool.query(
      `SELECT amount, currency, status, details
       FROM customer_wallet_transactions
       WHERE email = $1 AND reference_no = $2 AND kind = 'topup'`,
      [email, referenceNo]
    );
    return result.rows[0] || null;
  }

  async function getCustomerWalletPaymentStatus(email, paymentId) {
    const result = await pool.query(
      `SELECT status
       FROM customer_wallet_transactions
       WHERE email = $1 AND (payment_id = $2 OR id = $2)`,
      [email, paymentId]
    );
    return result.rows[0] || null;
  }

  async function createCustomerWalletTopup(email, referenceNo, amount, details = {}) {
    const result = await pool.query(
      `INSERT INTO customer_wallet_transactions
         (id, email, reference_no, kind, direction, amount, status, details)
       VALUES ($1, $2, $3, 'topup', 'credit', $4, 'PENDING', $5::jsonb)
       RETURNING id`,
      [`topup:${referenceNo}`, email, referenceNo, amount, JSON.stringify(details)]
    );
    return result.rows[0];
  }

  async function recordCustomerWalletTopupOrder(email, referenceNo, paymentId) {
    const client = await pool.connect();
    try {
      await client.query("BEGIN");
      const result = await client.query(
        `SELECT payment_id FROM customer_wallet_transactions
         WHERE email = $1 AND reference_no = $2 AND kind = 'topup'
         FOR UPDATE`,
        [email, referenceNo]
      );
      if (!result.rowCount) throw new AdminStoreError(404, "wallet_topup_not_found");
      if (result.rows[0].payment_id && result.rows[0].payment_id !== paymentId) {
        throw new AdminStoreError(409, "wallet_topup_payment_mismatch");
      }
      await client.query(
        `UPDATE customer_wallet_transactions
         SET payment_id = COALESCE(payment_id, $3), updated_at = NOW()
         WHERE email = $1 AND reference_no = $2 AND kind = 'topup'`,
        [email, referenceNo, paymentId]
      );
      await client.query("COMMIT");
    } catch (error) {
      await client.query("ROLLBACK");
      throw error;
    } finally {
      client.release();
    }
  }

  async function finishCustomerWalletTopup(email, referenceNo, status, details = {}) {
    await pool.query(
      `UPDATE customer_wallet_transactions
       SET status = CASE
             WHEN status IN ('EXECUTED', 'REJECTED', 'CANCELED', 'EXPIRED', 'ERROR') THEN status
             ELSE $3
           END,
           details = details || $4::jsonb,
           updated_at = NOW()
       WHERE email = $1 AND reference_no = $2 AND kind = 'topup'`,
      [email, referenceNo, status, JSON.stringify(details)]
    );
  }

  async function reserveCustomerWalletDisbursement(email, referenceNo, amount) {
    const client = await pool.connect();
    try {
      await client.query("BEGIN");
      const user = await client.query(
        "SELECT status FROM customer_users WHERE email = $1 FOR UPDATE",
        [email]
      );
      if (!user.rowCount || user.rows[0].status !== "active") {
        throw new AdminStoreError(401, "unauthorized");
      }
      const balance = await client.query(
        `SELECT COALESCE(SUM(
           CASE
             WHEN direction = 'credit' AND status = 'EXECUTED' THEN amount
             WHEN direction = 'debit' AND status IN ('PENDING', 'EXECUTED') THEN -amount
             ELSE 0
           END
         ), 0) >= $2::numeric AS sufficient
         FROM customer_wallet_transactions
         WHERE email = $1`,
        [email, amount]
      );
      if (!balance.rows[0]?.sufficient) {
        throw new AdminStoreError(409, "insufficient_wallet_balance");
      }
      await client.query(
        `INSERT INTO customer_wallet_transactions
           (id, email, reference_no, kind, direction, amount, status)
         VALUES ($1, $2, $3, 'disbursement', 'debit', $4, 'PENDING')`,
        [`disbursement:${email}:${referenceNo}`, email, referenceNo, amount]
      );
      await client.query("COMMIT");
    } catch (error) {
      await client.query("ROLLBACK");
      throw error;
    } finally {
      client.release();
    }
  }

  async function finishCustomerWalletDisbursement(email, referenceNo, status, details = {}) {
    await pool.query(
      `UPDATE customer_wallet_transactions
       SET status = CASE
             WHEN status = 'EXECUTED' THEN status
             WHEN $3 = 'REJECTED' THEN 'REJECTED'
             ELSE status
           END,
           details = $4::jsonb,
           updated_at = NOW()
       WHERE email = $1 AND reference_no = $2 AND kind = 'disbursement'`,
      [email, referenceNo, status, JSON.stringify(details)]
    );
  }

  async function listUsers() {
    const result = await pool.query(
      "SELECT email, role, status, created_by, created_at, updated_at, wallet_mode FROM merchant_users ORDER BY created_at DESC, email"
    );
    return result.rows;
  }

  async function withAudit(actor, action, target, details, write) {
    const client = await pool.connect();
    try {
      await client.query("BEGIN");
      const result = await write(client);
      await client.query(
        "INSERT INTO admin_audit (actor_email, action, target, details) VALUES ($1, $2, $3, $4)",
        [actor, action, target, JSON.stringify(details)]
      );
      await client.query("COMMIT");
      return result;
    } catch (error) {
      await client.query("ROLLBACK");
      throw error;
    } finally {
      client.release();
    }
  }

  async function createMerchant(email, actor) {
    return withAudit(actor, "merchant.created", email, {}, async (client) => {
      const result = await client.query(
        `INSERT INTO merchant_users (email, role, status, created_by, onboarding_eligible, wallet_mode)
         VALUES ($1, 'merchant', 'active', $2, TRUE, 'internal')
         ON CONFLICT (email) DO UPDATE
         SET status = 'active', updated_at = NOW()
         WHERE merchant_users.role = 'merchant'
         RETURNING email, role, status, created_by, created_at, updated_at, wallet_mode`,
        [email, actor]
      );
      if (!result.rowCount) throw new AdminStoreError(409, "Super-admin accounts cannot be changed as merchant users");
      return result.rows[0];
    });
  }

  async function setMerchantStatus(email, status, actor) {
    return withAudit(actor, `merchant.${status}`, email, {}, async (client) => {
      const result = await client.query(
        `UPDATE merchant_users
         SET status = $2, updated_at = NOW()
         WHERE email = $1 AND role = 'merchant'
         RETURNING email, role, status, created_by, created_at, updated_at`,
        [email, status]
      );
      if (!result.rowCount) throw new AdminStoreError(404, "Merchant user not found");
      return result.rows[0];
    });
  }

  async function getOnboardingSettings() {
    const result = await pool.query(
      "SELECT onboarding_enabled AS enabled FROM app_feature_settings WHERE id = 1"
    );
    if (!result.rows[0]) throw new Error("Onboarding settings are not initialized");
    return result.rows[0];
  }

  async function setOnboardingEnabled(enabled, actor) {
    const action = enabled ? "onboarding.enabled" : "onboarding.disabled";
    return withAudit(actor, action, "all-merchants", { enabled }, async (client) => {
      const result = await client.query(
        `UPDATE app_feature_settings
         SET onboarding_enabled = $1, updated_by = $2, updated_at = NOW()
         WHERE id = 1
         RETURNING onboarding_enabled AS enabled`,
        [enabled, actor]
      );
      if (!result.rowCount) throw new Error("Onboarding settings are not initialized");
      return result.rows[0];
    });
  }

  async function getMerchantOnboardingState(email) {
    const result = await pool.query(
      `SELECT (
                settings.onboarding_enabled
                AND
                users.role = 'merchant'
                AND users.onboarding_eligible
                AND users.onboarding_completed_at IS NULL
              ) AS required
       FROM app_feature_settings settings
       JOIN merchant_users users ON users.email = $1
       WHERE settings.id = 1`,
      [email.toLowerCase()]
    );
    if (!result.rows[0]) throw new Error("Merchant onboarding state is unavailable");
    return result.rows[0];
  }

  async function completeMerchantOnboarding(email) {
    const result = await pool.query(
      `UPDATE merchant_users
       SET onboarding_completed_at = NOW()
       WHERE email = $1
         AND role = 'merchant'
         AND onboarding_eligible = TRUE
         AND onboarding_completed_at IS NULL`,
      [email.toLowerCase()]
    );
    if (result.rowCount) return true;

    const existing = await pool.query(
      `SELECT onboarding_eligible AND onboarding_completed_at IS NOT NULL AS completed
       FROM merchant_users
       WHERE email = $1 AND role = 'merchant'`,
      [email.toLowerCase()]
    );
    return existing.rows[0]?.completed === true;
  }

  function requireKybKey() {
    if (!Buffer.isBuffer(kybEncryptionKey) || kybEncryptionKey.length !== 32) {
      throw new AdminStoreError(503, "Merchant registration is not configured for secure document storage");
    }
  }

  async function getRegistrationSettings() {
    const result = await pool.query(
      "SELECT registration_enabled AS enabled FROM app_feature_settings WHERE id = 1"
    );
    if (!result.rows[0]) throw new Error("Registration settings are not initialized");
    return { ...result.rows[0], secureStorageAvailable: Buffer.isBuffer(kybEncryptionKey) && kybEncryptionKey.length === 32 };
  }

  async function setRegistrationEnabled(enabled, actor) {
    if (enabled) requireKybKey();
    const action = enabled ? "registration.enabled" : "registration.disabled";
    return withAudit(actor, action, "public-registration", { enabled }, async (client) => {
      const result = await client.query(
        `UPDATE app_feature_settings
         SET registration_enabled = $1, updated_by = $2, updated_at = NOW()
         WHERE id = 1
         RETURNING registration_enabled AS enabled`,
        [enabled, actor]
      );
      if (!result.rowCount) throw new Error("Registration settings are not initialized");
      return result.rows[0];
    });
  }

  async function getRegistration(email) {
    const result = await pool.query(
      "SELECT email, status FROM merchant_registrations WHERE email = $1",
      [email.toLowerCase()]
    );
    return result.rows[0] || null;
  }

  async function createRegistrationDraft(email) {
    requireKybKey();
    const normalizedEmail = email.toLowerCase();
    const merchant = await getUser(normalizedEmail);
    if (merchant) throw new AdminStoreError(409, "This email already has a merchant account");
    const result = await pool.query(
      `INSERT INTO merchant_registrations (email, status, encrypted_profile)
       VALUES ($1, 'draft', $2)
       ON CONFLICT (email) DO UPDATE
         SET status = 'draft', generation = merchant_registrations.generation + 1,
             reviewed_by = NULL, reviewed_at = NULL, updated_at = NOW()
         WHERE merchant_registrations.status = 'rejected'
       RETURNING email, status`,
      [
        normalizedEmail,
        encryptKyb(Buffer.from("{}", "utf8"), kybEncryptionKey, `profile:${normalizedEmail}`).toString("base64")
      ]
    );
    if (result.rowCount) return result.rows[0];
    const existing = await getRegistration(normalizedEmail);
    if (existing) return existing;
    throw new AdminStoreError(409, "This email already has a merchant account");
  }

  async function getRegistrationStatus(email) {
    const result = await pool.query(
      `SELECT email, status, updated_at AS "updatedAt"
       FROM merchant_registrations WHERE email = $1`,
      [email.toLowerCase()]
    );
    return result.rows[0] || null;
  }

  async function getRegistrationApplication(email) {
    requireKybKey();
    const normalizedEmail = email.toLowerCase();
    const result = await pool.query(
      `SELECT email, status, encrypted_profile, encrypted_review_note, updated_at AS "updatedAt",
              generation
       FROM merchant_registrations WHERE email = $1`,
      [normalizedEmail]
    );
    const registration = result.rows[0];
    if (!registration) return null;
    const documents = await pool.query(
      `SELECT kind FROM merchant_registration_documents
       WHERE email = $1 AND generation = $2 ORDER BY kind`,
      [normalizedEmail, registration.generation]
    );
    const profile = JSON.parse(decryptKyb(
      Buffer.from(registration.encrypted_profile, "base64"),
      kybEncryptionKey,
      `profile:${normalizedEmail}`
    ).toString("utf8"));
    const reviewNote = registration.encrypted_review_note
      ? decryptKyb(
        Buffer.from(registration.encrypted_review_note, "base64"),
        kybEncryptionKey,
        `review:${normalizedEmail}`
      ).toString("utf8")
      : "";
    delete registration.encrypted_profile;
    delete registration.encrypted_review_note;
    delete registration.generation;
    return { ...registration, reviewNote, profile, documents: documents.rows.map((document) => document.kind) };
  }

  async function saveRegistrationProfile(email, profile) {
    requireKybKey();
    const result = await pool.query(
      `UPDATE merchant_registrations
       SET encrypted_profile = $2, updated_at = NOW()
       WHERE email = $1 AND status IN ('draft', 'rejected')
       RETURNING status`,
      [
        email.toLowerCase(),
        encryptKyb(
          Buffer.from(JSON.stringify(profile), "utf8"),
          kybEncryptionKey,
          `profile:${email.toLowerCase()}`
        ).toString("base64")
      ]
    );
    if (!result.rowCount) throw new AdminStoreError(409, "The application is not editable");
    return result.rows[0];
  }

  async function saveRegistrationDocument(email, kind, contentType, data) {
    requireKybKey();
    const result = await pool.query(
      `INSERT INTO merchant_registration_documents (email, kind, content_type, encrypted_data, generation)
       SELECT email, $2, $3, $4, generation FROM merchant_registrations
       WHERE email = $1 AND status IN ('draft', 'rejected')
       ON CONFLICT (email, kind) DO UPDATE
         SET content_type = EXCLUDED.content_type, encrypted_data = EXCLUDED.encrypted_data,
             generation = EXCLUDED.generation, created_at = NOW()
       RETURNING kind`,
      [
        email.toLowerCase(),
        kind,
        contentType,
        encryptKyb(data, kybEncryptionKey, `document:${email.toLowerCase()}:${kind}`).toString("base64")
      ]
    );
    if (!result.rowCount) throw new AdminStoreError(409, "The application is not editable");
    return result.rows[0];
  }

  async function submitRegistration(email) {
    requireKybKey();
    const normalizedEmail = email.toLowerCase();
    const count = await pool.query(
      `SELECT COUNT(*)::integer AS count
       FROM merchant_registration_documents documents
       JOIN merchant_registrations registrations ON registrations.email = documents.email
       WHERE documents.email = $1
         AND documents.kind IN ('business_certificate', 'representative_id', 'selfie')
         AND documents.generation = registrations.generation`,
      [normalizedEmail]
    );
    if (count.rows[0]?.count !== 3) {
      throw new AdminStoreError(400, "Upload the business certificate, representative ID, and selfie before submitting");
    }
    const result = await pool.query(
      `UPDATE merchant_registrations
       SET status = 'submitted', encrypted_review_note = $2, updated_at = NOW()
       WHERE email = $1 AND status IN ('draft', 'rejected')
       RETURNING email, status, updated_at AS "updatedAt"`,
      [
        normalizedEmail,
        encryptKyb(Buffer.from("", "utf8"), kybEncryptionKey, `review:${normalizedEmail}`).toString("base64")
      ]
    );
    if (!result.rowCount) throw new AdminStoreError(409, "The application cannot be submitted in its current state");
    return result.rows[0];
  }

  async function listRegistrations() {
    const result = await pool.query(
      `SELECT email, status, created_at AS "createdAt",
              updated_at AS "updatedAt", reviewed_by AS "reviewedBy", reviewed_at AS "reviewedAt"
       FROM merchant_registrations ORDER BY created_at DESC LIMIT 200`
    );
    return result.rows;
  }

  async function getRegistrationDetails(email) {
    requireKybKey();
    const result = await pool.query(
      `SELECT email, status, encrypted_profile, encrypted_review_note, created_at AS "createdAt",
              updated_at AS "updatedAt", reviewed_by AS "reviewedBy", reviewed_at AS "reviewedAt"
       FROM merchant_registrations WHERE email = $1`,
      [email.toLowerCase()]
    );
    const registration = result.rows[0];
    if (!registration) throw new AdminStoreError(404, "Registration not found");
    const documents = await pool.query(
      `SELECT kind, content_type AS "contentType", created_at AS "uploadedAt"
       FROM merchant_registration_documents WHERE email = $1 ORDER BY kind`,
      [email.toLowerCase()]
    );
    const profile = JSON.parse(decryptKyb(
      Buffer.from(registration.encrypted_profile, "base64"),
      kybEncryptionKey,
      `profile:${email.toLowerCase()}`
    ).toString("utf8"));
    const reviewNote = registration.encrypted_review_note
      ? decryptKyb(
        Buffer.from(registration.encrypted_review_note, "base64"),
        kybEncryptionKey,
        `review:${email.toLowerCase()}`
      ).toString("utf8")
      : "";
    delete registration.encrypted_profile;
    delete registration.encrypted_review_note;
    return { ...registration, reviewNote, profile, documents: documents.rows };
  }

  async function getRegistrationDocument(email, kind) {
    requireKybKey();
    const result = await pool.query(
      `SELECT content_type AS "contentType", encrypted_data
       FROM merchant_registration_documents WHERE email = $1 AND kind = $2`,
      [email.toLowerCase(), kind]
    );
    if (!result.rows[0]) throw new AdminStoreError(404, "Document not found");
    return {
      contentType: result.rows[0].contentType,
      data: decryptKyb(
        Buffer.from(result.rows[0].encrypted_data, "base64"),
        kybEncryptionKey,
        `document:${email.toLowerCase()}:${kind}`
      )
    };
  }

  async function reviewRegistration(email, status, note, actor) {
    const normalizedEmail = email.toLowerCase();
    return withAudit(actor, `registration.${status}`, normalizedEmail, {}, async (client) => {
      const registration = await client.query(
        `SELECT status FROM merchant_registrations WHERE email = $1 FOR UPDATE`,
        [normalizedEmail]
      );
      if (!registration.rowCount) throw new AdminStoreError(404, "Registration not found");
      if (registration.rows[0].status !== "submitted") {
        throw new AdminStoreError(409, "Only submitted applications can be reviewed");
      }
      if (status === "approved") {
        const customer = await client.query(
          "SELECT email FROM customer_users WHERE email = $1 FOR UPDATE",
          [normalizedEmail]
        );
        if (customer.rowCount) {
          throw new AdminStoreError(409, "A personal-wallet account cannot be approved as a merchant");
        }
        await client.query(
          `INSERT INTO merchant_users (email, role, status, created_by, onboarding_eligible, wallet_mode)
           VALUES ($1, 'merchant', 'active', $2, TRUE, 'internal')
           ON CONFLICT (email) DO UPDATE SET status = 'active', wallet_mode = 'internal', updated_at = NOW()
           WHERE merchant_users.role = 'merchant'`,
          [normalizedEmail, actor]
        );
        const merchant = await client.query(
          "SELECT role FROM merchant_users WHERE email = $1",
          [normalizedEmail]
        );
        if (merchant.rows[0]?.role !== "merchant") {
          throw new AdminStoreError(409, "A super-admin account cannot be approved as a merchant");
        }
      }
      const updated = await client.query(
        `UPDATE merchant_registrations
         SET status = $2, encrypted_review_note = $3, reviewed_by = $4, reviewed_at = NOW(), updated_at = NOW()
         WHERE email = $1
         RETURNING email, status, updated_at AS "updatedAt"`,
        [
          normalizedEmail,
          status,
          encryptKyb(Buffer.from(note, "utf8"), kybEncryptionKey, `review:${normalizedEmail}`).toString("base64"),
          actor
        ]
      );
      return { ...updated.rows[0], reviewNote: note };
    });
  }

  async function deleteRegistration(email, actor) {
    const normalizedEmail = email.toLowerCase();
    return withAudit(actor, "registration.deleted", normalizedEmail, {}, async (client) => {
      const result = await client.query(
        "DELETE FROM merchant_registrations WHERE email = $1 AND status IN ('approved', 'rejected') RETURNING email",
        [normalizedEmail]
      );
      if (!result.rowCount) throw new AdminStoreError(409, "Reject or approve the application before permanently deleting its documents");
      return { deleted: true };
    });
  }

  async function listAudit(limit) {
    const result = await pool.query(
      "SELECT id, actor_email AS actor, action, target, details, created_at FROM admin_audit ORDER BY id DESC LIMIT $1",
      [limit]
    );
    return result.rows;
  }

  async function createAdjustment(amount, reason, actor) {
    return withAudit(actor, "balance.adjusted", "shared-swiftpay-account", { amount, currency: "PHP", reason }, async (client) => {
      const result = await client.query(
        `INSERT INTO balance_adjustments (amount, reason, actor_email)
         VALUES ($1, $2, $3)
         RETURNING id, amount, currency, reason, actor_email AS actor, created_at`,
        [amount, reason, actor]
      );
      return result.rows[0];
    });
  }

  async function createManualBankDeposit({ id, amount, bankName, referenceNo, notes }, actor) {
    return withAudit(
      actor,
      "manual_bank_deposit.recorded",
      referenceNo,
      { amount, currency: "PHP", bankName, referenceNo },
      async (client) => {
        const result = await client.query(
          `INSERT INTO manual_bank_deposits (id, amount, bank_name, reference_no, notes, actor_email)
           VALUES ($1, $2, $3, $4, $5, $6)
           RETURNING id, amount, bank_name, reference_no, notes, actor_email AS actor, created_at`,
          [id, amount, bankName, referenceNo, notes, actor]
        );
        return result.rows[0];
      }
    );
  }

  async function listManualBankDeposits(limit, offset = 0) {
    const result = await pool.query(
      `SELECT id, amount, bank_name, reference_no, notes, created_at
       FROM manual_bank_deposits ORDER BY created_at DESC, id DESC LIMIT $1 OFFSET $2`,
      [limit, offset]
    );
    return result.rows;
  }

  async function createTransferIntent(actor, referenceNo, details) {
    try {
      const result = await pool.query(
        `INSERT INTO admin_audit (actor_email, action, target, details)
         VALUES ($1, 'transfer.pending', $2, $3)
         RETURNING id`,
        [actor, referenceNo, JSON.stringify(details)]
      );
      return result.rows[0].id;
    } catch (error) {
      if (error.code === "23505") {
        throw new AdminStoreError(409, "This transfer reference has already been used");
      }
      throw error;
    }
  }

  async function finishTransferIntent(id, outcome, target, details) {
    const existing = await pool.query("SELECT details FROM admin_audit WHERE id = $1", [id]);
    if (!existing.rowCount) throw new Error("Transfer audit intent not found");
    await pool.query(
      `UPDATE admin_audit
       SET action = $2, target = $3, details = $4
       WHERE id = $1`,
      [id, `transfer.${outcome}`, target, JSON.stringify({ ...existing.rows[0].details, ...details })]
    );
  }

  async function recordWebhookEvent(event) {
    const client = await pool.connect();
    try {
      await client.query("BEGIN");
      const inserted = await client.query(
        `INSERT INTO swiftpay_webhook_events
           (event_key, event_type, payment_id, reference_no, status, details, amount, currency)
         VALUES ($1, $2, $3, $4, $5, '{}', $6, $7)
         ON CONFLICT (event_key) DO NOTHING
         RETURNING event_key`,
        [
          event.eventKey,
          event.eventType,
          event.paymentId || null,
          event.referenceNo,
          event.status,
          event.amount ?? event.details?.amount ?? null,
          event.currency ?? event.details?.currency ?? null
        ]
      );
      const existing = await client.query(
        "SELECT details, status FROM swiftpay_webhook_events WHERE event_key = $1 FOR UPDATE",
        [event.eventKey]
      );
      const previousStatus = inserted.rows.length > 0 ? null : existing.rows[0].status;
      const details = { ...existing.rows[0].details, ...event.details };
      const updatedEvent = await client.query(
        `UPDATE swiftpay_webhook_events
         SET payment_id = COALESCE($2, payment_id),
           status = CASE
             WHEN status IN ('EXECUTED', 'REJECTED', 'CANCELED', 'EXPIRED', 'ERROR') THEN status
             ELSE $3
           END,
           details = $4,
           amount = COALESCE($5, amount),
           currency = COALESCE($6, currency),
           received_at = NOW()
         WHERE event_key = $1
         RETURNING status`,
        [
          event.eventKey,
          event.paymentId || null,
          event.status,
          JSON.stringify(details),
          event.amount ?? event.details?.amount ?? null,
          event.currency ?? event.details?.currency ?? null
        ]
      );
      const effectiveStatus = updatedEvent.rows[0].status;
      const amount = event.amount ?? event.details?.amount ?? details.amount;
      const currency = event.currency ?? event.details?.currency ?? details.currency;
      if (event.eventType === "payment") {
        const topup = await client.query(
          `SELECT id, email, payment_id, amount, status
           FROM customer_wallet_transactions
           WHERE kind = 'topup' AND reference_no = $1
           FOR UPDATE`,
          [event.referenceNo]
        );
        if (topup.rowCount) {
          const transaction = topup.rows[0];
          const paymentMatches = !transaction.payment_id || !event.paymentId || transaction.payment_id === event.paymentId;
          const amountMatches = amount === undefined || amount === null ||
            Math.round(Number(amount) * 100) === Math.round(Number(transaction.amount) * 100);
          const currencyMatches = currency === undefined || currency === null || currency === "PHP";
          if (paymentMatches && amountMatches && currencyMatches) {
            await client.query(
              `UPDATE customer_wallet_transactions
               SET payment_id = COALESCE(payment_id, $2),
                   status = CASE
                     WHEN status IN ('EXECUTED', 'REJECTED', 'CANCELED', 'EXPIRED', 'ERROR') THEN status
                     ELSE $3
                   END,
                   updated_at = NOW()
               WHERE id = $1`,
              [transaction.id, event.paymentId || null, effectiveStatus]
            );
          } else if (transaction.status === "PENDING") {
            await client.query(
              `UPDATE customer_wallet_transactions
               SET status = 'ERROR',
                   details = '{"settlementMismatch":true}'::jsonb,
                   updated_at = NOW()
               WHERE id = $1`,
              [transaction.id]
            );
          }
        }
        if (event.verifiedWebhook) {
          const collection = await client.query(
            `SELECT id, payment_id, amount, status, details
             FROM merchant_wallet_transactions
             WHERE kind = 'collection' AND reference_no = $1
             FOR UPDATE`,
            [event.referenceNo]
          );
          if (collection.rowCount) {
            const transaction = collection.rows[0];
            const paymentMatches = Boolean(event.paymentId) &&
              (!transaction.payment_id || transaction.payment_id === event.paymentId);
            const amountMatches = amount !== undefined && amount !== null &&
              Number.isFinite(Number(amount)) &&
              Math.round(Number(amount) * 100) === Math.round(Number(transaction.amount) * 100);
            const currencyMatches = currency === "PHP";
            if (paymentMatches && amountMatches && currencyMatches) {
              await client.query(
                `UPDATE merchant_wallet_transactions
                 SET payment_id = COALESCE(payment_id, $2),
                     status = CASE
                       WHEN status IN ('EXECUTED', 'REJECTED', 'CANCELED', 'EXPIRED', 'ERROR') THEN status
                       ELSE $3
                     END,
                     updated_at = NOW()
                 WHERE id = $1`,
                [transaction.id, event.paymentId, effectiveStatus]
              );
            } else if (transaction.status === "PENDING") {
              await client.query(
                `UPDATE merchant_wallet_transactions
                 SET status = 'ERROR',
                     details = $2::jsonb,
                     updated_at = NOW()
                 WHERE id = $1`,
                [transaction.id, JSON.stringify({ ...transaction.details, settlementMismatch: true })]
              );
            }
          }
        }
      } else if (event.eventType === "disbursement") {
        const reservationStatus = effectiveStatus === "EXECUTED"
          ? "EXECUTED"
          : effectiveStatus === "REJECTED" ? "REJECTED" : "PENDING";
        await client.query(
          `UPDATE customer_wallet_transactions
           SET status = CASE
                 WHEN status = 'EXECUTED' THEN status
                 WHEN $2 = 'REJECTED' THEN 'REJECTED'
                 WHEN $2 = 'EXECUTED' THEN 'EXECUTED'
                 ELSE status
               END,
               details = $3::jsonb,
               updated_at = NOW()
           WHERE kind = 'disbursement' AND reference_no = $1`,
          [event.referenceNo, reservationStatus, JSON.stringify(event.details || {})]
        );
        if (event.verifiedWebhook) {
          await client.query(
            `UPDATE merchant_wallet_transactions
             SET status = CASE
                   WHEN status = 'EXECUTED' THEN status
                   WHEN $2 = 'REJECTED' THEN 'REJECTED'
                   WHEN $2 = 'EXECUTED' THEN 'EXECUTED'
                   ELSE status
                 END,
                 details = $3::jsonb,
                 updated_at = NOW()
             WHERE id = $1 AND kind = 'disbursement'`,
            [`merchant-disbursement:${event.referenceNo}`, reservationStatus, JSON.stringify(event.details || {})]
          );
        }
      }
      if (
        event.notifyIncoming &&
        event.eventType === "payment" &&
        effectiveStatus === "EXECUTED" &&
        previousStatus !== "EXECUTED" &&
        event.paymentId
      ) {
        await client.query(
          `INSERT INTO incoming_payment_push_outbox
             (event_key, payment_id, reference_no, amount, currency)
           VALUES ($1, $2, $3, $4, $5)
           ON CONFLICT (event_key) DO NOTHING`,
          [`incoming:${event.paymentId}`, event.paymentId, event.referenceNo, amount, currency]
        );
      }
      await client.query("COMMIT");
    } catch (error) {
      await client.query("ROLLBACK");
      throw error;
    } finally {
      client.release();
    }
  }

  async function upsertPushToken(email, token) {
    const user = await getUser(email);
    if (!user || user.status !== "active") throw new AdminStoreError(401, "unauthorized");
    if (user.role === "customer") {
      await pool.query(
        `INSERT INTO customer_push_tokens (token, email)
         VALUES ($1, $2)
         ON CONFLICT (token) DO UPDATE
         SET email = EXCLUDED.email, updated_at = NOW()`,
        [token, email.toLowerCase()]
      );
      return;
    }
    await pool.query(
      `INSERT INTO merchant_push_tokens (token, email)
       VALUES ($1, $2)
       ON CONFLICT (token) DO UPDATE
       SET email = EXCLUDED.email, updated_at = NOW()`,
      [token, email.toLowerCase()]
    );
  }

  async function deletePushToken(email, token) {
    const user = await getUser(email.toLowerCase());
    if (user?.role === "customer") {
      await pool.query(
        "DELETE FROM customer_push_tokens WHERE email = $1 AND token = $2",
        [email.toLowerCase(), token]
      );
      return;
    }
    await pool.query(
      "DELETE FROM merchant_push_tokens WHERE email = $1 AND token = $2",
      [email.toLowerCase(), token]
    );
  }

  async function listActivePushTokens() {
    const result = await pool.query(
      `SELECT token FROM merchant_push_tokens t
       JOIN merchant_users u ON u.email = t.email
       WHERE u.status = 'active'
       ORDER BY t.created_at`
    );
    return result.rows.map((row) => row.token);
  }

  async function removePushToken(token) {
    await pool.query(
      "DELETE FROM merchant_push_tokens WHERE token = $1",
      [token]
    );
    await pool.query("DELETE FROM customer_push_tokens WHERE token = $1", [token]);
  }

  async function claimIncomingPaymentPushes(limit = 10) {
    const client = await pool.connect();
    try {
      await client.query("BEGIN");
      const due = await client.query(
        `SELECT event_key
         FROM incoming_payment_push_outbox
         WHERE sent_at IS NULL
           AND available_at <= NOW()
           AND (locked_until IS NULL OR locked_until < NOW())
         ORDER BY created_at
         LIMIT $1
         FOR UPDATE`,
        [limit]
      );
      const claimed = [];
      for (const row of due.rows) {
        const result = await client.query(
          `UPDATE incoming_payment_push_outbox
           SET locked_until = NOW() + INTERVAL '90 seconds',
               attempts = attempts + 1
           WHERE event_key = $1
           RETURNING event_key, payment_id, reference_no, amount, currency, attempts`,
          [row.event_key]
        );
        if (result.rows[0]) claimed.push(result.rows[0]);
      }
      await client.query("COMMIT");
      return claimed;
    } catch (error) {
      await client.query("ROLLBACK");
      throw error;
    } finally {
      client.release();
    }
  }

  async function completeIncomingPaymentPush(eventKey) {
    await pool.query(
      `UPDATE incoming_payment_push_outbox
       SET sent_at = NOW(), locked_until = NULL, last_error = NULL
       WHERE event_key = $1`,
      [eventKey]
    );
  }

  async function retryIncomingPaymentPush(eventKey, attempts) {
    const delaySeconds = Math.min(3600, 15 * (2 ** Math.min(Math.max(attempts - 1, 0), 8)));
    await pool.query(
      `UPDATE incoming_payment_push_outbox
       SET available_at = NOW() + ($2 * INTERVAL '1 second'),
           locked_until = NULL,
           last_error = 'delivery_failed'
       WHERE event_key = $1`,
      [eventKey, delaySeconds]
    );
  }

  async function getPaymentWebhookStatus(paymentId) {
    const result = await pool.query(
      `SELECT status FROM swiftpay_webhook_events
       WHERE event_type = 'payment' AND payment_id = $1
       LIMIT 1`,
      [paymentId]
    );
    return result.rows[0] || null;
  }

  async function listPaymentWebhookTransactions(limit, offset = 0) {
    const result = await pool.query(
      `SELECT payment_id AS id, reference_no, amount, currency, status, received_at, event_key
       FROM (
         SELECT DISTINCT ON (payment_id)
           payment_id, reference_no, amount, currency, status, received_at, event_key
         FROM swiftpay_webhook_events
         WHERE event_type = 'payment'
           AND payment_id IS NOT NULL
           AND amount IS NOT NULL
           AND currency = 'PHP'
         ORDER BY payment_id, received_at DESC, event_key DESC
       ) latest
       ORDER BY received_at DESC, payment_id DESC
       LIMIT $1 OFFSET $2`,
      [limit, offset]
    );
    return result.rows;
  }

  async function getKrwQrPaymentBalance() {
    const result = await pool.query(
      `SELECT COALESCE(SUM(amount), 0)::text AS balance,
              COUNT(*)::text AS payment_count
       FROM swiftpay_webhook_events
       WHERE event_type = 'payment'
         AND status = 'EXECUTED'
        AND currency = 'KRW'
        AND amount IS NOT NULL`
    );
    return {
      balance: result.rows[0]?.balance ?? "0",
      paymentCount: Number(result.rows[0]?.payment_count ?? 0)
    };
  }

  async function listAdjustments(limit) {
    const result = await pool.query(
      `SELECT id, amount, currency, reason, actor_email AS actor, created_at
       FROM balance_adjustments ORDER BY id DESC LIMIT $1`,
      [limit]
    );
    return result.rows;
  }

  async function adjustmentTotal() {
    const result = await pool.query(
      "SELECT COALESCE(SUM(amount), 0)::text AS total FROM balance_adjustments"
    );
    return result.rows[0].total;
  }

  async function close() {
    await pool.end();
  }

  return {
    initialize,
    getUser,
    ensureEmailCustomer,
    ensureSelfServiceMerchant,
    getCustomerWalletBalance,
    listCustomerWalletTransactions,
    listCustomerWalletDisbursements,
    getCustomerWalletTopup,
    getCustomerWalletPaymentStatus,
    createCustomerWalletTopup,
    recordCustomerWalletTopupOrder,
    finishCustomerWalletTopup,
    reserveCustomerWalletDisbursement,
    finishCustomerWalletDisbursement,
    getMerchantWalletBalance,
    listMerchantWalletTransactions,
    listMerchantWalletDisbursements,
    getMerchantWalletPaymentStatus,
    createMerchantWalletCollection,
    recordMerchantWalletCollectionOrder,
    finishMerchantWalletCollection,
    reserveMerchantWalletDisbursement,
    finishMerchantWalletDisbursement,
    listUsers,
    createMerchant,
    setMerchantStatus,
    getOnboardingSettings,
    setOnboardingEnabled,
    getRegistrationSettings,
    setRegistrationEnabled,
    getMerchantOnboardingState,
    completeMerchantOnboarding,
    getRegistration,
    createRegistrationDraft,
    getRegistrationStatus,
    getRegistrationApplication,
    saveRegistrationProfile,
    saveRegistrationDocument,
    submitRegistration,
    listRegistrations,
    getRegistrationDetails,
    getRegistrationDocument,
    reviewRegistration,
    deleteRegistration,
    listAudit,
    createAdjustment,
    createManualBankDeposit,
    listManualBankDeposits,
    createTransferIntent,
    finishTransferIntent,
    recordWebhookEvent,
    upsertPushToken,
    deletePushToken,
    listActivePushTokens,
    removePushToken,
    claimIncomingPaymentPushes,
    completeIncomingPaymentPush,
    retryIncomingPaymentPush,
    getPaymentWebhookStatus,
    listPaymentWebhookTransactions,
    getKrwQrPaymentBalance,
    listAdjustments,
    adjustmentTotal,
    recordAudit: (actor, action, target, details) =>
      withAudit(actor, action, target, details, async () => null),
    close
  };
}
