-- Ledger schema (dossier section 3, ADR-18/19/20). Runs as the owner role; the
-- application role ${app_role} only gets the privileges granted at the end.

CREATE TYPE normal_balance AS ENUM ('debit', 'credit');
CREATE TYPE txn_status     AS ENUM ('pending', 'posted', 'archived');
CREATE TYPE direction      AS ENUM ('debit', 'credit');

CREATE TABLE ledgers (
    id          uuid PRIMARY KEY,
    name        text NOT NULL CHECK (name <> ''),
    environment text NOT NULL CHECK (environment IN ('live', 'test')),
    created_at  timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE ledger_accounts (
    id                uuid PRIMARY KEY,
    ledger_id         uuid NOT NULL REFERENCES ledgers (id),
    name              text NOT NULL CHECK (name <> ''),
    normal_balance    normal_balance NOT NULL,
    currency          char(3) NOT NULL DEFAULT 'XOF',
    currency_exponent smallint NOT NULL DEFAULT 0,
    balance_mode      text NOT NULL DEFAULT 'sync' CHECK (balance_mode IN ('sync', 'async')),
    status            text NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'inactive')),
    metadata          jsonb NOT NULL DEFAULT '{}',
    created_at        timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX ON ledger_accounts (ledger_id);
CREATE INDEX ON ledger_accounts USING gin (metadata);

CREATE TABLE account_balances (
    account_id   uuid PRIMARY KEY REFERENCES ledger_accounts (id),
    posted_dr    bigint NOT NULL DEFAULT 0 CHECK (posted_dr >= 0),
    posted_cr    bigint NOT NULL DEFAULT 0 CHECK (posted_cr >= 0),
    pending_dr   bigint NOT NULL DEFAULT 0 CHECK (pending_dr >= 0),
    pending_cr   bigint NOT NULL DEFAULT 0 CHECK (pending_cr >= 0),
    lock_version bigint NOT NULL DEFAULT 0 CHECK (lock_version >= 0),
    updated_at   timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE ledger_transactions (
    id           uuid PRIMARY KEY,
    ledger_id    uuid NOT NULL REFERENCES ledgers (id),
    external_id  text NOT NULL CHECK (external_id <> ''),
    request_hash bytea NOT NULL,
    status       txn_status NOT NULL,
    description  text,
    effective_at timestamptz NOT NULL,
    posted_at    timestamptz,
    -- ADR-18: needed to compute pending balances as of a past date.
    archived_at  timestamptz,
    reverses_id  uuid REFERENCES ledger_transactions (id),
    metadata     jsonb NOT NULL DEFAULT '{}',
    created_at   timestamptz NOT NULL DEFAULT now(),
    UNIQUE (ledger_id, external_id),
    CHECK ((status = 'pending'  AND posted_at IS NULL     AND archived_at IS NULL)
        OR (status = 'posted'   AND posted_at IS NOT NULL AND archived_at IS NULL)
        OR (status = 'archived' AND posted_at IS NULL     AND archived_at IS NOT NULL))
);

CREATE INDEX ON ledger_transactions USING gin (metadata);
-- A transaction is reversed at most once.
CREATE UNIQUE INDEX ledger_transactions_reverses_id_key
    ON ledger_transactions (reverses_id) WHERE reverses_id IS NOT NULL;

CREATE TABLE ledger_entries (
    id              bigint GENERATED ALWAYS AS IDENTITY,
    transaction_id  uuid NOT NULL REFERENCES ledger_transactions (id),
    account_id      uuid NOT NULL REFERENCES ledger_accounts (id),
    direction       direction NOT NULL,
    amount          bigint NOT NULL CHECK (amount > 0),
    -- ADR-18: lock_version of the account after this entry.
    account_version bigint NOT NULL CHECK (account_version > 0),
    created_at      timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (id, created_at)
) PARTITION BY RANGE (created_at);

CREATE INDEX ON ledger_entries (account_id, created_at);
CREATE INDEX ON ledger_entries (transaction_id);

-- Partitions (ADR-19): one per UTC month, created ahead of time so the default
-- partition stays empty; a new partition cannot be added over rows already in
-- the default one.

CREATE TABLE ledger_entries_default PARTITION OF ledger_entries DEFAULT;

CREATE FUNCTION create_entry_partition(month date) RETURNS boolean
    LANGUAGE plpgsql
    SECURITY DEFINER
    SET search_path = public, pg_temp
AS $$
DECLARE
    start_at timestamptz := make_timestamptz(
            extract(year FROM month)::int, extract(month FROM month)::int, 1, 0, 0, 0, 'UTC');
    partition_name text := 'ledger_entries_' || to_char(month, 'YYYY_MM');
BEGIN
    IF to_regclass(partition_name) IS NOT NULL THEN
        RETURN false;
    END IF;
    EXECUTE format('CREATE TABLE %I PARTITION OF ledger_entries FOR VALUES FROM (%L) TO (%L)',
                   partition_name, start_at, start_at + interval '1 month');
    RETURN true;
END
$$;

-- Called at every ledger start; returns the number of partitions created.
CREATE FUNCTION ensure_entry_partitions(months_ahead int) RETURNS int
    LANGUAGE plpgsql
    SECURITY DEFINER
    SET search_path = public, pg_temp
AS $$
DECLARE
    current_month date := date_trunc('month', now() AT TIME ZONE 'UTC')::date;
    created int := 0;
BEGIN
    IF months_ahead < 0 THEN
        RAISE EXCEPTION 'months_ahead must be >= 0, got %', months_ahead;
    END IF;
    -- Serialises concurrent starts of several ledger instances.
    PERFORM pg_advisory_xact_lock(hashtext('ensure_entry_partitions'));
    FOR i IN 0..months_ahead LOOP
        IF create_entry_partition((current_month + make_interval(months => i))::date) THEN
            created := created + 1;
        END IF;
    END LOOP;
    RETURN created;
END
$$;

DO $$
BEGIN
    PERFORM create_entry_partition(month::date)
    FROM generate_series(date '2026-10-01', date '2027-12-01', interval '1 month') AS month;
END
$$;

-- Guardrails in the database, on top of the application checks.

-- Each transaction has at least two entries and its debits equal its credits.
-- Checked at commit, once every entry is in.
CREATE FUNCTION assert_transaction_balanced(txn_id uuid) RETURNS void
    LANGUAGE plpgsql
AS $$
DECLARE
    entry_count bigint;
    debits bigint;
    credits bigint;
BEGIN
    SELECT count(*),
           coalesce(sum(amount) FILTER (WHERE direction = 'debit'), 0),
           coalesce(sum(amount) FILTER (WHERE direction = 'credit'), 0)
    INTO entry_count, debits, credits
    FROM ledger_entries
    WHERE transaction_id = txn_id;
    IF entry_count < 2 OR debits <> credits THEN
        RAISE EXCEPTION 'ledger transaction % is unbalanced: % entries, debits %, credits %',
            txn_id, entry_count, debits, credits
            USING ERRCODE = 'check_violation';
    END IF;
END
$$;

CREATE FUNCTION check_inserted_transaction() RETURNS trigger
    LANGUAGE plpgsql
AS $$
BEGIN
    PERFORM assert_transaction_balanced(NEW.id);
    RETURN NULL;
END
$$;

CREATE FUNCTION check_inserted_entry() RETURNS trigger
    LANGUAGE plpgsql
AS $$
BEGIN
    PERFORM assert_transaction_balanced(NEW.transaction_id);
    RETURN NULL;
END
$$;

CREATE CONSTRAINT TRIGGER ledger_transactions_balanced
    AFTER INSERT ON ledger_transactions
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION check_inserted_transaction();

CREATE CONSTRAINT TRIGGER ledger_entries_balanced
    AFTER INSERT ON ledger_entries
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION check_inserted_entry();

-- Entries are append-only, even for the owner role.
CREATE FUNCTION forbid_entry_change() RETURNS trigger
    LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'ledger_entries is append-only: % refused', TG_OP
        USING ERRCODE = 'insufficient_privilege';
END
$$;

CREATE TRIGGER ledger_entries_append_only
    BEFORE UPDATE OR DELETE ON ledger_entries
    FOR EACH ROW EXECUTE FUNCTION forbid_entry_change();

CREATE TRIGGER ledger_entries_no_truncate
    BEFORE TRUNCATE ON ledger_entries
    FOR EACH STATEMENT EXECUTE FUNCTION forbid_entry_change();

-- A transaction only moves pending -> posted | archived; nothing else changes.
CREATE FUNCTION guard_transaction_update() RETURNS trigger
    LANGUAGE plpgsql
AS $$
BEGIN
    IF OLD.status <> 'pending' OR NEW.status NOT IN ('posted', 'archived') THEN
        RAISE EXCEPTION 'illegal transition % -> % for ledger transaction %', OLD.status, NEW.status, OLD.id
            USING ERRCODE = 'check_violation';
    END IF;
    IF (NEW.id, NEW.ledger_id, NEW.external_id, NEW.request_hash, NEW.description, NEW.effective_at,
        NEW.reverses_id, NEW.metadata, NEW.created_at)
       IS DISTINCT FROM
       (OLD.id, OLD.ledger_id, OLD.external_id, OLD.request_hash, OLD.description, OLD.effective_at,
        OLD.reverses_id, OLD.metadata, OLD.created_at) THEN
        RAISE EXCEPTION 'only the status of ledger transaction % may change', OLD.id
            USING ERRCODE = 'check_violation';
    END IF;
    RETURN NEW;
END
$$;

CREATE TRIGGER ledger_transactions_guard_update
    BEFORE UPDATE ON ledger_transactions
    FOR EACH ROW EXECUTE FUNCTION guard_transaction_update();

CREATE FUNCTION forbid_transaction_delete() RETURNS trigger
    LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'ledger transactions are never deleted'
        USING ERRCODE = 'insufficient_privilege';
END
$$;

CREATE TRIGGER ledger_transactions_no_delete
    BEFORE DELETE ON ledger_transactions
    FOR EACH ROW EXECUTE FUNCTION forbid_transaction_delete();

-- Application role (ADR-20): exactly what the ledger service needs.

REVOKE EXECUTE ON FUNCTION create_entry_partition(date) FROM PUBLIC;
REVOKE EXECUTE ON FUNCTION ensure_entry_partitions(int) FROM PUBLIC;

GRANT USAGE ON SCHEMA public TO ${app_role};
GRANT SELECT, INSERT ON ledgers, ledger_accounts, ledger_entries TO ${app_role};
GRANT SELECT, INSERT, UPDATE ON account_balances TO ${app_role};
GRANT SELECT, INSERT ON ledger_transactions TO ${app_role};
GRANT UPDATE (status, posted_at, archived_at) ON ledger_transactions TO ${app_role};
GRANT EXECUTE ON FUNCTION ensure_entry_partitions(int) TO ${app_role};
