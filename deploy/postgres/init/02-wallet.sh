#!/usr/bin/env bash
# wallet_owner owns the schema and runs Flyway; wallet_app is what the service
# connects as (same split as the ledger, ADR-20).
set -euo pipefail

owner_password="${WALLET_OWNER_PASSWORD:-wallet_owner}"
app_password="${WALLET_DB_PASSWORD:-wallet_app}"

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres \
    -v owner_password="$owner_password" \
    -v app_password="$app_password" <<'SQL'
CREATE ROLE wallet_owner LOGIN PASSWORD :'owner_password';
CREATE ROLE wallet_app LOGIN PASSWORD :'app_password';
CREATE DATABASE wallet OWNER wallet_owner;
REVOKE ALL ON DATABASE wallet FROM PUBLIC;
GRANT CONNECT ON DATABASE wallet TO wallet_app;
SQL

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname wallet <<'SQL'
ALTER SCHEMA public OWNER TO wallet_owner;
REVOKE ALL ON SCHEMA public FROM PUBLIC;
GRANT USAGE ON SCHEMA public TO wallet_app;
SQL
