# GPD Technical Support reconciliation performance tests

This folder contains the k6 workflow and the external tools used to prepare, execute, and validate debt-position status reconciliation tests.

Supported environments:

```text
local
dev
uat
```

## Purpose and execution model

The workflow validates the complete reconciliation path:

```text
APD candidates
    -> Biz+ DONE events
    -> asynchronous reconciliation trigger
    -> GPD PAY calls
    -> reconciliation reports in Cosmos DB
    -> final APD state validation
```

The k6 execution profile is fixed to:

```text
src/test-types/pilot.json
```

It always uses one virtual user and one iteration, so k6 sends exactly one asynchronous reconciliation request.

The load is controlled only by:

```text
POSITIONS_TO_CREATE
```

Increasing k6 virtual users or iterations is not supported because it would start concurrent reconciliation runs for the same test day and make the results unreliable.

## Workflow

A complete `dev` or `uat` execution performs these steps:

1. Validate the selected environment and input parameters.
2. Remove only data created by previous GPD Technical Support performance runs.
3. Stop safely if unrelated reconciliation candidates exist on `TEST_DAY`.
4. Seed APD payment positions, payment options, and transfers.
5. Export the APD candidate manifest and run metadata.
6. Create matching Biz+ events in `DONE` state.
7. Send one asynchronous reconciliation request through k6.
8. Extract the returned `executionId` and `logicalRunKey`.
9. Poll and validate the exact Cosmos DB run returned by the trigger.
10. Validate all reconciliation reports for that `executionId`.
11. Validate the final APD payment-position and payment-option states.

The final success marker is:

```text
PERFORMANCE_WORKFLOW_VALIDATION_PASSED
```

## Generated dataset

For `N = POSITIONS_TO_CREATE`, the APD seed creates:

- `max(1, floor(N / 100))` positions in `EXPIRED`;
- `max(1, floor(N / 100))` positions in `INVALID`;
- all remaining positions in `VALID`.

Examples:

Examples:

- **10 positions:** 8 `VALID`, 1 `EXPIRED`, 1 `INVALID`.
- **100 positions:** 98 `VALID`, 1 `EXPIRED`, 1 `INVALID`.
- **1,000 positions:** 980 `VALID`, 10 `EXPIRED`, 10 `INVALID`.
- **10,000 positions:** 9,800 `VALID`, 100 `EXPIRED`, 100 `INVALID`.

Every seeded position has one unpaid payment option and one unreported transfer.

For `dev` and `uat`, the Biz+ preparation step creates one matching `DONE` event for every APD candidate. The fee values are rotated through:

```text
0
1
0.45
1.0
```

Expected reconciliation results:

- `VALID` positions are recovered through PAY and become `PAID / PO_PAID`;
- `EXPIRED` positions remain `EXPIRED / PO_UNPAID` and are reported as `MANUAL_REQUIRED`;
- `INVALID` positions remain `INVALID / PO_UNPAID` and are reported as `MANUAL_REQUIRED`.

## Prerequisites

The machine or runner executing the tests must provide:

- Docker with the Docker Compose plugin;
- Bash;
- access to the selected APD and Cosmos DB endpoints;
- access to the environment API endpoint;
- internal DNS and network connectivity where required;
- the secrets listed below.

Make the scripts executable when needed:

```bash
chmod +x \
  run_performance_test.sh \
  run_local_apd_safety_test.sh \
  src/data-preparation/apd/prepare-test-day.sh \
  src/data-validation/apd/validate-reconciliation-result.sh
```

Display the command help with:

```bash
./run_performance_test.sh --help
```

## Execution parameters

Arguments must use `KEY=VALUE` syntax. Flags are passed without a value.

### Required parameters

**`ENVIRONMENT`**

Target environment. Allowed values:

```text
local
dev
uat
```

No default value is provided.

**`TEST_DAY`**

Processing day in `YYYY-MM-DD` format. Use a dedicated day that satisfies the configured minimum processing delay.

No default value is provided.

### Optional parameters

**`POSITIONS_TO_CREATE`**

Number of positions to seed.

```text
Default: 100
Minimum: 3
Maximum: 1,000,000
```

**`SCRIPT`**

k6 script name under `src/`. The optional `.js` suffix is removed automatically.

```text
Default: reconciliation_workflow
```

**`DB_NAME`**

InfluxDB database name used by the k6 output proxy.

```text
Default: k6
```

### Optional flags

**`--prepare-only`**

Runs APD preparation and, for `dev` or `uat`, Biz+ preparation without triggering reconciliation.

**`--skip-prepare`**

Reuses an already prepared dataset after validating its environment, day, position count, and manifest size.

**`--allow-full-day-purge`**

Allows deletion of unrelated candidate graphs on `TEST_DAY`, but only when the selected environment configuration also enables full-day purge.

`TEST_TYPE` is not an execution parameter. It is fixed internally to:

```text
pilot
```

Logging is selected automatically:

- `POSITIONS_TO_CREATE <= 10`: `VERBOSE`;
- `POSITIONS_TO_CREATE > 10`: `SUMMARY`.

`--prepare-only` and `--skip-prepare` are mutually exclusive.

## Secrets

Secrets must not be stored in the environment JSON files.

```text
API_SUBSCRIPTION_KEY
APD_PASSWORD
BIZ_COSMOS_KEY
RECONCILIATION_COSMOS_KEY
```

Prefer exporting secrets or injecting them through the CI runner instead of placing them directly in shell history.

```bash
export API_SUBSCRIPTION_KEY='<apim-subscription-key>'
export APD_PASSWORD='<apd-password>'
export BIZ_COSMOS_KEY='<biz-cosmos-key>'
export RECONCILIATION_COSMOS_KEY='<reconciliation-cosmos-key>'
```

Required secrets by execution mode:

Required secrets depend on the execution mode:

- **Local APD preparation**

  ```text
  APD_PASSWORD
  ```

- **DEV or UAT preparation only**

  ```text
  APD_PASSWORD
  BIZ_COSMOS_KEY
  ```

- **Complete DEV or UAT execution**

  ```text
  API_SUBSCRIPTION_KEY
  APD_PASSWORD
  BIZ_COSMOS_KEY
  RECONCILIATION_COSMOS_KEY
  ```

- **DEV or UAT execution with `--skip-prepare`**

  ```text
  API_SUBSCRIPTION_KEY
  APD_PASSWORD
  RECONCILIATION_COSMOS_KEY
  ```

`BIZ_COSMOS_KEY` is not required with `--skip-prepare` because the Biz+ seed step is skipped.

## APD safety behavior

The default cleanup removes only data owned by previous performance runs:

```text
payment_position.iupd LIKE 'GPDTS_PERF_%'
```

APD sequences are never reset or altered.

After owned-data cleanup, the preparation step counts reconciliation candidates using the same selection criteria as the application reader.

When unrelated candidates exist:

- they are not deleted;
- a summary is printed;
- the command exits with code `20`;
- the API trigger is not executed.

Choose another `TEST_DAY`, or use `--allow-full-day-purge` only on a dedicated test day and after reviewing the affected records.

The explicit full-day purge removes the complete APD graph for every unrelated payment position contributing at least one candidate:

```text
transfer_metadata
payment_option_metadata
transfer
payment_option
payment_position
```

The purge is executed only when both conditions are true:

1. `--allow-full-day-purge` was supplied;
2. `safety.fullDayPurgeEnabled` is `true` in the selected environment file.

## Practical executions

### Local APD safety verification

This automated test starts a temporary PostgreSQL 15 instance and verifies both APD safety branches:

```bash
./run_local_apd_safety_test.sh
```

It verifies that:

- safe mode removes only owned performance data and stops on unrelated candidates;
- explicit purge removes unrelated candidate graphs;
- unrelated non-candidate positions are preserved.

Expected output:

```text
LOCAL_APD_SAFETY_TEST_PASSED
```

The local database and volume are removed automatically.

### Local preparation only

Local mode prepares APD data but skips Biz+ and Cosmos DB operations because no Cosmos emulator is configured.

```bash
export APD_PASSWORD='local-apd-password'

./run_performance_test.sh \
  ENVIRONMENT=local \
  TEST_DAY="$(date -d '2 days ago' +%F)" \
  POSITIONS_TO_CREATE=10 \
  --prepare-only
```

### DEV preparation only

```bash
export APD_PASSWORD='<dev-apd-password>'
export BIZ_COSMOS_KEY='<dev-biz-cosmos-key>'

./run_performance_test.sh \
  ENVIRONMENT=dev \
  TEST_DAY=2026-07-13 \
  POSITIONS_TO_CREATE=10000 \
  --prepare-only
```

This prepares APD and Biz+ but does not invoke the reconciliation API.

### Complete DEV run

```bash
export API_SUBSCRIPTION_KEY='<dev-apim-subscription-key>'
export APD_PASSWORD='<dev-apd-password>'
export BIZ_COSMOS_KEY='<dev-biz-cosmos-key>'
export RECONCILIATION_COSMOS_KEY='<dev-reconciliation-cosmos-key>'

./run_performance_test.sh \
  ENVIRONMENT=dev \
  TEST_DAY=2026-07-13 \
  POSITIONS_TO_CREATE=10000
```

### Execute a previously prepared DEV dataset

First prepare the dataset:

```bash
export APD_PASSWORD='<dev-apd-password>'
export BIZ_COSMOS_KEY='<dev-biz-cosmos-key>'

./run_performance_test.sh \
  ENVIRONMENT=dev \
  TEST_DAY=2026-07-13 \
  POSITIONS_TO_CREATE=10000 \
  --prepare-only
```

Then execute that same dataset without preparing it again:

```bash
export API_SUBSCRIPTION_KEY='<dev-apim-subscription-key>'
export APD_PASSWORD='<dev-apd-password>'
export RECONCILIATION_COSMOS_KEY='<dev-reconciliation-cosmos-key>'

./run_performance_test.sh \
  ENVIRONMENT=dev \
  TEST_DAY=2026-07-13 \
  POSITIONS_TO_CREATE=10000 \
  --skip-prepare
```

`--skip-prepare` validates the persisted metadata and manifest before sending the trigger. The following values must match the preparation run exactly:

```text
ENVIRONMENT
TEST_DAY
POSITIONS_TO_CREATE
```

Use `--skip-prepare` before the prepared positions have already been reconciled. It does not restore APD positions to their original unpaid state.

### Complete UAT run

Use a dedicated day and agree on the intended volume before running a large dataset.

```bash
export API_SUBSCRIPTION_KEY='<uat-apim-subscription-key>'
export APD_PASSWORD='<uat-apd-password>'
export BIZ_COSMOS_KEY='<uat-biz-cosmos-key>'
export RECONCILIATION_COSMOS_KEY='<uat-reconciliation-cosmos-key>'

./run_performance_test.sh \
  ENVIRONMENT=uat \
  TEST_DAY=2026-07-13 \
  POSITIONS_TO_CREATE=100000
```

### Explicit purge on a dedicated test day

```bash
export APD_PASSWORD='<uat-apd-password>'
export BIZ_COSMOS_KEY='<uat-biz-cosmos-key>'

./run_performance_test.sh \
  ENVIRONMENT=uat \
  TEST_DAY=2026-07-13 \
  POSITIONS_TO_CREATE=1000 \
  --prepare-only \
  --allow-full-day-purge
```

Review the foreign-candidate summary before using this option.

## Run correlation and validation

The trigger response contains:

```text
logicalRunKey
executionId
```

The parent script extracts both values from the k6 output and passes them to the Cosmos DB validator.

The validator accepts only the exact run returned by the trigger:

```text
run.logicalRunKey == EXPECTED_LOGICAL_RUN_KEY
run.executionId == EXPECTED_EXECUTION_ID
run.day == EXPECTED_TEST_DAY
```

All report queries are filtered by the expected `executionId`. This prevents the test from validating a different run started for the same day.

The APD and Cosmos DB validators also compare the current invocation with the persisted preparation metadata.

## Metrics and output

### Trigger duration

```text
RECONCILIATION_TRIGGER durationMs=<value>
```

This is the synchronous time required by the API to accept the request and return `202`.

k6 also emits:

```text
reconciliation_trigger_duration
reconciliation_trigger_accepted
```

### Reconciliation duration

```text
DURATION_MS=<value>
```

This is the interval between the run `startedAt` and `completedAt` timestamps. It measures only the asynchronous reconciliation.

It does not include:

- Docker image builds;
- APD cleanup and seed;
- Biz+ cleanup and seed;
- the APIM trigger duration;
- validator polling;
- final Cosmos DB report validation;
- final APD validation.

Approximate throughput can be calculated as:

```text
scanned / (DURATION_MS / 1000)
```

### Progress counters

The run counters are persisted at completion rather than after every processed chunk. During a long execution it is therefore normal to see:

```text
status=RUNNING scanned=0 recovered=0 manualRequired=0 technicalFailures=0
```

followed by the final counters when the status becomes `DONE`.

This avoids additional Cosmos DB writes during the reconciliation.

### Final validation markers

A successful complete run prints:

```text
RECONCILIATION_COSMOS_VALIDATION_PASSED
APD_RECONCILIATION_VALIDATION_PASSED
PERFORMANCE_WORKFLOW_VALIDATION_PASSED
```

## Environment configuration

Non-secret configuration is stored in:

```text
src/environments/local.environment.json
src/environments/dev.environment.json
src/environments/uat.environment.json
```

The files define:

- API host and paths;
- APD connection metadata;
- Biz+ Cosmos DB endpoint and container;
- generated Biz+ event fields;
- reconciliation Cosmos DB containers;
- polling and timeout values;
- service types and trigger behavior;
- test-data markers;
- data-mutation safety switches.

Do not place credentials or keys in these files.

## Directory layout

```text
performance-test/
├── README.md
├── run_performance_test.sh
├── run_local_apd_safety_test.sh
├── docker-compose.yaml
├── nginx/
├── src/
│   ├── README.md
│   ├── reconciliation_workflow.js
│   ├── environments/
│   ├── test-types/
│   ├── modules/
│   ├── data-preparation/
│   │   ├── apd/
│   │   └── biz/
│   └── data-validation/
│       └── apd/
└── Dockerfile.*
```

## Troubleshooting

### Preparation exits with code 20

Unrelated reconciliation candidates exist on `TEST_DAY`. Choose another day or review the explicit purge option.

### `--skip-prepare` reports a mismatch

The persisted dataset was prepared with a different environment, day, or position count, or the candidate manifest is incomplete. Rerun preparation with the requested parameters.

### The triggered run was replaced

Another reconciliation was started for the same logical run while validation was in progress. The validator stops instead of following the newer execution. Use a dedicated test day and rerun the complete workflow.

### Internal endpoints are unreachable

Verify VPN, internal DNS resolution, network routing, firewall rules, and the selected environment JSON.

### The run validation times out

Check the run document and application logs. The timeout and polling intervals are configured under `validation` in the selected environment JSON.
