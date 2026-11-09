#!/usr/bin/env bash
# Happy-path P2P through Traefik: register, verify, fund via the ledger
# service token, transfer with a customer token. Used by CI compose.
set -euo pipefail

KEYCLOAK="http://localhost:8080/realms/senwallet"
API="https://localhost/api"
LEDGER="http://localhost:8002"
CURL=(curl -skS --fail-with-body)

token() {
  local body=$1
  curl -sS --fail-with-body \
    -H "Content-Type: application/x-www-form-urlencoded" \
    -d "$body" \
    "$KEYCLOAK/protocol/openid-connect/token" \
    | python3 -c "import json,sys; print(json.load(sys.stdin)['access_token'])"
}

CUSTOMER_TOKEN=$(token "grant_type=password&client_id=wallet-web&username=adama&password=password")
SERVICE_TOKEN=$(token "grant_type=client_credentials&client_id=wallet-service&client_secret=wallet-service-secret")

register() {
  local phone=$1
  "${CURL[@]}" -X POST "$API/customers" \
    -H "Content-Type: application/json" \
    -d "{\"phone\":\"$phone\"}"
}

verify() {
  local id=$1 otp=$2
  "${CURL[@]}" -X POST "$API/customers/$id/verification" \
    -H "Authorization: Bearer $CUSTOMER_TOKEN" \
    -H "Content-Type: application/json" \
    -d "{\"otp\":\"$otp\"}"
}

field() {
  python3 -c "import json,sys; print(json.load(sys.stdin)$1)"
}

ADAMA_REG=$(register "+221770000001")
ADAMA_ID=$(printf '%s' "$ADAMA_REG" | field "['id']")
ADAMA_OTP=$(printf '%s' "$ADAMA_REG" | field "['otp']")
ADAMA=$(verify "$ADAMA_ID" "$ADAMA_OTP")
ADAMA_WALLET=$(printf '%s' "$ADAMA" | field "['wallet_id']")
ADAMA_ACCOUNT=$(printf '%s' "$ADAMA" | field "['ledger_account_id']")
LEDGER_ID=$("${CURL[@]}" -H "Authorization: Bearer $CUSTOMER_TOKEN" \
  "$API/wallets/$ADAMA_WALLET" | field "['ledger_id']")

BINTA_REG=$(register "+221770000002")
BINTA_ID=$(printf '%s' "$BINTA_REG" | field "['id']")
BINTA_OTP=$(printf '%s' "$BINTA_REG" | field "['otp']")
BINTA=$(verify "$BINTA_ID" "$BINTA_OTP")
BINTA_PHONE="+221770000002"

SETTLEMENT=$("${CURL[@]}" -X POST "$LEDGER/ledger_accounts" \
  -H "Authorization: Bearer $SERVICE_TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"ledger_id\":\"$LEDGER_ID\",\"name\":\"Compensation smoke\",\"normal_balance\":\"debit\"}" \
  | field "['id']")

"${CURL[@]}" -X POST "$LEDGER/ledger_transactions" \
  -H "Authorization: Bearer $SERVICE_TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"ledger_id\":\"$LEDGER_ID\",\"external_id\":\"pispi-in:smoke\",\"status\":\"posted\",\"entries\":[
        {\"account_id\":\"$SETTLEMENT\",\"direction\":\"debit\",\"amount\":100000},
        {\"account_id\":\"$ADAMA_ACCOUNT\",\"direction\":\"credit\",\"amount\":100000}
      ]}" >/dev/null

TRANSFER=$("${CURL[@]}" -X POST "$API/transfers" \
  -H "Authorization: Bearer $CUSTOMER_TOKEN" \
  -H "Idempotency-Key: 3f1c9a52-7d0e-4b8a-9f21-6c2d5e8a1b44" \
  -H "Content-Type: application/json" \
  -d "{\"from_wallet_id\":\"$ADAMA_WALLET\",\"to_phone\":\"$BINTA_PHONE\",\"amount\":50000,\"currency\":\"XOF\"}")

STATUS=$(printf '%s' "$TRANSFER" | field "['status']")
test "$STATUS" = "posted"

REPLAY=$("${CURL[@]}" -X POST "$API/transfers" \
  -H "Authorization: Bearer $CUSTOMER_TOKEN" \
  -H "Idempotency-Key: 3f1c9a52-7d0e-4b8a-9f21-6c2d5e8a1b44" \
  -H "Content-Type: application/json" \
  -d "{\"from_wallet_id\":\"$ADAMA_WALLET\",\"to_phone\":\"$BINTA_PHONE\",\"amount\":50000,\"currency\":\"XOF\"}")

test "$(printf '%s' "$REPLAY" | field "['id']")" = "$(printf '%s' "$TRANSFER" | field "['id']")"

AVAILABLE=$("${CURL[@]}" -H "Authorization: Bearer $CUSTOMER_TOKEN" \
  "$API/wallets/$ADAMA_WALLET" | field "['balances']['available']")
test "$AVAILABLE" = "49500"

echo "gateway smoke: P2P posted, replayed, available=49500"
