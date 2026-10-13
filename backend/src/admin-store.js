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

  async function listManualBankDeposits(limit) {
    const result = await pool.query(
      `SELECT id, amount, bank_name, reference_no, notes, created_at
       FROM manual_bank_deposits ORDER BY created_at DESC, id DESC LIMIT $1`,
      [limit]
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
      await client.query(
        `UPDATE swiftpay_webhook_events
         SET payment_id = COALESCE($2, payment_id),
           status = $3,
           details = $4,
           amount = COALESCE($5, amount),
           currency = COALESCE($6, currency),
           received_at = NOW()
         WHERE event_key = $1`,
        [
          event.eventKey,
          event.paymentId || null,
          event.status,
          JSON.stringify(details),
          event.amount ?? event.details?.amount ?? null,
          event.currency ?? event.details?.currency ?? null
        ]
      );
      const amount = event.amount ?? event.details?.amount ?? details.amount;
      const currency = event.currency ?? event.details?.currency ?? details.currency;
      if (
        event.notifyIncoming &&
        event.eventType === "payment" &&
        event.status === "EXECUTED" &&
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
    await pool.query(
      `INSERT INTO merchant_push_tokens (token, email)
       VALUES ($1, $2)
       ON CONFLICT (token) DO UPDATE
       SET email = EXCLUDED.email, updated_at = NOW()`,
      [token, email.toLowerCase()]
    );
  }

  async function deletePushToken(email, token) {
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
    await pool.query("DELETE FROM merchant_push_tokens WHERE token = $1", [token]);
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

  async function listPaymentWebhookTransactions(limit) {
    const result = await pool.query(
      `SELECT payment_id AS id, reference_no, amount, currency, status, received_at
       FROM swiftpay_webhook_events
       WHERE event_type = 'payment'
         AND payment_id IS NOT NULL
         AND amount IS NOT NULL
         AND currency = 'PHP'
       ORDER BY received_at DESC, payment_id DESC
       LIMIT $1`,
      [limit]
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
    listUsers,
    createMerchant,
    setMerchantStatus,
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
