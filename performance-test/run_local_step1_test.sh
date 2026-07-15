#!/usr/bin/env bash

set -Eeuo pipefail

readonly SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

APD_PASSWORD="${APD_PASSWORD:-local-apd-password}"
TEST_DAY="${TEST_DAY:-$(date -d '2 days ago' +%F)}"

# These variables are needed only because this script also invokes Docker Compose directly
# to initialize and validate the local PostgreSQL fixture.
export env=local
export type=pilot
export script=reconciliation_workflow
export db_name=k6
export test_day="$TEST_DAY"
export allow_full_day_purge=false
export apd_password="$APD_PASSWORD"
export api_subscription_key=""
export biz_cosmos_key=""
export reconciliation_cosmos_key=""

stack_name="$(cd .. && basename "$PWD")-k6"
compose=(docker compose -p "$stack_name")

cleanup() {
  "${compose[@]}" --profile local down -v --remove-orphans >/dev/null 2>&1 || true
}
trap cleanup EXIT

cleanup

"${compose[@]}" --profile local up -d apd-local
for _ in $(seq 1 30); do
  if "${compose[@]}" exec -T apd-local pg_isready -U apduser -d apd >/dev/null 2>&1; then
    break
  fi
  sleep 2
done

"${compose[@]}" exec -T apd-local pg_isready -U apduser -d apd >/dev/null 2>&1 \
  || { echo "Local APD PostgreSQL did not become ready" >&2; exit 1; }

"${compose[@]}" exec -T apd-local \
  psql -X -v ON_ERROR_STOP=1 -U apduser -d apd \
  -v "test_day=$TEST_DAY" \
  -f /local/reset-and-seed.sql

echo
echo "Test A - safe mode must delete owned data and stop on foreign candidates"
set +e
./run_performance_test.sh \
  ENVIRONMENT=local \
  TEST_TYPE=pilot \
  SCRIPT=reconciliation_workflow \
  DB_NAME=k6 \
  TEST_DAY="$TEST_DAY" \
  APD_PASSWORD="$APD_PASSWORD" \
  --prepare-only
safe_exit_code=$?
set -e

if [[ "$safe_exit_code" -ne 20 ]]; then
  echo "Expected exit code 20, received $safe_exit_code" >&2
  exit 1
fi

"${compose[@]}" exec -T apd-local \
  psql -X -v ON_ERROR_STOP=1 -U apduser -d apd \
  -v "test_day=$TEST_DAY" \
  -f /local/assert-safe-mode.sql

echo
echo "Test B - explicit purge flag must remove foreign candidate graphs"
./run_performance_test.sh \
  ENVIRONMENT=local \
  TEST_TYPE=pilot \
  SCRIPT=reconciliation_workflow \
  DB_NAME=k6 \
  TEST_DAY="$TEST_DAY" \
  APD_PASSWORD="$APD_PASSWORD" \
  --prepare-only \
  --allow-full-day-purge

"${compose[@]}" exec -T apd-local \
  psql -X -v ON_ERROR_STOP=1 -U apduser -d apd \
  -v "test_day=$TEST_DAY" \
  -f /local/assert-full-purge.sql

echo
echo "LOCAL_STEP1_TEST_PASSED"
