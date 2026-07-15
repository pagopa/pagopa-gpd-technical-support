# GPD Technical Support performance tests

This folder contains the k6 framework and the external data-preparation tools used by the debt-position status reconciliation performance tests.

The layout follows the existing PagoPA load-test convention:

- `src/environments`: non-secret environment configuration;
- `src/test-types`: k6 execution options loaded through `open(__ENV.TEST_TYPE)`;
- `run_performance_test.sh`: single entry point;
- `docker-compose.yaml`: k6, InfluxDB proxy and data-preparation services;
- `src/data-preparation`: PostgreSQL and Cosmos preparation/validation tools.

## Secrets

Only these values must stay outside the environment JSON files:

```text
API_SUBSCRIPTION_KEY
APD_PASSWORD
BIZ_COSMOS_KEY
RECONCILIATION_COSMOS_KEY
```

For Step 1 only `APD_PASSWORD` is required. The other secrets are already reserved in the runner and Compose configuration for the following steps.

## Step 1: prepare a safe APD TEST_DAY

The default behavior is deliberately conservative:

1. load APD connection and reconciliation settings from `src/environments/<env>.environment.json`;
2. delete only previous performance data identified by `payment_position.iupd LIKE 'GPDTS_PERF_%'` on `TEST_DAY`;
3. count candidates using the same predicate as `JdbcPaymentOptionCandidateReader`;
4. stop with exit code `20` when foreign candidates are present;
5. execute a complete candidate-position purge only when `--allow-full-day-purge` is explicitly supplied.

The purge removes the complete graph of each foreign payment position contributing at least one reconciliation candidate:

```text
transfer_metadata
payment_option_metadata
transfer
payment_option
payment_position
```

APD sequences are never reset or altered.

### Safe preparation

```bash
export APD_PASSWORD='<apd-password>'

./run_performance_test.sh \
  uat \
  pilot \
  reconciliation_workflow \
  k6 \
  2026-07-01 \
  --prepare-only
```

When foreign candidates exist, the process prints their summary and exits with code `20` without deleting them.

### Explicit full-day purge

```bash
export APD_PASSWORD='<apd-password>'

./run_performance_test.sh \
  uat \
  pilot \
  reconciliation_workflow \
  k6 \
  2026-07-01 \
  --prepare-only \
  --allow-full-day-purge
```

The flag is disabled by default. Environment configuration is also authoritative: production sets both `dataMutationEnabled` and `fullDayPurgeEnabled` to `false`.

## Local automated verification

The following command creates a temporary PostgreSQL 15 instance and verifies both Step 1 branches:

- safe mode deletes only `GPDTS_PERF_` data and stops on a foreign candidate;
- explicit purge deletes the foreign candidate graph but preserves a foreign non-candidate position.

```bash
./run_local_step1_test.sh
```

The expected final output is:

```text
LOCAL_STEP1_TEST_PASSED
```

The local database and volume are removed automatically after the test.

## Full k6 invocation

The k6 phase is already wired but the end-to-end dataset seed and asynchronous final validation will be added in the following steps.

```bash
export API_SUBSCRIPTION_KEY='<apim-subkey>'
export APD_PASSWORD='<apd-password>'
export BIZ_COSMOS_KEY='<biz-cosmos-key>'
export RECONCILIATION_COSMOS_KEY='<reconciliation-cosmos-key>'

./run_performance_test.sh \
  uat \
  pilot \
  reconciliation_workflow \
  k6 \
  2026-07-01
```

## Environment configuration

Environment files are available for:

```text
local
dev
uat
prod
```

The APD hostname and username are stored in these files because they are not secrets. Before the first execution against an Azure environment, verify that the configured internal DNS alias is reachable from the machine or runner executing Docker.
