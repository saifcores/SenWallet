-- Transactional outbox (dossier section 6.1, ADR-21). The identity column
-- orders the relay; event_id is the consumer deduplication key.

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

GRANT SELECT, INSERT ON outbox TO ${app_role};
GRANT UPDATE (published_at) ON outbox TO ${app_role};
GRANT USAGE, SELECT ON SEQUENCE outbox_id_seq TO ${app_role};
