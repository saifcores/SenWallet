#!/usr/bin/env bash
# payments_owner owns the schema and runs Flyway; payments_app is what the
# service connects as (same split as the wallet, ADR-25).
set -euo pipefail

owner_password="${PAYMENTS_OWNER_PASSWORD:-payments_owner}"
app_password="${PAYMENTS_DB_PASSWORD:-payments_app}"

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres \
    -v owner_password="$owner_password" \
    -v app_password="$app_password" <<'SQL'
CREATE ROLE payments_owner LOGIN PASSWORD :'owner_password';
CREATE ROLE payments_app LOGIN PASSWORD :'app_password';
CREATE DATABASE payments OWNER payments_owner;
REVOKE ALL ON DATABASE payments FROM PUBLIC;
GRANT CONNECT ON DATABASE payments TO payments_app;
SQL

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname payments <<'SQL'
ALTER SCHEMA public OWNER TO payments_owner;
REVOKE ALL ON SCHEMA public FROM PUBLIC;
GRANT USAGE ON SCHEMA public TO payments_app;
SQL
