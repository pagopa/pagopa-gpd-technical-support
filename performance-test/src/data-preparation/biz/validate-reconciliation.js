import {
  existsSync,
  readFileSync,
  writeFileSync,
} from "node:fs";
import { CosmosClient } from "@azure/cosmos";

const METADATA_PATH =
  "/work/apd-seed-metadata.json";

const RESULT_PATH =
  "/work/reconciliation-validation-result.json";

const verboseLogs =
  String(process.env.VERBOSE_LOGS || "")
    .trim()
    .toLowerCase() === "true";

function fail(message) {
  throw new Error(message);
}

function requiredEnvironmentVariable(name) {
  const value = process.env[name];

  if (
    value === undefined ||
    value === null ||
    String(value).trim() === ""
  ) {
    fail(`${name} is required`);
  }

  return String(value).trim();
}

function readJson(path) {
  if (!existsSync(path)) {
    fail(`Required file not found: ${path}`);
  }

  return JSON.parse(readFileSync(path, "utf8"));
}

function positiveInteger(
  value,
  name,
  defaultValue,
) {
  const parsed = Number(
    value === undefined || value === null
      ? defaultValue
      : value,
  );

  if (
    !Number.isSafeInteger(parsed) ||
    parsed <= 0
  ) {
    fail(`${name} must be a positive integer`);
  }

  return parsed;
}

function sleep(milliseconds) {
  return new Promise((resolve) => {
    setTimeout(resolve, milliseconds);
  });
}

function expectedDistribution(total) {
  const expired = Math.max(
    1,
    Math.floor(total / 100),
  );

  const invalid = Math.max(
    1,
    Math.floor(total / 100),
  );

  return {
    total,
    valid: total - expired - invalid,
    expired,
    invalid,
    manualRequired: expired + invalid,
  };
}

function serviceTypesKey(serviceTypes) {
  return [...serviceTypes]
    .sort()
    .join("|");
}

function logicalRunKey(
  testDay,
  serviceTypes,
) {
  return (
    `${testDay}__` +
    serviceTypesKey(serviceTypes)
  );
}

function numberField(document, field) {
  const value = Number(document[field]);

  if (!Number.isFinite(value)) {
    fail(
      `Run field '${field}' is missing or invalid`,
    );
  }

  return value;
}

function assertEqual(
  actual,
  expected,
  description,
) {
  if (actual !== expected) {
    fail(
      `${description}: expected ${expected}, ` +
        `found ${actual}`,
    );
  }
}

function mapGroupResults(
  resources,
  fieldName,
) {
  const result = new Map();

  for (const item of resources || []) {
    result.set(
      String(item[fieldName]),
      Number(item.count || 0),
    );
  }

  return result;
}

function groupValue(
  values,
  key,
) {
  return values.get(key) || 0;
}

function runDurationMilliseconds(run) {
  const startedAt =
    run.startedAt || run.start || run.createdAt;

  const completedAt =
    run.completedAt || run.stop || run.updatedAt;

  const start = Date.parse(startedAt);
  const stop = Date.parse(completedAt);

  if (
    !Number.isFinite(start) ||
    !Number.isFinite(stop)
  ) {
    return null;
  }

  return Math.max(0, stop - start);
}

async function readRun(
  runsContainer,
  runId,
  testDay,
) {
  try {
    const response = await runsContainer
      .item(runId, testDay)
      .read();

    return response.resource || null;
  } catch (error) {
    const statusCode =
      error.statusCode || error.code;

    if (Number(statusCode) === 404) {
      return null;
    }

    throw error;
  }
}

async function waitForTerminalRun(
  runsContainer,
  runId,
  testDay,
  expectedExecutionId,
  pollIntervalMilliseconds,
  timeoutMilliseconds,
) {
  const started = Date.now();
  let lastStatus = null;
  let lastProgressLog = 0;
  let expectedExecutionSeen = false;

  while (
    Date.now() - started <
    timeoutMilliseconds
  ) {
    const run = await readRun(
      runsContainer,
      runId,
      testDay,
    );

    if (run === null) {
      if (
        Date.now() - lastProgressLog >=
        30000
      ) {
        console.log(
          `Waiting for run document: ${runId}`,
        );

        lastProgressLog = Date.now();
      }

      await sleep(pollIntervalMilliseconds);
      continue;
    }

    const currentExecutionId = String(
      run.executionId || "",
    );

    if (
      currentExecutionId !== expectedExecutionId
    ) {
      if (expectedExecutionSeen) {
        fail(
          `Run ${runId} was replaced while validation was in progress. ` +
            `Expected executionId=${expectedExecutionId}, ` +
            `found executionId=${currentExecutionId || "N/A"}`,
        );
      }

      if (
        verboseLogs ||
        Date.now() - lastProgressLog >= 30000
      ) {
        console.log(
          "Waiting for the expected execution. " +
            `logicalRunKey=${runId} ` +
            `expectedExecutionId=${expectedExecutionId} ` +
            `currentExecutionId=${currentExecutionId || "N/A"}`,
        );

        lastProgressLog = Date.now();
      }

      await sleep(pollIntervalMilliseconds);
      continue;
    }

    expectedExecutionSeen = true;

    const status = String(
      run.status || "",
    );

    const shouldLog =
      verboseLogs ||
      status !== lastStatus ||
      Date.now() - lastProgressLog >= 30000;

    if (shouldLog) {
      console.log(
        "RECONCILIATION_RUN_PROGRESS " +
          `status=${status} ` +
          `scanned=${Number(run.scanned || 0)} ` +
          `recovered=${Number(
            run.recovered || 0,
          )} ` +
          `manualRequired=${Number(
            run.manualRequired || 0,
          )} ` +
          `technicalFailures=${Number(
            run.technicalFailures || 0,
          )}`,
      );

      lastProgressLog = Date.now();
      lastStatus = status;
    }

    if (status === "FAILED") {
      fail(
        "Reconciliation run failed. " +
          `errorCode=${run.errorCode || "N/A"}, ` +
          `errorMessage=${run.errorMessage || "N/A"}`,
      );
    }

    if (status === "DONE") {
      return run;
    }

    await sleep(pollIntervalMilliseconds);
  }

  fail(
    `Reconciliation run did not complete within ` +
      `${Math.round(
        timeoutMilliseconds / 1000,
      )} seconds`,
  );
}

async function reportCount(
  reportsContainer,
  executionId,
) {
  const query = {
    query: `
      SELECT VALUE COUNT(1)
      FROM c
      WHERE c.executionId = @executionId
    `,
    parameters: [
      {
        name: "@executionId",
        value: executionId,
      },
    ],
  };

  const response = await reportsContainer
    .items
    .query(query)
    .fetchAll();

  return Number(
    response.resources?.[0] || 0,
  );
}

async function waitForReports(
  reportsContainer,
  executionId,
  expectedCount,
  pollIntervalMilliseconds,
  timeoutMilliseconds,
) {
  const started = Date.now();
  let lastCount = -1;

  while (
    Date.now() - started <
    timeoutMilliseconds
  ) {
    const count = await reportCount(
      reportsContainer,
      executionId,
    );

    if (
      verboseLogs ||
      count !== lastCount
    ) {
      console.log(
        "RECONCILIATION_REPORT_PROGRESS " +
          `reports=${count}/${expectedCount}`,
      );

      lastCount = count;
    }

    if (count === expectedCount) {
      return count;
    }

    if (count > expectedCount) {
      fail(
        `Found ${count} reports, expected ` +
          `${expectedCount}`,
      );
    }

    await sleep(pollIntervalMilliseconds);
  }

  fail(
    `Reports did not reach ${expectedCount} ` +
      `documents within ` +
      `${Math.round(
        timeoutMilliseconds / 1000,
      )} seconds`,
  );
}

async function statusCounts(
  reportsContainer,
  executionId,
) {
  const query = {
    query: `
      SELECT
        c.reconciliationStatus AS status,
        COUNT(1) AS count
      FROM c
      WHERE c.executionId = @executionId
      GROUP BY c.reconciliationStatus
    `,
    parameters: [
      {
        name: "@executionId",
        value: executionId,
      },
    ],
  };

  const response = await reportsContainer
    .items
    .query(query)
    .fetchAll();

  return mapGroupResults(
    response.resources,
    "status",
  );
}

async function outcomeCounts(
  reportsContainer,
  executionId,
) {
  const query = {
    query: `
      SELECT
        c.outcome AS outcome,
        COUNT(1) AS count
      FROM c
      WHERE c.executionId = @executionId
      GROUP BY c.outcome
    `,
    parameters: [
      {
        name: "@executionId",
        value: executionId,
      },
    ],
  };

  const response = await reportsContainer
    .items
    .query(query)
    .fetchAll();

  return mapGroupResults(
    response.resources,
    "outcome",
  );
}

async function payCounts(
  reportsContainer,
  executionId,
) {
  const query = {
    query: `
      SELECT
        c.payInvoked,
        c.paySucceeded,
        COUNT(1) AS count
      FROM c
      WHERE c.executionId = @executionId
      GROUP BY
        c.payInvoked,
        c.paySucceeded
    `,
    parameters: [
      {
        name: "@executionId",
        value: executionId,
      },
    ],
  };

  const response = await reportsContainer
    .items
    .query(query)
    .fetchAll();

  return response.resources || [];
}

async function reportSample(
  reportsContainer,
  executionId,
) {
  const query = {
    query: `
      SELECT TOP 10
        c.id,
        c.paymentPositionId,
        c.paymentOptionId,
        c.ec,
        c.nav,
        c.ppStatus,
        c.poStatus,
        c.reconciliationStatus,
        c.outcome,
        c.payInvoked,
        c.paySucceeded,
        c.errorCode
      FROM c
      WHERE c.executionId = @executionId
    `,
    parameters: [
      {
        name: "@executionId",
        value: executionId,
      },
    ],
  };

  const response = await reportsContainer
    .items
    .query(query)
    .fetchAll();

  return response.resources || [];
}

async function technicalFailureSample(
  reportsContainer,
  executionId,
) {
  const query = {
    query: `
      SELECT TOP 20
        c.id,
        c.paymentPositionId,
        c.paymentOptionId,
        c.ec,
        c.nav,
        c.ppStatus,
        c.poStatus,
        c.reconciliationStatus,
        c.outcome,
        c.payInvoked,
        c.paySucceeded,
        c.errorCode,
        c.errorMessage
      FROM c
      WHERE c.executionId = @executionId
        AND c.reconciliationStatus =
          "TECHNICAL_FAILURE"
    `,
    parameters: [
      {
        name: "@executionId",
        value: executionId,
      },
    ],
  };

  const response = await reportsContainer
    .items
    .query(query)
    .fetchAll();

  return response.resources || [];
}

function validateRunCounters(
  run,
  expected,
) {
  assertEqual(
    numberField(run, "scanned"),
    expected.total,
    "run.scanned",
  );

  assertEqual(
    numberField(
      run,
      "positiveEventsFound",
    ),
    expected.total,
    "run.positiveEventsFound",
  );

  assertEqual(
    numberField(
      run,
      "reconciliationCases",
    ),
    expected.total,
    "run.reconciliationCases",
  );

  assertEqual(
    numberField(run, "recovered"),
    expected.valid,
    "run.recovered",
  );

  assertEqual(
    numberField(run, "notRecovered"),
    expected.manualRequired,
    "run.notRecovered",
  );

  assertEqual(
    numberField(run, "manualRequired"),
    expected.manualRequired,
    "run.manualRequired",
  );

  assertEqual(
    numberField(
      run,
      "technicalFailures",
    ),
    0,
    "run.technicalFailures",
  );

  assertEqual(
    numberField(run, "payExecuted"),
    expected.valid,
    "run.payExecuted",
  );

  assertEqual(
    numberField(run, "payFailed"),
    0,
    "run.payFailed",
  );
}

function validateReportStatuses(
  counts,
  expected,
) {
  assertEqual(
    groupValue(counts, "RECOVERED"),
    expected.valid,
    "RECOVERED reports",
  );

  assertEqual(
    groupValue(
      counts,
      "MANUAL_REQUIRED",
    ),
    expected.manualRequired,
    "MANUAL_REQUIRED reports",
  );

  assertEqual(
    groupValue(
      counts,
      "TECHNICAL_FAILURE",
    ),
    0,
    "TECHNICAL_FAILURE reports",
  );
}

function validateOutcomes(
  counts,
  expected,
) {
  assertEqual(
    groupValue(
      counts,
      "POSITIVE_EVENT_FOUND_PAY_EXECUTED",
    ),
    expected.valid,
    "PAY_EXECUTED outcomes",
  );

  assertEqual(
    groupValue(
      counts,
      "POSITIVE_EVENT_FOUND_EXPIRED_MANUAL_REQUIRED",
    ),
    expected.expired,
    "EXPIRED manual outcomes",
  );

  assertEqual(
    groupValue(
      counts,
      "POSITIVE_EVENT_FOUND_INVALID_MANUAL_REQUIRED",
    ),
    expected.invalid,
    "INVALID manual outcomes",
  );
}

function validatePayFlags(
  groups,
  expected,
) {
  let successfulPay = 0;
  let manualWithoutPay = 0;
  let other = 0;

  for (const group of groups) {
    const count = Number(group.count || 0);

    if (
      group.payInvoked === true &&
      group.paySucceeded === true
    ) {
      successfulPay += count;
    } else if (
      group.payInvoked === false &&
      group.paySucceeded === false
    ) {
      manualWithoutPay += count;
    } else {
      other += count;
    }
  }

  assertEqual(
    successfulPay,
    expected.valid,
    "reports with successful PAY",
  );

  assertEqual(
    manualWithoutPay,
    expected.manualRequired,
    "manual reports without PAY",
  );

  assertEqual(
    other,
    0,
    "reports with unexpected PAY flags",
  );
}

async function main() {
  const varsPath =
    requiredEnvironmentVariable("VARS");

  const cosmosKey =
    requiredEnvironmentVariable(
      "RECONCILIATION_COSMOS_KEY",
    );

  const expectedEnvironment =
    requiredEnvironmentVariable(
      "EXPECTED_ENVIRONMENT",
    );

  const expectedTestDay =
    requiredEnvironmentVariable(
      "EXPECTED_TEST_DAY",
    );

  const expectedPositions =
    positiveInteger(
      requiredEnvironmentVariable(
        "EXPECTED_POSITIONS",
      ),
      "EXPECTED_POSITIONS",
    );

  const expectedExecutionId =
    requiredEnvironmentVariable(
      "EXPECTED_EXECUTION_ID",
    );

  const expectedLogicalRunKey =
    requiredEnvironmentVariable(
      "EXPECTED_LOGICAL_RUN_KEY",
    );

  const environmentDocument =
    readJson(varsPath);

  const configuration =
    environmentDocument.environment?.[0];

  if (!configuration) {
    fail(
      `Invalid environment configuration: ${varsPath}`,
    );
  }

  const metadata =
    readJson(METADATA_PATH);

  assertEqual(
    String(configuration.env || ""),
    expectedEnvironment,
    "environment configuration",
  );

  assertEqual(
    String(metadata.environment || ""),
    expectedEnvironment,
    "prepared dataset environment",
  );

  assertEqual(
    String(metadata.testDay || ""),
    expectedTestDay,
    "prepared dataset test day",
  );

  const metadataPositions =
    positiveInteger(
      metadata.positions,
      "metadata.positions",
    );

  assertEqual(
    metadataPositions,
    expectedPositions,
    "prepared dataset positions",
  );

  const serviceTypes =
    configuration.reconciliation?.serviceTypes;

  if (
    !Array.isArray(serviceTypes) ||
    serviceTypes.length === 0
  ) {
    fail(
      "reconciliation.serviceTypes must contain at least one value",
    );
  }

  const validationConfiguration =
    configuration.validation || {};

  const pollIntervalSeconds =
    positiveInteger(
      validationConfiguration.pollIntervalSeconds,
      "validation.pollIntervalSeconds",
      5,
    );

  const completionTimeoutSeconds =
    positiveInteger(
      validationConfiguration.completionTimeoutSeconds,
      "validation.completionTimeoutSeconds",
      7200,
    );

  const reportConsistencyTimeoutSeconds =
    positiveInteger(
      validationConfiguration.reportConsistencyTimeoutSeconds,
      "validation.reportConsistencyTimeoutSeconds",
      120,
    );

  const positions = expectedPositions;

  const expected =
    expectedDistribution(positions);

  const calculatedLogicalRunKey =
    logicalRunKey(
      expectedTestDay,
      serviceTypes,
    );

  assertEqual(
    calculatedLogicalRunKey,
    expectedLogicalRunKey,
    "triggered logicalRunKey",
  );

  const runId = expectedLogicalRunKey;

  console.log(
    "============================================================",
  );
  console.log(
    "Reconciliation Cosmos validation",
  );
  console.log(
    `ENVIRONMENT=${configuration.env}`,
  );
  console.log(
    `TEST_DAY=${expectedTestDay}`,
  );
  console.log(
    `LOGICAL_RUN_KEY=${runId}`,
  );
  console.log(
    `EXPECTED_EXECUTION_ID=${expectedExecutionId}`,
  );
  console.log(
    `POSITIONS=${positions}`,
  );
  console.log(
    `EXPECTED_RECOVERED=${expected.valid}`,
  );
  console.log(
    `EXPECTED_MANUAL_REQUIRED=${expected.manualRequired}`,
  );
  console.log(
    `LOG_MODE=${
      verboseLogs ? "VERBOSE" : "SUMMARY"
    }`,
  );
  console.log(
    "============================================================",
  );

  const client = new CosmosClient({
    endpoint:
      configuration.reconciliationCosmos.endpoint,
    key: cosmosKey,
    userAgentSuffix:
      "gpd-technical-support-reconciliation-validator",
  });

  const database = client.database(
    configuration.reconciliationCosmos.database,
  );

  const runsContainer = database.container(
    configuration.reconciliationCosmos
      .runsContainer,
  );

  const reportsContainer = database.container(
    configuration.reconciliationCosmos
      .reportsContainer,
  );

  const run = await waitForTerminalRun(
    runsContainer,
    runId,
    expectedTestDay,
    expectedExecutionId,
    pollIntervalSeconds * 1000,
    completionTimeoutSeconds * 1000,
  );

  if (
    run.logicalRunKey !== runId ||
    run.day !== expectedTestDay ||
    run.executionId !== expectedExecutionId
  ) {
    fail(
      "The completed run does not match the exact execution triggered by k6",
    );
  }

  await waitForReports(
    reportsContainer,
    expectedExecutionId,
    expected.total,
    pollIntervalSeconds * 1000,
    reportConsistencyTimeoutSeconds * 1000,
  );

  const statuses = await statusCounts(
    reportsContainer,
    expectedExecutionId,
  );

  const outcomes = await outcomeCounts(
    reportsContainer,
    expectedExecutionId,
  );

  const payFlagGroups = await payCounts(
    reportsContainer,
    expectedExecutionId,
  );
  
  const technicalFailures =
    await technicalFailureSample(
      reportsContainer,
      expectedExecutionId,
    );

  if (technicalFailures.length > 0) {
    console.error(
      "TECHNICAL_FAILURE_DETAILS " +
        `count=${technicalFailures.length}`,
    );

    console.table(technicalFailures);
  }

  validateRunCounters(
    run,
    expected,
  );

  validateReportStatuses(
    statuses,
    expected,
  );

  validateOutcomes(
    outcomes,
    expected,
  );

  validatePayFlags(
    payFlagGroups,
    expected,
  );

  const durationMilliseconds =
    runDurationMilliseconds(run);

  if (verboseLogs) {
    console.log("Completed run:");
    console.dir(run, {
      depth: null,
    });

    const sample = await reportSample(
      reportsContainer,
      expectedExecutionId,
    );

    console.log("First reconciliation reports:");
    console.table(sample);
  } else {
    console.log(
      "Run and report details omitted because LOG_MODE=SUMMARY.",
    );
  }

  const result = {
    testDay: expectedTestDay,
    logicalRunKey: run.logicalRunKey,
    executionId: expectedExecutionId,
    status: run.status,
    positions,
    recovered: expected.valid,
    manualRequired:
      expected.manualRequired,
    technicalFailures: 0,
    durationMilliseconds,
    validatedAt:
      new Date().toISOString(),
  };

  writeFileSync(
    RESULT_PATH,
    JSON.stringify(result, null, 2),
    "utf8",
  );

  console.log(
    "RECONCILIATION_COSMOS_VALIDATION_PASSED",
  );
  console.log(
    `  EXECUTION_ID=${expectedExecutionId}`,
  );
  console.log(
    `  STATUS=${run.status}`,
  );
  console.log(
    `  RECOVERED=${expected.valid}`,
  );
  console.log(
    `  MANUAL_REQUIRED=${expected.manualRequired}`,
  );
  console.log(
    "  TECHNICAL_FAILURES=0",
  );

  if (durationMilliseconds !== null) {
    console.log(
      `  DURATION_MS=${durationMilliseconds}`,
    );
  }
}

main().catch((error) => {
  console.error(`ERROR: ${error.message}`);
  process.exit(1);
});