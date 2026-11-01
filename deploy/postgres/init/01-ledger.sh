#!/usr/bin/env bash
# Runs once, on first start with an empty volume (docker compose down -v to rerun).
# ledger_owner owns the schema and runs Flyway; ledger_app is what the service
# connects as (ADR-20).
set -euo pipefail

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres \
    -v owner_password="$LEDGER_OWNER_PASSWORD" \
    -v app_password="$LEDGER_DB_PASSWORD" <<'SQL'
CREATE ROLE ledger_owner LOGIN PASSWORD :'owner_password';
CREATE ROLE ledger_app LOGIN PASSWORD :'app_password';
CREATE DATABASE ledger OWNER ledger_owner;
REVOKE ALL ON DATABASE ledger FROM PUBLIC;
GRANT CONNECT ON DATABASE ledger TO ledger_app;
SQL

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname ledger <<'SQL'
ALTER SCHEMA public OWNER TO ledger_owner;
REVOKE ALL ON SCHEMA public FROM PUBLIC;
GRANT USAGE ON SCHEMA public TO ledger_app;
SQL
