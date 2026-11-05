-- Consumers copy this statement into their own Flyway migration and grant
-- INSERT, SELECT to the service role. The ledger does not own this table.
CREATE TABLE IF NOT EXISTS processed_events (
    event_id     uuid PRIMARY KEY,
    processed_at timestamptz NOT NULL
);
