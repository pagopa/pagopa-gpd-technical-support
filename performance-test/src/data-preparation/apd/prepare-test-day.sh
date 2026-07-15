#!/usr/bin/env bash

set -Eeuo pipefail

readonly SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly SQL_DIR="${SCRIPT_DIR}/sql"

VARS="${VARS:-}"
TEST_DAY="${TEST_DAY:-}"
POSITIONS_TO_CREATE="${POSITIONS_TO_CREATE:-100}"
ALLOW_FULL_DAY_PURGE="${ALLOW_FULL_DAY_PURGE:-false}"
APD_PASSWORD="${APD_PASSWORD:-}"

usage() {
  cat <<'USAGE'
Prepare an APD day for GPD Technical Support performance tests.

Required environment variables:
  VARS                    Environment JSON path.
  TEST_DAY                Processing day in YYYY-MM-DD format.
  APD_PASSWORD            APD database password.

Optional environment variables:
  ALLOW_FULL_DAY_PURGE    false by default. Set to true only to delete complete
                          foreign candidate payment-position graphs for TEST_DAY.
USAGE
}

fail() {
  local exit_code="$1"
  shift
  echo "ERROR: $*" >&2
  exit "$exit_code"
}

json_value() {
  local expression="$1"
  local value

  value="$(jq -r "$expression" "$VARS")" \
    || fail 10 "Unable to read environment configuration expression: $expression"

  [[ "$value" != "null" ]] \
    || fail 10 "Missing environment configuration value for expression: $expression"

  printf '%s\n' "$value"
}

if [[ "${1:-}" == "--help" || "${1:-}" == "-h" ]]; then
  usage
  exit 0
fi

command -v jq >/dev/null 2>&1 || fail 10 "jq is required"
command -v psql >/dev/null 2>&1 || fail 10 "psql is required"
[[ -n "$VARS" ]] || fail 10 "VARS is required"
[[ -f "$VARS" ]] || fail 10 "Environment file not found: $VARS"
[[ -n "$TEST_DAY" ]] || fail 10 "TEST_DAY is required"
[[ "$TEST_DAY" =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}$ ]] \
  || fail 10 "TEST_DAY must use YYYY-MM-DD format"
[[ -n "$APD_PASSWORD" ]] || fail 10 "APD_PASSWORD is required"
[[ "$POSITIONS_TO_CREATE" =~ ^[1-9][0-9]*$ ]] \
  || fail 10 "POSITIONS_TO_CREATE must be a positive integer"
(( POSITIONS_TO_CREATE >= 3 )) \
  || fail 10 "POSITIONS_TO_CREATE must be at least 3 to cover VALID, EXPIRED and INVALID scenarios"  
ALLOW_FULL_DAY_PURGE="$(printf '%s' "$ALLOW_FULL_DAY_PURGE" | tr '[:upper:]' '[:lower:]')"
[[ "$ALLOW_FULL_DAY_PURGE" == "true" || "$ALLOW_FULL_DAY_PURGE" == "false" ]] \
  || fail 10 "ALLOW_FULL_DAY_PURGE must be true or false"

ENVIRONMENT="$(json_value '.environment[0].env')"
APD_JDBC_URL="$(json_value '.environment[0].apd.jdbcUrl')"
APD_USERNAME="$(json_value '.environment[0].apd.username')"
APD_SCHEMA="$(json_value '.environment[0].apd.schema')"
SERVICE_TYPES="$(json_value '.environment[0].reconciliation.serviceTypes | join(",")')"
MIN_PROCESSING_DELAY_DAYS="$(json_value '.environment[0].reconciliation.minProcessingDelayDays')"
MARKER_PREFIX="$(json_value '.environment[0].testData.performanceDataPrefix')"
ORGANIZATION_FISCAL_CODE="$(
  json_value '.environment[0].testData.organizationFiscalCode'
)"

RUN_ID="$(date -u +%Y%m%d%H%M%S)"
DATA_MUTATION_ENABLED="$(json_value '.environment[0].safety.dataMutationEnabled')"
FULL_DAY_PURGE_ENABLED="$(json_value '.environment[0].safety.fullDayPurgeEnabled')"

[[ "$DATA_MUTATION_ENABLED" == "true" ]] \
  || fail 17 "APD data mutation is disabled for environment '$ENVIRONMENT'"

if [[ "$ALLOW_FULL_DAY_PURGE" == "true" && "$FULL_DAY_PURGE_ENABLED" != "true" ]]; then
  fail 18 "Full-day purge is disabled for environment '$ENVIRONMENT'"
fi

# Convert the configured JDBC URL into standard libpq variables. The APD URLs
# currently use the supported form jdbc:postgresql://host:port/database?params.
JDBC_WITHOUT_SCHEME="${APD_JDBC_URL#jdbc:postgresql://}"
[[ "$JDBC_WITHOUT_SCHEME" != "$APD_JDBC_URL" ]] \
  || fail 10 "Unsupported APD JDBC URL: $APD_JDBC_URL"

CONNECTION_PART="${JDBC_WITHOUT_SCHEME%%\?*}"
QUERY_PART=""
if [[ "$JDBC_WITHOUT_SCHEME" == *"?"* ]]; then
  QUERY_PART="${JDBC_WITHOUT_SCHEME#*\?}"
fi

HOST_PORT="${CONNECTION_PART%%/*}"
PGDATABASE="${CONNECTION_PART#*/}"

if [[ "$HOST_PORT" == *":"* ]]; then
  PGHOST="${HOST_PORT%%:*}"
  PGPORT="${HOST_PORT##*:}"
else
  PGHOST="$HOST_PORT"
  PGPORT="5432"
fi

PGSSLMODE=""
if [[ -n "$QUERY_PART" ]]; then
  PGSSLMODE="$(printf '%s' "$QUERY_PART" | tr '&' '\n' | sed -n 's/^sslmode=//p' | head -n 1)"
fi

export PGHOST PGPORT PGDATABASE
export PGUSER="$APD_USERNAME"
export PGPASSWORD="$APD_PASSWORD"
if [[ -n "$PGSSLMODE" ]]; then
  export PGSSLMODE
fi

PSQL=(psql -X -v ON_ERROR_STOP=1)
PSQL_VARS=(
  -v "test_day=${TEST_DAY}"
  -v "service_types=${SERVICE_TYPES}"
  -v "marker_prefix=${MARKER_PREFIX}"
  -v "schema_name=${APD_SCHEMA}"
  -v "min_processing_delay_days=${MIN_PROCESSING_DELAY_DAYS}"
  -v "positions_to_create=${POSITIONS_TO_CREATE}"
  -v "organization_fiscal_code=${ORGANIZATION_FISCAL_CODE}"
  -v "run_id=${RUN_ID}"
)

run_sql() {
  local file="$1"
  "${PSQL[@]}" "${PSQL_VARS[@]}" -f "$file"
}

scalar_sql() {
  local file="$1"
  "${PSQL[@]}" "${PSQL_VARS[@]}" -qAt -f "$file" | tr -d '[:space:]'
}

echo "============================================================"
echo "APD performance-test day preparation"
echo "ENVIRONMENT=${ENVIRONMENT}"
echo "TEST_DAY=${TEST_DAY}"
echo "APD_HOST=${PGHOST}"
echo "APD_DATABASE=${PGDATABASE}"
echo "APD_USERNAME=${PGUSER}"
echo "APD_SCHEMA=${APD_SCHEMA}"
echo "SERVICE_TYPES=${SERVICE_TYPES}"
echo "MARKER_PREFIX=${MARKER_PREFIX}"
echo "ALLOW_FULL_DAY_PURGE=${ALLOW_FULL_DAY_PURGE}"
echo "============================================================"

run_sql "${SQL_DIR}/preflight.sql"

echo
echo "Cleaning only data owned by previous GPDTS performance runs..."
run_sql "${SQL_DIR}/cleanup-own-data.sql"

FOREIGN_COUNT="$(scalar_sql "${SQL_DIR}/count-foreign-candidates.sql")"
[[ "$FOREIGN_COUNT" =~ ^[0-9]+$ ]] \
  || fail 10 "Unable to read foreign candidate count: '$FOREIGN_COUNT'"

if (( FOREIGN_COUNT > 0 )); then
  echo
  echo "TEST_DAY_NOT_CLEAN: found ${FOREIGN_COUNT} foreign reconciliation candidates."
  run_sql "${SQL_DIR}/inspect-foreign-candidates.sql"

  if [[ "$ALLOW_FULL_DAY_PURGE" != "true" ]]; then
    echo
    echo "Preparation stopped without deleting foreign data."
    echo "Choose another TEST_DAY or rerun with --allow-full-day-purge."
    exit 20
  fi

  echo
  echo "WARNING: explicit full-day purge enabled."
  echo "Deleting complete APD graphs for payment positions contributing foreign candidates..."
  run_sql "${SQL_DIR}/purge-full-day-candidates.sql"
fi

REMAINING_COUNT="$(scalar_sql "${SQL_DIR}/count-all-candidates.sql")"
[[ "$REMAINING_COUNT" =~ ^[0-9]+$ ]] \
  || fail 10 "Unable to read remaining candidate count: '$REMAINING_COUNT'"

if (( REMAINING_COUNT > 0 )); then
  fail 30 "TEST_DAY still contains ${REMAINING_COUNT} reconciliation candidates after preparation"
fi

echo
echo "TEST_DAY_READY: ${TEST_DAY} contains no reconciliation candidates."

echo
echo "Seeding APD performance data..."
echo "RUN_ID=${RUN_ID}"
echo "POSITIONS_TO_CREATE=${POSITIONS_TO_CREATE}"
echo "ORGANIZATION_FISCAL_CODE=${ORGANIZATION_FISCAL_CODE}"

run_sql "${SQL_DIR}/seed-performance-data.sql"

echo
echo "Validating APD performance data..."

run_sql "${SQL_DIR}/validate-performance-data.sql"

echo
echo "APD_TEST_DATA_READY:"
echo "  TEST_DAY=${TEST_DAY}"
echo "  RUN_ID=${RUN_ID}"
echo "  POSITIONS=${POSITIONS_TO_CREATE}"
