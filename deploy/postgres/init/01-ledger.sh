#!/usr/bin/env bash
# Runs once, on first start with an empty volume.
set -euo pipefail

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres \
    -v ledger_password="$LEDGER_DB_PASSWORD" <<'SQL'
CREATE ROLE ledger_app LOGIN PASSWORD :'ledger_password';
CREATE DATABASE ledger OWNER ledger_app;
REVOKE ALL ON DATABASE ledger FROM PUBLIC;
SQL
