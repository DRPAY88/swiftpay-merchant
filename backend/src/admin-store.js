import pg from "pg";

const { Pool } = pg;

export class AdminStoreError extends Error {
  constructor(status, message) {
    super(message);
    this.status = status;
  }
}

export function createAdminStore(databaseUrl, pool = new Pool({ connectionString: databaseUrl, max: 5 })) {
  async function initialize(merchantEmails, superAdminEmails) {
    await pool.query(`
      CREATE TABLE IF NOT EXISTS merchant_users (
        email TEXT PRIMARY KEY,
        role TEXT NOT NULL CHECK (role IN ('merchant', 'super_admin')),
        status TEXT NOT NULL CHECK (status IN ('active', 'suspended', 'removed')),
        created_by TEXT NOT NULL,
        created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
        updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
      );
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
      CREATE TABLE IF NOT EXISTS swiftpay_webhook_events (
        event_key TEXT PRIMARY KEY,
        event_type TEXT NOT NULL CHECK (event_type IN ('payment', 'disbursement')),
        payment_id TEXT,
        reference_no TEXT NOT NULL,
        status TEXT NOT NULL,
        details JSONB NOT NULL DEFAULT '{}'::jsonb,
        received_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
      );
      CREATE INDEX IF NOT EXISTS swiftpay_webhook_events_payment_id
        ON swiftpay_webhook_events (payment_id)
        WHERE payment_id IS NOT NULL;
      CREATE UNIQUE INDEX IF NOT EXISTS unique_transfer_reference
        ON admin_audit ((details->>'referenceNo'))
        WHERE action LIKE 'transfer.%';
    `);

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
      "SELECT email, role, status, created_by, created_at, updated_at FROM merchant_users WHERE email = $1",
      [email.toLowerCase()]
    );
    return result.rows[0] || null;
  }

  async function listUsers() {
    const result = await pool.query(
      "SELECT email, role, status, created_by, created_at, updated_at FROM merchant_users ORDER BY created_at DESC, email"
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
        `INSERT INTO merchant_users (email, role, status, created_by)
         VALUES ($1, 'merchant', 'active', $2)
         ON CONFLICT (email) DO UPDATE
         SET status = 'active', updated_at = NOW()
         WHERE merchant_users.role = 'merchant'
         RETURNING email, role, status, created_by, created_at, updated_at`,
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
    await pool.query(
      `INSERT INTO swiftpay_webhook_events
         (event_key, event_type, payment_id, reference_no, status, details)
       VALUES ($1, $2, $3, $4, $5, $6)
       ON CONFLICT (event_key) DO UPDATE
       SET status = EXCLUDED.status, details = EXCLUDED.details, received_at = NOW()`,
      [
        event.eventKey,
        event.eventType,
        event.paymentId || null,
        event.referenceNo,
        event.status,
        JSON.stringify(event.details)
      ]
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
    listUsers,
    createMerchant,
    setMerchantStatus,
    listAudit,
    createAdjustment,
    createTransferIntent,
    finishTransferIntent,
    recordWebhookEvent,
    getPaymentWebhookStatus,
    listAdjustments,
    adjustmentTotal,
    recordAudit: (actor, action, target, details) =>
      withAudit(actor, action, target, details, async () => null),
    close
  };
}
