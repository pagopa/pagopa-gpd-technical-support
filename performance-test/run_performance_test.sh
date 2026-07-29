#!/usr/bin/env bash

set -Eeuo pipefail

readonly SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# Non-sensitive execution parameters. TEST_TYPE, SCRIPT and DB_NAME have safe defaults.
ENVIRONMENT="${ENVIRONMENT:-}"
readonly TEST_TYPE="pilot"
SCRIPT="${SCRIPT:-reconciliation_workflow}"
DB_NAME="${DB_NAME:-k6}"
TEST_DAY="${TEST_DAY:-}"
POSITIONS_TO_CREATE="${POSITIONS_TO_CREATE:-100}"

# Execution flags.
PREPARE_ONLY="${PREPARE_ONLY:-false}"
SKIP_PREPARE="${SKIP_PREPARE:-false}"
ALLOW_FULL_DAY_PURGE="${ALLOW_FULL_DAY_PURGE:-false}"

# Secrets may be inherited from the shell, but named arguments passed to this script override them.
API_SUBSCRIPTION_KEY="${API_SUBSCRIPTION_KEY:-}"
APD_PASSWORD="${APD_PASSWORD:-}"
BIZ_COSMOS_KEY="${BIZ_COSMOS_KEY:-}"
RECONCILIATION_COSMOS_KEY="${RECONCILIATION_COSMOS_KEY:-}"

usage() {
  cat <<'USAGE'
Run GPD Technical Support reconciliation performance tests.

Usage:
  ./run_performance_test.sh \
    ENVIRONMENT=<local|dev|uat> \
    TEST_DAY=<YYYY-MM-DD> \
    [POSITIONS_TO_CREATE=<positive integer>] \
    APD_PASSWORD='<password>' \
    [SCRIPT=<script-name>] \
    [DB_NAME=<influx-db-name>] \
    [API_SUBSCRIPTION_KEY='<key>'] \
    [BIZ_COSMOS_KEY='<key>'] \
    [RECONCILIATION_COSMOS_KEY='<key>'] \
    [--prepare-only] \
    [--skip-prepare] \
    [--allow-full-day-purge]

Defaults:
  TEST_TYPE=pilot (fixed)
  SCRIPT=reconciliation_workflow
  DB_NAME=k6
  POSITIONS_TO_CREATE=100

Required secrets:
  APD_PASSWORD               Required when APD preparation is executed.
  API_SUBSCRIPTION_KEY       Required when k6 is executed.
  BIZ_COSMOS_KEY             Reserved for the Biz+ seed/validation steps.
  RECONCILIATION_COSMOS_KEY  Reserved for reconciliation validation steps.

Examples:
  ./run_performance_test.sh \
    ENVIRONMENT=dev \
    TEST_DAY=2026-07-13 \
    APD_PASSWORD='***' \
    --prepare-only

  ./run_performance_test.sh \
    ENVIRONMENT=uat \
    SCRIPT=reconciliation_workflow \
    DB_NAME=k6 \
    TEST_DAY=2026-07-13 \
    APD_PASSWORD='***' \
    --prepare-only \
    --allow-full-day-purge
USAGE
}

fail() {
  echo "ERROR: $*" >&2
  exit 1
}

set_named_argument() {
  local key="$1"
  local value="$2"

  case "$key" in
    ENVIRONMENT)
      ENVIRONMENT="$value"
      ;;
    SCRIPT)
      SCRIPT="$value"
      ;;
    DB_NAME)
      DB_NAME="$value"
      ;;
    TEST_DAY)
      TEST_DAY="$value"
      ;;
    POSITIONS_TO_CREATE)
      POSITIONS_TO_CREATE="$value"
      ;;  
    API_SUBSCRIPTION_KEY)
      API_SUBSCRIPTION_KEY="$value"
      ;;
    APD_PASSWORD)
      APD_PASSWORD="$value"
      ;;
    BIZ_COSMOS_KEY)
      BIZ_COSMOS_KEY="$value"
      ;;
    RECONCILIATION_COSMOS_KEY)
      RECONCILIATION_COSMOS_KEY="$value"
      ;;
    PREPARE_ONLY)
      PREPARE_ONLY="$value"
      ;;
    SKIP_PREPARE)
      SKIP_PREPARE="$value"
      ;;
    ALLOW_FULL_DAY_PURGE)
      ALLOW_FULL_DAY_PURGE="$value"
      ;;
    *)
      fail "Unknown named argument: $key"
      ;;
  esac
}

for argument in "$@"; do
  case "$argument" in
    --prepare-only)
      PREPARE_ONLY=true
      ;;
    --skip-prepare)
      SKIP_PREPARE=true
      ;;
    --allow-full-day-purge)
      ALLOW_FULL_DAY_PURGE=true
      ;;
    --help|-h)
      usage
      exit 0
      ;;
    *=*)
      argument_key="${argument%%=*}"
      argument_value="${argument#*=}"
      set_named_argument "$argument_key" "$argument_value"
      ;;
    *)
      fail "Unknown argument: $argument. Use KEY=VALUE syntax or a supported flag."
      ;;
  esac
done

[[ -n "$ENVIRONMENT" ]] || { usage; fail "ENVIRONMENT is required"; }
[[ "$ENVIRONMENT" =~ ^(local|dev|uat)$ ]] \
  || fail "ENVIRONMENT must be one of: local, dev, uat"
[[ -n "$SCRIPT" ]] || fail "SCRIPT cannot be blank"
[[ -n "$DB_NAME" ]] || fail "DB_NAME cannot be blank"
[[ -n "$TEST_DAY" ]] || fail "TEST_DAY is required"
[[ "$TEST_DAY" =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}$ ]] \
  || fail "TEST_DAY must use YYYY-MM-DD format"
[[ "$POSITIONS_TO_CREATE" =~ ^[1-9][0-9]*$ ]] \
  || fail "POSITIONS_TO_CREATE must be a positive integer"
(( POSITIONS_TO_CREATE >= 3 )) \
  || fail "POSITIONS_TO_CREATE must be at least 3 to cover VALID, EXPIRED and INVALID scenarios"  
(( POSITIONS_TO_CREATE <= 1000000 )) \
  || fail "POSITIONS_TO_CREATE cannot be greater than 1000000"
  
if (( POSITIONS_TO_CREATE <= 10 )); then
  VERBOSE_LOGS="true"
  LOG_MODE="VERBOSE"
else
  VERBOSE_LOGS="false"
  LOG_MODE="SUMMARY"
fi

for boolean_name in PREPARE_ONLY SKIP_PREPARE ALLOW_FULL_DAY_PURGE; do
  boolean_value="${!boolean_name}"
  [[ "$boolean_value" == "true" || "$boolean_value" == "false" ]] \
    || fail "$boolean_name must be true or false"
done

if [[ "$PREPARE_ONLY" == "true" && "$SKIP_PREPARE" == "true" ]]; then
  fail "--prepare-only and --skip-prepare are mutually exclusive"
fi

SCRIPT="${SCRIPT%.js}"

[[ -f "src/environments/${ENVIRONMENT}.environment.json" ]] \
  || fail "Missing environment file: src/environments/${ENVIRONMENT}.environment.json"
[[ -f "src/test-types/pilot.json" ]] \
  || fail "Missing test type file: src/test-types/pilot.json"
[[ -f "src/${SCRIPT}.js" ]] \
  || fail "Missing k6 script: src/${SCRIPT}.js"

if [[ "$SKIP_PREPARE" != "true" ]]; then
  [[ -n "$APD_PASSWORD" ]] || fail "APD_PASSWORD is required for APD preparation"
fi

if [[
  "$SKIP_PREPARE" != "true" &&
  "$ENVIRONMENT" != "local"
]]; then
  [[ -n "$BIZ_COSMOS_KEY" ]] \
    || fail "BIZ_COSMOS_KEY is required for Biz+ preparation"
fi

if [[ "$PREPARE_ONLY" != "true" ]]; then
  [[ -n "$API_SUBSCRIPTION_KEY" ]] \
    || fail \
      "API_SUBSCRIPTION_KEY is required when k6 is executed"

  if [[ "$ENVIRONMENT" != "local" ]]; then
    [[ -n "$RECONCILIATION_COSMOS_KEY" ]] \
      || fail \
        "RECONCILIATION_COSMOS_KEY is required for Step 3C"

    [[ -n "$APD_PASSWORD" ]] \
      || fail \
        "APD_PASSWORD is required for APD result validation"
  fi
fi

# Populated after the asynchronous trigger is accepted.
RECONCILIATION_EXECUTION_ID=""
RECONCILIATION_LOGICAL_RUN_KEY=""

# Docker Compose receives only internal lower-case variables. Secrets are never printed.
export env="$ENVIRONMENT"
export type="$TEST_TYPE"
export script="$SCRIPT"
export db_name="$DB_NAME"
export test_day="$TEST_DAY"
export positions_to_create="$POSITIONS_TO_CREATE"
export verbose_logs="$VERBOSE_LOGS"
export allow_full_day_purge="$ALLOW_FULL_DAY_PURGE"
export api_subscription_key="$API_SUBSCRIPTION_KEY"
export apd_password="$APD_PASSWORD"
export biz_cosmos_key="$BIZ_COSMOS_KEY"
export reconciliation_cosmos_key="$RECONCILIATION_COSMOS_KEY"
export reconciliation_execution_id="$RECONCILIATION_EXECUTION_ID"
export reconciliation_logical_run_key="$RECONCILIATION_LOGICAL_RUN_KEY"

stack_name="$(cd .. && basename "$PWD")-k6"
compose=(docker compose -p "$stack_name")
K6_LOG_FILE=""

cleanup_k6() {
  if [[ -n "$K6_LOG_FILE" ]]; then
    rm -f "$K6_LOG_FILE"
  fi

  "${compose[@]}" stop nginx >/dev/null 2>&1 || true
  "${compose[@]}" rm -sf k6 nginx >/dev/null 2>&1 || true
}
trap cleanup_k6 EXIT

validate_prepared_dataset() {
  echo "Validating the dataset reused by --skip-prepare..."

  "${compose[@]}" build apd-preparation

  "${compose[@]}" run --rm --no-deps \
    --entrypoint /bin/bash \
    -e "EXPECTED_ENVIRONMENT=${ENVIRONMENT}" \
    -e "EXPECTED_TEST_DAY=${TEST_DAY}" \
    -e "EXPECTED_POSITIONS=${POSITIONS_TO_CREATE}" \
    apd-preparation \
    -Eeuo pipefail \
    -c '
      readonly metadata_path="/work/apd-seed-metadata.json"
      readonly manifest_path="/work/apd-candidates.jsonl"

      [[ -s "$metadata_path" ]] || {
        echo "ERROR: prepared dataset metadata not found: $metadata_path" >&2
        exit 1
      }

      [[ -s "$manifest_path" ]] || {
        echo "ERROR: prepared APD candidate manifest not found: $manifest_path" >&2
        exit 1
      }

      actual_environment="$(jq -er ".environment" "$metadata_path")" || {
        echo "ERROR: invalid environment in $metadata_path" >&2
        exit 1
      }

      actual_test_day="$(jq -er ".testDay" "$metadata_path")" || {
        echo "ERROR: invalid testDay in $metadata_path" >&2
        exit 1
      }

      actual_positions="$(jq -er ".positions" "$metadata_path")" || {
        echo "ERROR: invalid positions in $metadata_path" >&2
        exit 1
      }

      [[ "$actual_environment" == "$EXPECTED_ENVIRONMENT" ]] || {
        echo "ERROR: prepared dataset environment mismatch: expected $EXPECTED_ENVIRONMENT, found $actual_environment" >&2
        exit 1
      }

      [[ "$actual_test_day" == "$EXPECTED_TEST_DAY" ]] || {
        echo "ERROR: prepared dataset TEST_DAY mismatch: expected $EXPECTED_TEST_DAY, found $actual_test_day" >&2
        exit 1
      }

      [[ "$actual_positions" == "$EXPECTED_POSITIONS" ]] || {
        echo "ERROR: prepared dataset position count mismatch: expected $EXPECTED_POSITIONS, found $actual_positions" >&2
        exit 1
      }

      manifest_count="$(wc -l < "$manifest_path" | tr -d "[:space:]")"

      [[ "$manifest_count" == "$EXPECTED_POSITIONS" ]] || {
        echo "ERROR: APD manifest count mismatch: expected $EXPECTED_POSITIONS, found $manifest_count" >&2
        exit 1
      }

      echo "PREPARED_DATASET_VALIDATION_PASSED"
      echo "  ENVIRONMENT=$actual_environment"
      echo "  TEST_DAY=$actual_test_day"
      echo "  POSITIONS=$actual_positions"
    '
}

if [[ "$ENVIRONMENT" == "local" ]]; then
  echo "Starting local APD PostgreSQL fixture..."
  "${compose[@]}" --profile local up -d apd-local

  for _ in $(seq 1 30); do
    if "${compose[@]}" exec -T apd-local pg_isready -U apduser -d apd >/dev/null 2>&1; then
      break
    fi
    sleep 2
  done

  "${compose[@]}" exec -T apd-local pg_isready -U apduser -d apd >/dev/null 2>&1 \
    || fail "Local APD PostgreSQL did not become ready"
fi

if [[ "$SKIP_PREPARE" != "true" ]]; then
  echo "============================================================"
  echo "Step 1 - APD TEST_DAY preparation"
  echo "ENVIRONMENT=$ENVIRONMENT"
  echo "TEST_TYPE=$TEST_TYPE"
  echo "TEST_DAY=$TEST_DAY"
  echo "POSITIONS_TO_CREATE=$POSITIONS_TO_CREATE"
  echo "LOG_MODE=$LOG_MODE"
  echo "ALLOW_FULL_DAY_PURGE=$ALLOW_FULL_DAY_PURGE"
  echo "============================================================"

  "${compose[@]}" build apd-preparation
  "${compose[@]}" run --rm apd-preparation
  
  if [[ "$ENVIRONMENT" != "local" ]]; then
    echo
    echo "============================================================"
    echo "Step 2 - Biz+ DONE event preparation"
    echo "ENVIRONMENT=$ENVIRONMENT"
    echo "TEST_DAY=$TEST_DAY"
    echo "POSITIONS_TO_CREATE=$POSITIONS_TO_CREATE"
    echo "============================================================"

    "${compose[@]}" build biz-preparation
    "${compose[@]}" run --rm biz-preparation
  else
    echo
    echo "Step 2 skipped in local environment: Cosmos emulator is not configured."
  fi
fi

if [[ "$SKIP_PREPARE" == "true" ]]; then
  validate_prepared_dataset
fi

if [[ "$PREPARE_ONLY" == "true" ]]; then
  echo "Data preparation completed. k6 execution skipped by --prepare-only."
  exit 0
fi

echo "============================================================"
echo "Launching k6"
echo "ENVIRONMENT=$ENVIRONMENT"
echo "TEST_TYPE=$TEST_TYPE"
echo "SCRIPT=$SCRIPT"
echo "TEST_DAY=$TEST_DAY"
echo "POSITIONS_TO_CREATE=$POSITIONS_TO_CREATE"
echo "LOG_MODE=$LOG_MODE"
echo "============================================================"

"${compose[@]}" up -d \
  --remove-orphans \
  --force-recreate \
  nginx

K6_LOG_FILE="$(mktemp)"

set +e
"${compose[@]}" run --rm k6 2>&1 | tee "$K6_LOG_FILE"
k6_exit_code="${PIPESTATUS[0]}"
set -e

(( k6_exit_code == 0 )) \
  || fail "k6 execution failed with exit code ${k6_exit_code}"

RECONCILIATION_EXECUTION_ID="$(
  sed -nE \
    's/.*RECONCILIATION_EXECUTION_ID=([^"[:space:]]+).*/\1/p' \
    "$K6_LOG_FILE" |
    tail -n 1 |
    tr -d '\r\n'
)"

RECONCILIATION_LOGICAL_RUN_KEY="$(
  sed -nE \
    's/.*RECONCILIATION_LOGICAL_RUN_KEY=([^"[:space:]]+).*/\1/p' \
    "$K6_LOG_FILE" |
    tail -n 1 |
    tr -d '\r\n'
)"

[[ -n "$RECONCILIATION_EXECUTION_ID" ]] \
  || fail "Unable to extract reconciliation executionId from k6 output"

[[ -n "$RECONCILIATION_LOGICAL_RUN_KEY" ]] \
  || fail "Unable to extract reconciliation logicalRunKey from k6 output"

export reconciliation_execution_id="$RECONCILIATION_EXECUTION_ID"
export reconciliation_logical_run_key="$RECONCILIATION_LOGICAL_RUN_KEY"

if [[ "$ENVIRONMENT" != "local" ]]; then
  echo
  echo "============================================================"
  echo "Step 3C - Reconciliation result validation"
  echo "ENVIRONMENT=$ENVIRONMENT"
  echo "TEST_DAY=$TEST_DAY"
  echo "POSITIONS_TO_CREATE=$POSITIONS_TO_CREATE"
  echo "LOG_MODE=$LOG_MODE"
  echo "============================================================"

  "${compose[@]}" build \
    reconciliation-validation \
    apd-result-validation

  "${compose[@]}" run --rm \
    reconciliation-validation

  "${compose[@]}" run --rm \
    apd-result-validation
else
  echo
  echo "Step 3C skipped in local environment:"
  echo "reconciliation Cosmos is not configured."
fi

echo
echo "PERFORMANCE_WORKFLOW_VALIDATION_PASSED"