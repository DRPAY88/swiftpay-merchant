CREATE TABLE users (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  email TEXT NOT NULL,
  status TEXT NOT NULL DEFAULT 'pending'
    CHECK (status IN ('pending', 'active', 'suspended', 'closed')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX users_email_lower_unique ON users (LOWER(email));

CREATE TABLE business_profiles (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL UNIQUE REFERENCES users(id),
  legal_name TEXT NOT NULL,
  country_code CHAR(2) NOT NULL,
  business_type TEXT NOT NULL,
  verification_status TEXT NOT NULL DEFAULT 'pending'
    CHECK (verification_status IN ('pending', 'verified', 'rejected', 'restricted')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE currencies (
  code VARCHAR(5) PRIMARY KEY,
  name TEXT NOT NULL,
  currency_type TEXT NOT NULL CHECK (currency_type IN ('fiat', 'crypto')),
  decimal_places SMALLINT NOT NULL CHECK (decimal_places BETWEEN 0 AND 8),
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

INSERT INTO currencies (code, name, currency_type, decimal_places)
VALUES
  ('PHP', 'Philippine Peso', 'fiat', 2),
  ('USDT', 'Tether USD', 'crypto', 6),
  ('KRW', 'South Korean Won', 'fiat', 0)
ON CONFLICT (code) DO NOTHING;

CREATE TABLE wallets (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES users(id),
  currency_code VARCHAR(5) NOT NULL REFERENCES currencies(code),
  wallet_type TEXT NOT NULL DEFAULT 'receiving'
    CHECK (wallet_type IN ('receiving', 'settlement')),
  status TEXT NOT NULL DEFAULT 'active'
    CHECK (status IN ('pending', 'active', 'frozen', 'closed')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  UNIQUE (user_id, currency_code, wallet_type),
  UNIQUE (id, user_id),
  UNIQUE (id, currency_code)
);

CREATE INDEX wallets_user_status ON wallets (user_id, status);

CREATE TABLE receiving_accounts (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  wallet_id UUID NOT NULL REFERENCES wallets(id),
  provider TEXT NOT NULL,
  provider_account_id TEXT,
  country_code CHAR(2) NOT NULL,
  account_details_ciphertext TEXT,
  status TEXT NOT NULL DEFAULT 'pending'
    CHECK (status IN ('pending', 'active', 'disabled', 'failed')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  UNIQUE (wallet_id, provider),
  UNIQUE (provider, provider_account_id)
);

CREATE TABLE invoices (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES users(id),
  wallet_id UUID NOT NULL,
  invoice_number TEXT NOT NULL,
  client_name TEXT,
  client_email TEXT,
  amount NUMERIC(30, 8) NOT NULL CHECK (amount > 0),
  currency_code VARCHAR(5) NOT NULL REFERENCES currencies(code),
  line_items JSONB NOT NULL DEFAULT '[]'::jsonb,
  status TEXT NOT NULL DEFAULT 'draft'
    CHECK (status IN ('draft', 'issued', 'partially_paid', 'paid', 'overdue', 'void')),
  due_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  UNIQUE (user_id, invoice_number),
  UNIQUE (id, user_id),
  FOREIGN KEY (wallet_id, user_id) REFERENCES wallets(id, user_id),
  FOREIGN KEY (wallet_id, currency_code) REFERENCES wallets(id, currency_code)
);

CREATE TABLE payment_requests (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES users(id),
  wallet_id UUID NOT NULL,
  invoice_id UUID REFERENCES invoices(id),
  public_token_hash TEXT NOT NULL UNIQUE,
  description TEXT NOT NULL,
  amount NUMERIC(30, 8) NOT NULL CHECK (amount > 0),
  currency_code VARCHAR(5) NOT NULL REFERENCES currencies(code),
  status TEXT NOT NULL DEFAULT 'open'
    CHECK (status IN ('open', 'paid', 'expired', 'cancelled')),
  expires_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  UNIQUE (id, wallet_id),
  FOREIGN KEY (wallet_id, user_id) REFERENCES wallets(id, user_id),
  FOREIGN KEY (wallet_id, currency_code) REFERENCES wallets(id, currency_code),
  FOREIGN KEY (invoice_id, user_id) REFERENCES invoices(id, user_id)
);

CREATE INDEX payment_requests_user_status ON payment_requests (user_id, status, created_at DESC);

CREATE TABLE payment_methods (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES users(id),
  provider TEXT NOT NULL,
  rail TEXT NOT NULL CHECK (rail IN ('card', 'ach', 'sepa', 'bank_transfer')),
  provider_method_id TEXT NOT NULL,
  display_brand TEXT,
  last_four CHAR(4),
  status TEXT NOT NULL DEFAULT 'active'
    CHECK (status IN ('active', 'disabled', 'expired')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  UNIQUE (provider, provider_method_id)
);

CREATE TABLE payout_destinations (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES users(id),
  provider TEXT NOT NULL,
  rail TEXT NOT NULL CHECK (rail IN ('bank_transfer', 'ach', 'sepa')),
  country_code CHAR(2) NOT NULL,
  currency_code VARCHAR(5) NOT NULL REFERENCES currencies(code),
  provider_destination_id TEXT,
  account_details_ciphertext TEXT,
  status TEXT NOT NULL DEFAULT 'pending'
    CHECK (status IN ('pending', 'verified', 'rejected', 'disabled')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  UNIQUE (provider, provider_destination_id)
);

CREATE TABLE fx_quotes (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES users(id),
  provider TEXT NOT NULL,
  source_currency VARCHAR(5) NOT NULL REFERENCES currencies(code),
  target_currency VARCHAR(5) NOT NULL REFERENCES currencies(code),
  source_amount NUMERIC(30, 8) NOT NULL CHECK (source_amount > 0),
  target_amount NUMERIC(30, 8) NOT NULL CHECK (target_amount > 0),
  rate NUMERIC(30, 12) NOT NULL CHECK (rate > 0),
  fee_amount NUMERIC(30, 8) NOT NULL DEFAULT 0 CHECK (fee_amount >= 0),
  fee_currency VARCHAR(5) REFERENCES currencies(code),
  provider_quote_id TEXT,
  status TEXT NOT NULL DEFAULT 'quoted'
    CHECK (status IN ('quoted', 'accepted', 'expired', 'cancelled')),
  expires_at TIMESTAMPTZ NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CHECK (source_currency <> target_currency)
);

CREATE TABLE transactions (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  wallet_id UUID NOT NULL,
  payment_request_id UUID REFERENCES payment_requests(id),
  fx_quote_id UUID REFERENCES fx_quotes(id),
  reversal_of_transaction_id UUID REFERENCES transactions(id),
  transaction_type TEXT NOT NULL
    CHECK (transaction_type IN ('payment_in', 'payout', 'transfer', 'fee', 'fx_conversion', 'refund', 'adjustment')),
  status TEXT NOT NULL DEFAULT 'created'
    CHECK (status IN ('created', 'pending', 'processing', 'succeeded', 'failed', 'cancelled', 'reversed')),
  amount NUMERIC(30, 8) NOT NULL CHECK (amount > 0),
  currency_code VARCHAR(5) NOT NULL REFERENCES currencies(code),
  fee_amount NUMERIC(30, 8) NOT NULL DEFAULT 0 CHECK (fee_amount >= 0),
  fee_currency VARCHAR(5) REFERENCES currencies(code),
  provider TEXT,
  provider_reference TEXT,
  idempotency_key TEXT,
  description TEXT,
  metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  completed_at TIMESTAMPTZ,
  CHECK ((fee_amount = 0) OR (fee_currency IS NOT NULL)),
  UNIQUE (provider, provider_reference),
  UNIQUE (wallet_id, idempotency_key),
  FOREIGN KEY (wallet_id, currency_code) REFERENCES wallets(id, currency_code),
  FOREIGN KEY (payment_request_id, wallet_id) REFERENCES payment_requests(id, wallet_id)
);

CREATE INDEX transactions_wallet_created ON transactions (wallet_id, created_at DESC);
CREATE INDEX transactions_status_created ON transactions (status, created_at);

CREATE TABLE ledger_accounts (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  wallet_id UUID REFERENCES wallets(id),
  currency_code VARCHAR(5) NOT NULL REFERENCES currencies(code),
  account_code TEXT NOT NULL,
  account_type TEXT NOT NULL
    CHECK (account_type IN ('wallet_liability', 'provider_clearing', 'platform_cash', 'fee_revenue', 'fx_clearing')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  FOREIGN KEY (wallet_id, currency_code) REFERENCES wallets(id, currency_code)
);

CREATE UNIQUE INDEX ledger_system_account_unique
  ON ledger_accounts (currency_code, account_code)
  WHERE wallet_id IS NULL;
CREATE UNIQUE INDEX ledger_wallet_account_unique
  ON ledger_accounts (wallet_id, currency_code, account_code)
  WHERE wallet_id IS NOT NULL;

CREATE TABLE ledger_batches (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  transaction_id UUID NOT NULL UNIQUE REFERENCES transactions(id),
  reversal_of_batch_id UUID UNIQUE REFERENCES ledger_batches(id),
  status TEXT NOT NULL DEFAULT 'draft' CHECK (status IN ('draft', 'posted')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  posted_at TIMESTAMPTZ
);

CREATE TABLE ledger_entries (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  batch_id UUID NOT NULL REFERENCES ledger_batches(id),
  ledger_account_id UUID NOT NULL REFERENCES ledger_accounts(id),
  direction TEXT NOT NULL CHECK (direction IN ('debit', 'credit')),
  amount NUMERIC(30, 8) NOT NULL CHECK (amount > 0),
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX ledger_entries_batch ON ledger_entries (batch_id);
CREATE INDEX ledger_entries_account_created ON ledger_entries (ledger_account_id, created_at);

CREATE VIEW wallet_balances AS
SELECT
  wallets.id AS wallet_id,
  wallets.user_id,
  wallets.currency_code,
  COALESCE(
    SUM(
      CASE
        WHEN batches.status = 'posted' AND entries.direction = 'credit' THEN entries.amount
        WHEN batches.status = 'posted' AND entries.direction = 'debit' THEN -entries.amount
        ELSE 0
      END
    ),
    0
  ) AS balance
FROM wallets
LEFT JOIN ledger_accounts AS accounts
  ON accounts.wallet_id = wallets.id
  AND accounts.account_type = 'wallet_liability'
LEFT JOIN ledger_entries AS entries ON entries.ledger_account_id = accounts.id
LEFT JOIN ledger_batches AS batches ON batches.id = entries.batch_id
GROUP BY wallets.id, wallets.user_id, wallets.currency_code;

CREATE FUNCTION prevent_ledger_account_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
  IF EXISTS (SELECT 1 FROM ledger_entries WHERE ledger_account_id = OLD.id) THEN
    RAISE EXCEPTION 'Ledger accounts referenced by entries are immutable';
  END IF;
  IF TG_OP = 'DELETE' THEN
    RETURN OLD;
  END IF;
  RETURN NEW;
END;
$$;

CREATE TRIGGER ledger_accounts_integrity
BEFORE UPDATE OR DELETE ON ledger_accounts
FOR EACH ROW EXECUTE FUNCTION prevent_ledger_account_mutation();

CREATE FUNCTION enforce_ledger_batch_integrity()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
  batch_status TEXT;
BEGIN
  IF TG_TABLE_NAME = 'ledger_batches' THEN
    IF TG_OP = 'INSERT' THEN
      IF NEW.status <> 'draft' OR NEW.posted_at IS NOT NULL THEN
        RAISE EXCEPTION 'Ledger batches must be created as drafts';
      END IF;
      IF NEW.reversal_of_batch_id IS NOT NULL AND NOT EXISTS (
        SELECT 1 FROM ledger_batches
        WHERE id = NEW.reversal_of_batch_id AND status = 'posted'
      ) THEN
        RAISE EXCEPTION 'Only a posted ledger batch can be reversed';
      END IF;
      RETURN NEW;
    END IF;

    IF TG_OP = 'DELETE' THEN
      IF OLD.status = 'posted' THEN
        RAISE EXCEPTION 'Posted ledger batches are immutable';
      END IF;
      RETURN OLD;
    END IF;

    IF OLD.status = 'posted' THEN
      RAISE EXCEPTION 'Posted ledger batches are immutable';
    END IF;

    IF NEW.status = 'posted' THEN
      IF NOT EXISTS (SELECT 1 FROM ledger_entries WHERE batch_id = NEW.id) THEN
        RAISE EXCEPTION 'Cannot post an empty ledger batch';
      END IF;

      IF EXISTS (
        SELECT accounts.currency_code
        FROM ledger_entries AS entries
        JOIN ledger_accounts AS accounts ON accounts.id = entries.ledger_account_id
        WHERE entries.batch_id = NEW.id
        GROUP BY accounts.currency_code
        HAVING SUM(CASE WHEN entries.direction = 'debit' THEN entries.amount ELSE 0 END)
          <> SUM(CASE WHEN entries.direction = 'credit' THEN entries.amount ELSE 0 END)
      ) THEN
        RAISE EXCEPTION 'Ledger batch debits and credits must balance for every currency';
      END IF;

      IF NEW.reversal_of_batch_id IS NOT NULL AND EXISTS (
        WITH expected AS (
          SELECT ledger_account_id,
            CASE direction WHEN 'debit' THEN 'credit' ELSE 'debit' END AS direction,
            SUM(amount) AS amount
          FROM ledger_entries
          WHERE batch_id = NEW.reversal_of_batch_id
          GROUP BY ledger_account_id, direction
        ),
        actual AS (
          SELECT ledger_account_id, direction, SUM(amount) AS amount
          FROM ledger_entries
          WHERE batch_id = NEW.id
          GROUP BY ledger_account_id, direction
        )
        SELECT 1
        FROM expected
        FULL OUTER JOIN actual USING (ledger_account_id, direction)
        WHERE expected.amount IS DISTINCT FROM actual.amount
      ) THEN
        RAISE EXCEPTION 'Reversal entries must exactly offset the original posted batch';
      END IF;

      NEW.posted_at := COALESCE(NEW.posted_at, NOW());
    ELSIF NEW.posted_at IS NOT NULL THEN
      RAISE EXCEPTION 'Draft ledger batches cannot have a posted timestamp';
    END IF;
    RETURN NEW;
  END IF;

  IF TG_OP = 'INSERT' THEN
    batch_status := NULL;
    SELECT status INTO batch_status
      FROM ledger_batches
      WHERE id = NEW.batch_id
      FOR UPDATE;
    IF batch_status <> 'draft' THEN
      RAISE EXCEPTION 'Ledger entries can only be added to draft batches';
    END IF;
    RETURN NEW;
  END IF;

  IF TG_OP = 'DELETE' THEN
    SELECT status INTO batch_status
      FROM ledger_batches
      WHERE id = OLD.batch_id
      FOR UPDATE;
    IF batch_status = 'posted' THEN
      RAISE EXCEPTION 'Posted ledger entries are immutable';
    END IF;
    RETURN OLD;
  END IF;

  IF NEW.batch_id <> OLD.batch_id THEN
    RAISE EXCEPTION 'Ledger entries cannot be moved between batches';
  END IF;

  SELECT status INTO batch_status
    FROM ledger_batches
    WHERE id = OLD.batch_id
    FOR UPDATE;
  IF batch_status = 'posted' THEN
    RAISE EXCEPTION 'Posted ledger entries are immutable';
  END IF;
  RETURN NEW;
END;
$$;

CREATE TRIGGER ledger_batches_integrity
BEFORE INSERT OR UPDATE OR DELETE ON ledger_batches
FOR EACH ROW EXECUTE FUNCTION enforce_ledger_batch_integrity();

CREATE TRIGGER ledger_entries_integrity
BEFORE INSERT OR UPDATE OR DELETE ON ledger_entries
FOR EACH ROW EXECUTE FUNCTION enforce_ledger_batch_integrity();

CREATE TABLE provider_events (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  provider TEXT NOT NULL,
  provider_event_id TEXT NOT NULL,
  event_type TEXT NOT NULL,
  payload_hash TEXT NOT NULL,
  payload JSONB NOT NULL DEFAULT '{}'::jsonb,
  status TEXT NOT NULL DEFAULT 'received'
    CHECK (status IN ('received', 'processing', 'processed', 'failed')),
  received_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  processed_at TIMESTAMPTZ,
  UNIQUE (provider, provider_event_id)
);

COMMENT ON COLUMN provider_events.payload IS
  'Sanitized provider event payload only; never persist card data, credentials, or authentication secrets.';
