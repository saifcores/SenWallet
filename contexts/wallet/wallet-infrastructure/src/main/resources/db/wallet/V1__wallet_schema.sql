-- Wallet service (M4). Flyway runs as wallet_owner; ${app_role} is the
-- runtime role. Caps and the P2P fee are dated configuration, not constants.

CREATE TYPE customer_status AS ENUM ('unverified', 'verified');
CREATE TYPE kyc_tier AS ENUM ('n1', 'n2', 'merchant');
CREATE TYPE wallet_status AS ENUM ('opening', 'active');
CREATE TYPE transfer_status AS ENUM ('posted', 'rejected');

CREATE TABLE customers (
    id         uuid PRIMARY KEY,
    phone      text NOT NULL UNIQUE,
    status     customer_status NOT NULL,
    kyc_tier   kyc_tier,
    otp_code   text,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT customers_status_shape CHECK (
        (status = 'unverified' AND kyc_tier IS NULL AND otp_code IS NOT NULL)
        OR (status = 'verified' AND kyc_tier IS NOT NULL AND otp_code IS NULL)
    )
);

CREATE TABLE wallets (
    id                 uuid PRIMARY KEY,
    customer_id        uuid NOT NULL UNIQUE REFERENCES customers (id),
    status             wallet_status NOT NULL,
    ledger_account_id  uuid,
    pispi_alias        text NOT NULL UNIQUE,
    created_at         timestamptz NOT NULL,
    updated_at         timestamptz NOT NULL,
    CONSTRAINT wallets_active_have_account CHECK (status <> 'active' OR ledger_account_id IS NOT NULL)
);

CREATE INDEX wallets_ledger_account ON wallets (ledger_account_id);

CREATE TABLE transfers (
    id                    uuid PRIMARY KEY,
    from_wallet_id        uuid NOT NULL REFERENCES wallets (id),
    to_wallet_id          uuid NOT NULL REFERENCES wallets (id),
    amount                bigint NOT NULL CHECK (amount > 0),
    fee                   bigint NOT NULL CHECK (fee >= 0),
    currency              text NOT NULL,
    status                transfer_status NOT NULL,
    ledger_transaction_id uuid,
    created_at            timestamptz NOT NULL,
    CONSTRAINT transfers_posted_have_ledger CHECK (
        status <> 'posted' OR ledger_transaction_id IS NOT NULL
    ),
    CONSTRAINT transfers_distinct_wallets CHECK (from_wallet_id <> to_wallet_id)
);

CREATE TABLE limit_policies (
    id          uuid PRIMARY KEY,
    tier        kyc_tier NOT NULL,
    holding_cap bigint NOT NULL CHECK (holding_cap >= 0),
    valid_from  timestamptz NOT NULL,
    UNIQUE (tier, valid_from)
);

CREATE TABLE fee_schedules (
    id         uuid PRIMARY KEY,
    operation  text NOT NULL,
    rate       numeric(8, 6) NOT NULL CHECK (rate >= 0),
    valid_from timestamptz NOT NULL,
    UNIQUE (operation, valid_from)
);

CREATE TABLE idempotency_keys (
    key             uuid PRIMARY KEY,
    request_hash    text NOT NULL,
    transfer_id     uuid NOT NULL,
    response_status int,
    response_code   text,
    response_detail text,
    expires_at      timestamptz NOT NULL,
    created_at      timestamptz NOT NULL
);

-- Copied from platform/event-inbox. The wallet owns this table.
CREATE TABLE processed_events (
    event_id     uuid PRIMARY KEY,
    processed_at timestamptz NOT NULL
);

CREATE TABLE wallet_history (
    id             bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_id       uuid NOT NULL,
    event_type     text NOT NULL,
    wallet_id      uuid NOT NULL REFERENCES wallets (id),
    transaction_id uuid NOT NULL,
    external_id    text NOT NULL,
    ledger_id      uuid NOT NULL,
    direction      text NOT NULL,
    amount         bigint NOT NULL CHECK (amount > 0),
    occurred_at    timestamptz NOT NULL,
    UNIQUE (event_id, wallet_id)
);

CREATE INDEX wallet_history_wallet ON wallet_history (wallet_id, occurred_at, id);

CREATE TABLE platform_accounts (
    singleton      boolean PRIMARY KEY DEFAULT true CHECK (singleton),
    ledger_id      uuid NOT NULL,
    fee_account_id uuid NOT NULL
);

-- Instruction n° 020, in force on 2 November 2026: 4 000 000 XOF, P2P 1 %.
INSERT INTO limit_policies (id, tier, holding_cap, valid_from) VALUES
    ('11111111-1111-4111-8111-111111111111', 'n1', 4000000, '2026-11-02T00:00:00Z'),
    ('22222222-2222-4222-8222-222222222222', 'n2', 4000000, '2026-11-02T00:00:00Z'),
    ('33333333-3333-4333-8333-333333333333', 'merchant', 4000000, '2026-11-02T00:00:00Z');

INSERT INTO fee_schedules (id, operation, rate, valid_from) VALUES
    ('44444444-4444-4444-8444-444444444444', 'p2p', 0.010000, '2026-11-02T00:00:00Z');

GRANT SELECT, INSERT, UPDATE ON customers TO ${app_role};
GRANT SELECT, INSERT ON wallets, transfers TO ${app_role};
GRANT SELECT ON limit_policies, fee_schedules TO ${app_role};
GRANT SELECT, INSERT, UPDATE ON idempotency_keys, platform_accounts TO ${app_role};
GRANT SELECT, INSERT ON processed_events, wallet_history TO ${app_role};
GRANT USAGE, SELECT ON SEQUENCE wallet_history_id_seq TO ${app_role};
