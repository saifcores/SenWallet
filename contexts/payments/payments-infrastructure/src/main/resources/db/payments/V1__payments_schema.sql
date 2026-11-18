-- Payments service (M7, ADR-25). Flyway runs as payments_owner;
-- ${app_role} is the runtime role. Caps and cash-out fees are dated rows.

CREATE TABLE wallet_projections (
    wallet_id         uuid PRIMARY KEY,
    customer_id       uuid NOT NULL,
    ledger_id         uuid NOT NULL,
    ledger_account_id uuid NOT NULL,
    pispi_alias       text NOT NULL UNIQUE,
    phone             text NOT NULL,
    tier              text NOT NULL,
    holding_cap       bigint NOT NULL CHECK (holding_cap >= 0),
    fee_account_id    uuid NOT NULL,
    updated_at        timestamptz NOT NULL
);

CREATE TABLE platform_accounts (
    singleton              boolean PRIMARY KEY DEFAULT true CHECK (singleton),
    ledger_id              uuid NOT NULL,
    fee_account_id         uuid NOT NULL,
    wave_float_id          uuid NOT NULL,
    orange_float_id        uuid NOT NULL,
    wave_receivable_id     uuid NOT NULL,
    orange_receivable_id   uuid NOT NULL,
    pispi_compensation_id  uuid NOT NULL,
    suspense_account_id    uuid NOT NULL
);

CREATE TABLE payment_orders (
    id              uuid PRIMARY KEY,
    type            text NOT NULL CHECK (type IN ('topup', 'cashout', 'pispi_out', 'pispi_in')),
    state           text NOT NULL CHECK (state IN (
                        'created', 'hold_placed', 'submitted', 'completed',
                        'failed', 'rejected', 'unknown', 'manual_review')),
    wallet_id       uuid,
    amount          bigint NOT NULL CHECK (amount > 0),
    fee             bigint NOT NULL CHECK (fee >= 0 AND fee < amount),
    currency        text NOT NULL,
    provider        text NOT NULL CHECK (provider IN ('wave', 'orange', 'pispi')),
    provider_ref    text,
    ledger_txn_id   uuid,
    attempts        int NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    next_check_at   timestamptz,
    rail_submitted  boolean NOT NULL DEFAULT false,
    version         int NOT NULL DEFAULT 0,
    phone           text,
    alias           text,
    created_at      timestamptz NOT NULL,
    updated_at      timestamptz NOT NULL,
    CONSTRAINT payment_orders_completed_have_ledger CHECK (
        state <> 'completed' OR ledger_txn_id IS NOT NULL
    )
);

CREATE INDEX payment_orders_due ON payment_orders (next_check_at)
    WHERE state IN ('submitted', 'unknown');

CREATE UNIQUE INDEX payment_orders_inbound_ref ON payment_orders (provider_ref)
    WHERE type = 'pispi_in';

CREATE TABLE callback_inbox (
    id            bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    provider      text NOT NULL,
    nonce         text NOT NULL,
    provider_ref  text,
    external_ref  text,
    status        text NOT NULL,
    body          jsonb NOT NULL,
    received_at   timestamptz NOT NULL,
    processed_at  timestamptz,
    UNIQUE (provider, nonce)
);

CREATE INDEX callback_inbox_pending ON callback_inbox (id) WHERE processed_at IS NULL;

CREATE TABLE idempotency_keys (
    key             uuid PRIMARY KEY,
    request_hash    text NOT NULL,
    order_id        uuid NOT NULL,
    response_status int,
    response_body   text,
    expires_at      timestamptz NOT NULL,
    created_at      timestamptz NOT NULL
);

CREATE TABLE limit_policies (
    id          uuid PRIMARY KEY,
    operation   text NOT NULL,
    amount      bigint NOT NULL CHECK (amount >= 0),
    valid_from  timestamptz NOT NULL,
    UNIQUE (operation, valid_from)
);

CREATE TABLE fee_schedules (
    id         uuid PRIMARY KEY,
    operation  text NOT NULL,
    rate       numeric(8, 6) NOT NULL CHECK (rate >= 0),
    valid_from timestamptz NOT NULL,
    UNIQUE (operation, valid_from)
);

CREATE TABLE outbox (
    id             bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_id       uuid NOT NULL UNIQUE,
    aggregate_type text NOT NULL CHECK (aggregate_type <> ''),
    aggregate_id   uuid NOT NULL,
    event_type     text NOT NULL CHECK (event_type <> ''),
    payload        jsonb NOT NULL,
    created_at     timestamptz NOT NULL,
    published_at   timestamptz
);

CREATE INDEX outbox_unpublished ON outbox (id) WHERE published_at IS NULL;

CREATE TABLE processed_events (
    event_id     uuid PRIMARY KEY,
    processed_at timestamptz NOT NULL
);

-- Instruction n° 020 monthly recharge cap, and a zero cash-out fee so
-- SendCash can hold the full available balance.
INSERT INTO limit_policies (id, operation, amount, valid_from) VALUES
    ('55555555-5555-4555-8555-555555555555', 'topup_monthly', 15000000, '2026-11-02T00:00:00Z');

INSERT INTO fee_schedules (id, operation, rate, valid_from) VALUES
    ('66666666-6666-4666-8666-666666666666', 'cashout', 0.000000, '2026-11-02T00:00:00Z');

GRANT SELECT, INSERT, UPDATE ON wallet_projections, platform_accounts, payment_orders,
    idempotency_keys TO ${app_role};
GRANT SELECT, INSERT, UPDATE ON callback_inbox TO ${app_role};
GRANT USAGE, SELECT ON SEQUENCE callback_inbox_id_seq TO ${app_role};
GRANT SELECT ON limit_policies, fee_schedules TO ${app_role};
GRANT SELECT, INSERT ON processed_events, outbox TO ${app_role};
GRANT UPDATE (published_at) ON outbox TO ${app_role};
GRANT USAGE, SELECT ON SEQUENCE outbox_id_seq TO ${app_role};
