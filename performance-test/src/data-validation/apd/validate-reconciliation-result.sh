#!/usr/bin/env bash

set -Eeuo pipefail

readonly SCRIPT_DIR="$(
  cd "$(dirname "${BASH_SOURCE[0]}")" &&
    pwd
)"

readonly SQL_DIR="${SCRIPT_DIR}/sql"
readonly METADATA_PATH="/work/apd-seed-metadata.json"

VARS="${VARS:-}"
APD_PASSWORD="${APD_PASSWORD:-}"
VERBOSE_LOGS="${VERBOSE_LOGS:-false}"
EXPECTED_ENVIRONMENT="${EXPECTED_ENVIRONMENT:-}"
EXPECTED_TEST_DAY="${EXPECTED_TEST_DAY:-}"
EXPECTED_POSITIONS="${EXPECTED_POSITIONS:-}"

fail() {
  local exit_code="$1"
  shift

  echo "ERROR: $*" >&2
  exit "$exit_code"
}

json_value() {
  local expression="$1"
  local value

  value="$(jq -er "$expression" "$VARS")" \
    || fail 40 \
      "Unable to read environment configuration: ${expression}"

  [[ -n "$value" && "$value" != "null" ]] \
    || fail 40 \
      "Missing environment value: ${expression}"

  printf '%s\n' "$value"
}

command -v jq >/dev/null 2>&1 \
  || fail 40 "jq is required"

command -v psql >/dev/null 2>&1 \
  || fail 40 "psql is required"

[[ -n "$VARS" ]] \
  || fail 40 "VARS is required"

[[ -f "$VARS" ]] \
  || fail 40 "Environment file not found: ${VARS}"

[[ -f "$METADATA_PATH" ]] \
  || fail 40 \
    "Metadata file not found: ${METADATA_PATH}"

[[ -n "$APD_PASSWORD" ]] \
  || fail 40 "APD_PASSWORD is required"

[[ -n "$EXPECTED_ENVIRONMENT" ]] \
  || fail 40 "EXPECTED_ENVIRONMENT is required"

[[ -n "$EXPECTED_TEST_DAY" ]] \
  || fail 40 "EXPECTED_TEST_DAY is required"

[[ "$EXPECTED_TEST_DAY" =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}$ ]] \
  || fail 40 "EXPECTED_TEST_DAY must use YYYY-MM-DD format"

[[ "$EXPECTED_POSITIONS" =~ ^[1-9][0-9]*$ ]] \
  || fail 40 "EXPECTED_POSITIONS must be a positive integer"

VERBOSE_LOGS="$(
  printf '%s' "$VERBOSE_LOGS" |
    tr '[:upper:]' '[:lower:]'
)"

[[ "$VERBOSE_LOGS" == "true" ||
   "$VERBOSE_LOGS" == "false" ]] \
  || fail 40 \
    "VERBOSE_LOGS must be true or false"

METADATA_ENVIRONMENT="$(
  jq -er '.environment' "$METADATA_PATH"
)" || fail 40 \
  "Missing or invalid environment in ${METADATA_PATH}"

TEST_DAY="$(
  jq -er '.testDay' "$METADATA_PATH"
)" || fail 40 \
  "Missing or invalid testDay in ${METADATA_PATH}"

RUN_ID="$(
  jq -er '.runId' "$METADATA_PATH"
)" || fail 40 \
  "Missing or invalid runId in ${METADATA_PATH}"

POSITIONS_TO_CREATE="$(
  jq -er '.positions' "$METADATA_PATH"
)" || fail 40 \
  "Missing or invalid positions in ${METADATA_PATH}"

[[ "$TEST_DAY" =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}$ ]] \
  || fail 40 \
    "Invalid TEST_DAY in metadata: ${TEST_DAY}"

[[ -n "$RUN_ID" ]] \
  || fail 40 \
    "RUN_ID cannot be empty"

[[ "$POSITIONS_TO_CREATE" =~ ^[1-9][0-9]*$ ]] \
  || fail 40 \
    "POSITIONS_TO_CREATE must be a positive integer"

[[ "$METADATA_ENVIRONMENT" == "$EXPECTED_ENVIRONMENT" ]] \
  || fail 40 \
    "Prepared dataset environment mismatch: expected ${EXPECTED_ENVIRONMENT}, found ${METADATA_ENVIRONMENT}"

[[ "$TEST_DAY" == "$EXPECTED_TEST_DAY" ]] \
  || fail 40 \
    "Prepared dataset TEST_DAY mismatch: expected ${EXPECTED_TEST_DAY}, found ${TEST_DAY}"

[[ "$POSITIONS_TO_CREATE" == "$EXPECTED_POSITIONS" ]] \
  || fail 40 \
    "Prepared dataset position count mismatch: expected ${EXPECTED_POSITIONS}, found ${POSITIONS_TO_CREATE}"

CONFIG_ENVIRONMENT="$(
  json_value '.environment[0].env'
)"

[[ "$CONFIG_ENVIRONMENT" == "$EXPECTED_ENVIRONMENT" ]] \
  || fail 40 \
    "Environment configuration mismatch: expected ${EXPECTED_ENVIRONMENT}, found ${CONFIG_ENVIRONMENT}"

APD_JDBC_URL="$(
  json_value '.environment[0].apd.jdbcUrl'
)"

APD_USERNAME="$(
  json_value '.environment[0].apd.username'
)"

APD_SCHEMA="$(
  json_value '.environment[0].apd.schema'
)"

MARKER_PREFIX="$(
  json_value \
    '.environment[0].testData.performanceDataPrefix'
)"

JDBC_WITHOUT_SCHEME="${APD_JDBC_URL#jdbc:postgresql://}"

[[ "$JDBC_WITHOUT_SCHEME" != "$APD_JDBC_URL" ]] \
  || fail 40 \
    "Unsupported APD JDBC URL: ${APD_JDBC_URL}"

CONNECTION_PART="${JDBC_WITHOUT_SCHEME%%\?*}"
QUERY_PART=""

if [[ "$JDBC_WITHOUT_SCHEME" == *"?"* ]]; then
  QUERY_PART="${JDBC_WITHOUT_SCHEME#*\?}"
fi

HOST_PORT="${CONNECTION_PART%%/*}"
PGDATABASE="${CONNECTION_PART#*/}"

[[ -n "$HOST_PORT" && -n "$PGDATABASE" ]] \
  || fail 40 \
    "Unable to parse APD JDBC URL: ${APD_JDBC_URL}"

if [[ "$HOST_PORT" == *":"* ]]; then
  PGHOST="${HOST_PORT%%:*}"
  PGPORT="${HOST_PORT##*:}"
else
  PGHOST="$HOST_PORT"
  PGPORT="5432"
fi

PGSSLMODE=""

if [[ -n "$QUERY_PART" ]]; then
  PGSSLMODE="$(
    printf '%s' "$QUERY_PART" |
      tr '&' '\n' |
      sed -n 's/^sslmode=//p' |
      head -n 1
  )"
fi

export PGHOST
export PGPORT
export PGDATABASE
export PGUSER="$APD_USERNAME"
export PGPASSWORD="$APD_PASSWORD"

if [[ -n "$PGSSLMODE" ]]; then
  export PGSSLMODE
fi

if [[ "$VERBOSE_LOGS" == "true" ]]; then
  LOG_MODE="VERBOSE"
else
  LOG_MODE="SUMMARY"
fi

echo "============================================================"
echo "APD reconciliation result validation"
echo "ENVIRONMENT=${CONFIG_ENVIRONMENT}"
echo "TEST_DAY=${TEST_DAY}"
echo "RUN_ID=${RUN_ID}"
echo "POSITIONS=${POSITIONS_TO_CREATE}"
echo "APD_HOST=${PGHOST}"
echo "APD_DATABASE=${PGDATABASE}"
echo "APD_SCHEMA=${APD_SCHEMA}"
echo "LOG_MODE=${LOG_MODE}"
echo "============================================================"

psql \
  -X \
  -v ON_ERROR_STOP=1 \
  -v "test_day=${TEST_DAY}" \
  -v "run_id=${RUN_ID}" \
  -v "positions_to_create=${POSITIONS_TO_CREATE}" \
  -v "schema_name=${APD_SCHEMA}" \
  -v "marker_prefix=${MARKER_PREFIX}" \
  -v "verbose_logs=${VERBOSE_LOGS}" \
  -f "${SQL_DIR}/validate-reconciliation-result.sql"