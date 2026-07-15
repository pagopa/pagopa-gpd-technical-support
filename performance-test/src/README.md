# k6 reconciliation workflow

This directory contains the k6 script, non-secret environment configuration, test profile, data-preparation tools, and result validators used by the GPD Technical Support reconciliation performance workflow.

Supported environment files:

```text
/scripts/environments/local.environment.json
/scripts/environments/dev.environment.json
/scripts/environments/uat.environment.json
```

The k6 profile is fixed to:

```text
/scripts/test-types/pilot.json
```

## Execution model

`pilot.json` uses:

```json
{
  "vus": 1,
  "iterations": 1
}
```

Therefore `reconciliation_workflow.js` sends exactly one asynchronous reconciliation request.

The workload size is not controlled by k6 concurrency. It is controlled by the APD dataset prepared through:

```text
POSITIONS_TO_CREATE
```

The complete orchestration is performed by:

```text
../run_performance_test.sh
```

k6 validates the synchronous acceptance response only. The parent script then captures the returned run identifiers and starts the asynchronous validators.

## Configuration loading

The k6 script loads its options and environment configuration with:

```javascript
export let options = JSON.parse(open(__ENV.TEST_TYPE));

const varsArray = new SharedArray("vars", function () {
  return JSON.parse(open(__ENV.VARS)).environment;
});

const vars = varsArray[0];
```

Docker Compose sets:

```text
TEST_TYPE=/scripts/test-types/pilot.json
VARS=/scripts/environments/<local|dev|uat>.environment.json
```

`TEST_TYPE` is fixed by the framework and is not accepted as a command-line argument.

## Environment configuration schema

Each `*.environment.json` file contains one item in the `environment` array.

### API fields

**`env`**

Environment identifier used by metadata and validation.

**`host`**

API host.

**`basePath`**

Environment API base path.

**`reconciliationPath`**

Reconciliation endpoint path.

**`subscriptionHeader`**

Header used for the API subscription key.

The final trigger URL is assembled as:

```text
host + basePath + reconciliationPath
```

### APD fields

**`apd.jdbcUrl`**

APD JDBC URL used by preparation and final validation.

**`apd.username`**

APD database user.

**`apd.schema`**

APD schema.

The password is provided separately through:

```text
APD_PASSWORD
```

### Biz+ fields

**`bizCosmos.endpoint`**

Biz+ Cosmos DB account endpoint.

**`bizCosmos.database`**

Biz+ database.

**`bizCosmos.container`**

Biz+ event container.

**`bizEvent.*`**

Static fields used to build synthetic `DONE` events.

**`bizEvent.bulkChunkSize`**

Number of Cosmos DB operations submitted by each preparation chunk.

The account key is provided separately through:

```text
BIZ_COSMOS_KEY
```

### Reconciliation Cosmos DB fields

**`reconciliationCosmos.endpoint`**

Reconciliation Cosmos DB endpoint.

**`reconciliationCosmos.database`**

Database containing runs and reports.

**`reconciliationCosmos.runsContainer`**

Container storing reconciliation runs.

**`reconciliationCosmos.reportsContainer`**

Container storing reconciliation reports.

The account key is provided separately through:

```text
RECONCILIATION_COSMOS_KEY
```

### Validation fields

**`validation.pollIntervalSeconds`**

Delay between run polling attempts.

**`validation.completionTimeoutSeconds`**

Maximum wait for run completion.

**`validation.reportConsistencyTimeoutSeconds`**

Maximum wait for all expected reports after the run has completed.

### Reconciliation fields

**`reconciliation.serviceTypes`**

Service types included in the request and in APD candidate selection.

**`reconciliation.minProcessingDelayDays`**

Minimum candidate age used by APD selection.

**`reconciliation.force`**

Value sent in the request body.

**`reconciliation.triggerTimeout`**

Timeout applied to the synchronous trigger request.

### Test-data and safety fields

**`testData.organizationFiscalCode`**

Organization fiscal code assigned to synthetic positions and events.

**`testData.performanceDataPrefix`**

Prefix used to identify data owned by the performance framework.

**`safety.dataMutationEnabled`**

Enables APD mutation for the selected environment.

**`safety.fullDayPurgeEnabled`**

Allows explicit deletion of unrelated candidate graphs when `--allow-full-day-purge` is supplied.

Secrets must never be added to these JSON files.

## k6 runtime variables

Docker Compose passes these values to `reconciliation_workflow.js`:

**`API_SUBSCRIPTION_KEY`**

- Source: secret.
- Purpose: value of the API authentication header.

**`TEST_DAY`**

- Source: execution parameter.
- Purpose: used as both request `from` and `to`.

**`POSITIONS_TO_CREATE`**

- Source: execution parameter.
- Purpose: included in summary logs; the actual workload is already present in APD.

**`VERBOSE_LOGS`**

- Source: derived by the parent script.
- Purpose: enables request and response details for datasets of at most 10 positions.

**`VARS`**

- Source: framework configuration.
- Purpose: path of the selected environment JSON file.

**`TEST_TYPE`**

- Source: framework configuration.
- Purpose: fixed path of `pilot.json`.

**`K6_OUT`**

- Source: framework configuration.
- Purpose: InfluxDB output URL through the environment-specific Nginx proxy.

The other secrets are passed to the Compose stack because they are consumed by preparation and validation services, not by the k6 HTTP request.

## Request generated by k6

For one `TEST_DAY`, the script sends:

```json
{
  "from": "2026-07-13",
  "to": "2026-07-13",
  "serviceTypes": [
    "GPD"
  ],
  "force": true
}
```

`serviceTypes`, `force`, and the trigger timeout are loaded from the selected environment file.

Headers:

```text
Content-Type: application/json
X-Request-Id: gpdts-perf-<day>-<timestamp>-<vu>-<iteration>
Ocp-Apim-Subscription-Key: <API_SUBSCRIPTION_KEY>
```

The subscription-header name is configurable through `subscriptionHeader`.

## Acceptance checks

k6 verifies:

- HTTP status is `202`;
- response body contains `accepted=true`;
- the response contains exactly one run;
- the returned run day matches `TEST_DAY`;
- `logicalRunKey` is present;
- `executionId` is present;
- initial run status is `CREATED` or `RUNNING`.

A failed check causes the k6 command to fail through the configured thresholds or subsequent identifier extraction.

## Run identifiers

After a successful trigger, k6 prints:

```text
RECONCILIATION_EXECUTION_ID=<executionId>
RECONCILIATION_LOGICAL_RUN_KEY=<logicalRunKey>
```

The parent script extracts these values from the container output and exports:

```text
EXPECTED_EXECUTION_ID
EXPECTED_LOGICAL_RUN_KEY
```

to the reconciliation validator.

The validator must match all of these values:

```text
run.executionId
run.logicalRunKey
run.day
```

Report queries are filtered by the exact `executionId`, so a later run for the same day cannot be validated accidentally.

## Custom k6 metrics

The script emits:

```text
reconciliation_trigger_duration
reconciliation_trigger_accepted
```

It also prints:

```text
RECONCILIATION_TRIGGER status=<status> durationMs=<milliseconds> positions=<count> executionId=<id>
```

These values describe only the synchronous trigger request.

The complete asynchronous duration is calculated later by the Cosmos DB validator from:

```text
startedAt
completedAt
```

and printed as:

```text
DURATION_MS=<milliseconds>
```

## Data preparation

### APD

```text
data-preparation/apd/prepare-test-day.sh
```

The APD preparation service:

1. validates environment and connection configuration;
2. removes previous data identified by `testData.performanceDataPrefix`;
3. detects unrelated candidates;
4. optionally purges their complete APD graphs;
5. seeds the requested dataset;
6. validates candidate counts and status distribution;
7. writes:
   - `/work/apd-candidates.jsonl`;
   - `/work/apd-seed-metadata.json`.

The metadata includes:

```text
environment
testDay
runId
organizationFiscalCode
positions
```

### Biz+

```text
data-preparation/biz/prepare-biz-events.js
```

The Biz+ preparation service reads the APD manifest and metadata, deletes previous framework-owned events, creates one matching `DONE` event per candidate, and validates the current run.

Fee values cycle through:

```text
0
1
0.45
1.0
```

## Asynchronous validation

### Cosmos DB run and reports

```text
data-preparation/biz/validate-reconciliation.js
```

The validator:

1. validates the persisted preparation metadata against the current invocation;
2. verifies the expected logical run key;
3. polls the run container;
4. stops if the run was replaced by another `executionId`;
5. waits for `DONE` or fails on a terminal error status;
6. validates run counters;
7. waits for all expected reports;
8. validates report outcomes and PAY flags;
9. prints `RECONCILIATION_COSMOS_VALIDATION_PASSED`.

During `RUNNING`, counters can remain at zero because they are persisted only at the end of the reconciliation.

### APD final state

```text
data-validation/apd/validate-reconciliation-result.sh
```

The APD validator:

1. validates current invocation parameters against the persisted metadata;
2. verifies the environment configuration;
3. checks final position and option states;
4. rejects unexpected states;
5. prints `APD_RECONCILIATION_VALIDATION_PASSED`.

## `--skip-prepare` protection

The parent script validates these files before sending the trigger:

```text
/work/apd-seed-metadata.json
/work/apd-candidates.jsonl
```

It verifies:

```text
metadata.environment == ENVIRONMENT
metadata.testDay == TEST_DAY
metadata.positions == POSITIONS_TO_CREATE
manifest line count == POSITIONS_TO_CREATE
```

The same metadata checks are repeated by both final validators.

The files are stored in the Docker volume:

```text
performance-data
```

`--skip-prepare` is intended to execute a dataset created by an earlier `--prepare-only` command. It must not be used after that dataset has already been reconciled.

## Logging modes

The parent script derives `VERBOSE_LOGS` automatically:

Logging behavior depends on the requested dataset size:

- **From 3 to 10 positions — `VERBOSE`**

  Prints individual APD candidates, Biz+ events, the completed run, and the first reconciliation reports.

- **More than 10 positions — `SUMMARY`**

  Prints aggregate preparation progress and final validation results only.

## Source layout

```text
src/
├── README.md
├── reconciliation_workflow.js
├── environments/
│   ├── local.environment.json
│   ├── dev.environment.json
│   └── uat.environment.json
├── test-types/
│   └── pilot.json
├── modules/
│   └── helpers.js
├── data-preparation/
│   ├── apd/
│   │   ├── prepare-test-day.sh
│   │   ├── local/
│   │   └── sql/
│   └── biz/
│       ├── prepare-biz-events.js
│       └── validate-reconciliation.js
└── data-validation/
    └── apd/
        ├── validate-reconciliation-result.sh
        └── sql/
```

## Direct k6 execution

The supported entry point is `../run_performance_test.sh`, because it prepares the dataset, captures the exact run identifiers, and invokes the final validators.

For isolated debugging, the equivalent k6 container configuration must provide at least:

```text
API_SUBSCRIPTION_KEY
TEST_DAY
POSITIONS_TO_CREATE
VERBOSE_LOGS
VARS
TEST_TYPE
K6_OUT
```

A direct k6 execution validates only trigger acceptance and does not validate reconciliation completion or final data states.
