import {
  createReadStream,
  createWriteStream,
  existsSync,
  readFileSync,
  rmSync,
} from "node:fs";
import { once } from "node:events";
import { createInterface } from "node:readline";
import { BulkOperationType, CosmosClient } from "@azure/cosmos";

const MANIFEST_PATH = "/work/apd-candidates.jsonl";
const METADATA_PATH = "/work/apd-seed-metadata.json";
const EXISTING_IDS_PATH = "/work/biz-existing-ids.jsonl";
const DEFAULT_BULK_CHUNK_SIZE = 1000;
const VERBOSE_LOGS =
  process.env.VERBOSE_LOGS?.trim().toLowerCase() === "true";

function fail(message) {
  throw new Error(message);
}

function requiredEnvironmentVariable(name) {
  const value = process.env[name]?.trim();

  if (!value) {
    fail(`${name} is required`);
  }

  return value;
}

function readJson(path) {
  if (!existsSync(path)) {
    fail(`Required file not found: ${path}`);
  }

  return JSON.parse(readFileSync(path, "utf8"));
}

function getEnvironmentConfiguration() {
  const varsPath = requiredEnvironmentVariable("VARS");
  const document = readJson(varsPath);
  const configuration = document.environment?.[0];

  if (!configuration) {
    fail(`Invalid environment configuration: ${varsPath}`);
  }

  return configuration;
}

function positiveInteger(value, name, defaultValue) {
  const parsed = Number(value ?? defaultValue);

  if (!Number.isSafeInteger(parsed) || parsed <= 0) {
    fail(`${name} must be a positive integer`);
  }

  return parsed;
}

function createProgressReporter(total, operationName) {
  let nextPercentage = 10;

  return function report(current) {
    if (VERBOSE_LOGS) {
      console.log(`${operationName}: ${current}/${total}`);
      return;
    }

    const percentage =
      total === 0
        ? 100
        : Math.floor((current * 100) / total);

    if (percentage >= nextPercentage || current === total) {
      console.log(
        `${operationName}: ${current}/${total} (${percentage}%)`,
      );

      while (nextPercentage <= percentage) {
        nextPercentage += 10;
      }
    }
  };
}

function compactDate(date) {
  return date.replaceAll("-", "");
}

function buildDayPrefix(testDay) {
  return `gpdts-perf-${compactDate(testDay)}-`;
}

function buildRunPrefix(testDay, runId) {
  return `${buildDayPrefix(testDay)}${runId}-`;
}

function formatDecimalAmount(amountInCents) {
  const amount = BigInt(amountInCents);
  const units = amount / 100n;
  const cents = amount % 100n;

  return `${units}.${cents.toString().padStart(2, "0")}`;
}

function feeForOrdinal(ordinal) {
  const fees = ["0", "1", "0.45", "1.0"];
  return fees[(ordinal - 1) % fees.length];
}

function paymentDateTime(testDay, ordinal) {
  const milliseconds = String(ordinal % 1000).padStart(3, "0");
  return `${testDay}T12:00:00.${milliseconds}000`;
}

function buildBizEvent(candidate, metadata, configuration) {
  const ordinal = Number(candidate.ordinal);
  const bizConfiguration = configuration.bizEvent;

  const runPrefix = buildRunPrefix(metadata.testDay, metadata.runId);
  const itemId =
    `${runPrefix}${String(ordinal).padStart(8, "0")}`;

  const receiptId =
    `gpdtsperf${metadata.runId}${String(ordinal).padStart(8, "0")}`;

  const amount = formatDecimalAmount(candidate.amountInCents);
  const dateTime = paymentDateTime(metadata.testDay, ordinal);

  return {
    id: itemId,
    version: "2",
    complete: "false",
    receiptId,
    missingInfo: [
      "idPaymentManager",
      "paymentInfo.primaryCiIncurredFee",
      "paymentInfo.idBundle",
      "paymentInfo.idCiBundle",
    ],
    debtorPosition: {
      modelType: "2",
      noticeNumber: candidate.nav,
      iuv: candidate.iuv,
      iur: receiptId,
    },
    creditor: {
      idPA: candidate.organizationFiscalCode,
      idBrokerPA: candidate.organizationFiscalCode,
      idStation: `${candidate.organizationFiscalCode}_01`,
      companyName: "GPDTS Performance Test",
      officeName: "Performance Test",
    },
    psp: {
      idPsp: bizConfiguration.pspCode,
      idBrokerPsp: bizConfiguration.pspBrokerCode,
      idChannel: bizConfiguration.pspChannelCode,
      psp: bizConfiguration.pspCompany,
      pspPartitaIVA: bizConfiguration.pspTaxCode,
      pspFiscalCode: bizConfiguration.pspTaxCode,
      channelDescription: bizConfiguration.paymentChannel,
    },
    debtor: {
      fullName: "GPDTS Performance Debtor",
      entityUniqueIdentifierType: "F",
      entityUniqueIdentifierValue: "TSTFSC00A00H501X",
      streetName: "Via Test",
      civicNumber: "1",
      postalCode: "00100",
      city: "Roma",
      stateProvinceRegion: "RM",
      country: "IT",
      eMail: "gpdts-performance@test.invalid",
    },
    payer: {
      fullName: "GPDTS Performance Payer",
      entityUniqueIdentifierType: "F",
      entityUniqueIdentifierValue: "TSTPYR00A00H501X",
      streetName: "Via Test",
      civicNumber: "1",
      postalCode: "00100",
      city: "Roma",
      stateProvinceRegion: "RM",
      country: "IT",
      eMail: "gpdts-performance@test.invalid",
    },
    paymentInfo: {
      paymentDateTime: dateTime,
      applicationDate: metadata.testDay,
      transferDate: metadata.testDay,
      dueDate: metadata.testDay,
      paymentToken: receiptId,
      amount,
      fee: feeForOrdinal(ordinal),
      totalNotice: "1",
      paymentMethod: bizConfiguration.paymentMethod,
      touchpoint: bizConfiguration.touchpoint,
      paymentChannel: bizConfiguration.paymentChannel,
      remittanceInformation:
        `GPDTS performance test ${metadata.runId}`,
      description:
        `GPDTS performance test ${candidate.paymentPositionStatus}`,
      metadata: [
        {
          key: "gpdtsRunId",
          value: metadata.runId,
        },
        {
          key: "gpdtsOrdinal",
          value: String(ordinal),
        },
      ],
      IUR: receiptId,
    },
    transferList: [
      {
        idTransfer: "1",
        fiscalCodePA: candidate.organizationFiscalCode,
        companyName: "GPDTS Performance Test",
        amount,
        transferCategory: "9/0101108TS/",
        remittanceInformation:
          `GPDTS performance test ${metadata.runId}`,
        IBAN: "IT45R0760103200000000001016",
      },
    ],
    timestamp:
      Date.parse(`${metadata.testDay}T12:00:00.000Z`) + ordinal,
    properties: {
      serviceIdentifier: bizConfiguration.serviceIdentifier,
    },
    eventStatus: "DONE",
    eventRetryEnrichmentCount: 0,
    eventTriggeredBySchedule: false,
  };
}

function validateBulkResults(results) {
  let requestCharge = 0;
  const failures = [];

  for (const result of results) {
    const statusCode = result.response?.statusCode;

    requestCharge += Number(
      result.response?.requestCharge ?? 0,
    );

    if (
      result.error ||
      !statusCode ||
      statusCode < 200 ||
      statusCode >= 300
    ) {
      failures.push({
        id:
          result.operationInput?.id ??
          result.operationInput?.resourceBody?.id,
        statusCode,
        error:
          result.error?.message ??
          result.error?.code ??
          "Unknown bulk operation error",
      });
    }
  }

  if (failures.length > 0) {
    fail(
      `Cosmos bulk operation failed for ${failures.length} items. ` +
        `First failures: ${JSON.stringify(failures.slice(0, 5))}`,
    );
  }

  return requestCharge;
}

async function executeBulk(container, operations) {
  if (operations.length === 0) {
    return 0;
  }

  const results =
    await container.items.executeBulkOperations(
      operations,
      {
        contentResponseOnWriteEnabled: false,
      },
    );

  return validateBulkResults(results);
}

async function exportExistingIds(
  container,
  idPrefix,
  pageSize,
) {
  rmSync(EXISTING_IDS_PATH, { force: true });

  const output = createWriteStream(
    EXISTING_IDS_PATH,
    { encoding: "utf8" },
  );

  let count = 0;

  const querySpec = {
    query:
      "SELECT c.id FROM c " +
      "WHERE STARTSWITH(c.id, @idPrefix)",
    parameters: [
      {
        name: "@idPrefix",
        value: idPrefix,
      },
    ],
  };

  const iterator = container.items.query(
    querySpec,
    {
      maxItemCount: pageSize,
      maxDegreeOfParallelism: -1,
    },
  );

  while (iterator.hasMoreResults()) {
    const response = await iterator.fetchNext();

    for (const item of response.resources ?? []) {
      if (!output.write(`${JSON.stringify(item)}\n`)) {
        await once(output, "drain");
      }

      count += 1;
    }
  }

  output.end();
  await once(output, "finish");

  return count;
}

async function deleteExistingEvents(
  container,
  idPrefix,
  chunkSize,
) {
  const existingCount = await exportExistingIds(
    container,
    idPrefix,
    chunkSize,
  );

  if (existingCount === 0) {
    console.log(
      "No previous GPDTS Biz+ events found for TEST_DAY.",
    );

    return {
      deleted: 0,
      requestCharge: 0,
    };
  }

  console.log(
    `Deleting ${existingCount} previous GPDTS Biz+ events...`,
  );

  const lines = createInterface({
    input: createReadStream(
      EXISTING_IDS_PATH,
      { encoding: "utf8" },
    ),
    crlfDelay: Infinity,
  });

  let operations = [];
  let deleted = 0;
  let requestCharge = 0;
  const reportProgress = createProgressReporter(
    existingCount,
    "Deleted Biz+ events",
  );

  for await (const line of lines) {
    if (!line.trim()) {
      continue;
    }

    const { id } = JSON.parse(line);

    operations.push({
      operationType: BulkOperationType.Delete,
      id,
      partitionKey: id,
    });

    if (operations.length >= chunkSize) {
      requestCharge += await executeBulk(
        container,
        operations,
      );

      deleted += operations.length;
	  reportProgress(deleted);
      operations = [];
    }
  }

  if (operations.length > 0) {
    requestCharge += await executeBulk(
      container,
      operations,
    );

    deleted += operations.length;
	reportProgress(deleted);
  }

  rmSync(EXISTING_IDS_PATH, { force: true });

  return {
    deleted,
    requestCharge,
  };
}

async function upsertManifestEvents(
  container,
  metadata,
  configuration,
  chunkSize,
) {
  const lines = createInterface({
    input: createReadStream(
      MANIFEST_PATH,
      { encoding: "utf8" },
    ),
    crlfDelay: Infinity,
  });

  let operations = [];
  let inserted = 0;
  let requestCharge = 0;
  
  const reportProgress = createProgressReporter(
    Number(metadata.positions),
    "Upserted Biz+ events",
  );

  for await (const line of lines) {
    if (!line.trim()) {
      continue;
    }

    const candidate = JSON.parse(line);

    const event = buildBizEvent(
      candidate,
      metadata,
      configuration,
    );

    operations.push({
      operationType: BulkOperationType.Upsert,
      partitionKey: event.id,
      resourceBody: event,
    });

    if (operations.length >= chunkSize) {
      requestCharge += await executeBulk(
        container,
        operations,
      );

      inserted += operations.length;
	  reportProgress(inserted);
      operations = [];
    }
  }

  if (operations.length > 0) {
    requestCharge += await executeBulk(
      container,
      operations,
    );

    inserted += operations.length;
	reportProgress(inserted);
  }

  return {
    inserted,
    requestCharge,
  };
}

async function validateCurrentRun(
  container,
  metadata,
) {
  const runPrefix = buildRunPrefix(
    metadata.testDay,
    metadata.runId,
  );

  const countQuery = {
    query: `
      SELECT VALUE COUNT(1)
      FROM c
      WHERE STARTSWITH(c.id, @runPrefix)
        AND c.eventStatus = "DONE"
        AND c.creditor.idPA = @organizationFiscalCode
    `,
    parameters: [
      {
        name: "@runPrefix",
        value: runPrefix,
      },
      {
        name: "@organizationFiscalCode",
        value: metadata.organizationFiscalCode,
      },
    ],
  };

  const { resources: counts } =
    await container.items
      .query(countQuery)
      .fetchAll();

  const actualCount = Number(counts[0] ?? 0);

  if (actualCount !== Number(metadata.positions)) {
    fail(
      `Biz+ validation failed: expected ` +
        `${metadata.positions} events, found ${actualCount}`,
    );
  }

  if (VERBOSE_LOGS) {
    const sampleQuery = {
      query: `
        SELECT TOP 10
          c.id,
          c.creditor.idPA AS ec,
          c.debtorPosition.noticeNumber AS nav,
          c.debtorPosition.iuv AS iuv,
          c.paymentInfo.fee AS fee,
          c.eventStatus
        FROM c
        WHERE STARTSWITH(c.id, @runPrefix)
        ORDER BY c.id
      `,
      parameters: [
        {
          name: "@runPrefix",
          value: runPrefix,
        },
      ],
    };

    const { resources: sample } =
      await container.items
        .query(sampleQuery)
        .fetchAll();

    console.table(sample);
  } else {
    console.log(
      "Biz+ event details omitted because LOG_MODE=SUMMARY.",
    );
  }

  console.log(
    `BIZ_SEED_VALIDATION_PASSED: ` +
      `${actualCount} DONE events found.`,
  );
}

async function main() {
  const configuration =
    getEnvironmentConfiguration();

  const metadata = readJson(METADATA_PATH);

  const cosmosKey =
    requiredEnvironmentVariable("BIZ_COSMOS_KEY");

  if (
    configuration.safety?.dataMutationEnabled !== true
  ) {
    fail(
      `Data mutation is disabled for environment ` +
        `${configuration.env}`,
    );
  }

  if (!configuration.bizEvent) {
    fail(
      "Missing bizEvent configuration in environment JSON",
    );
  }

  const chunkSize = positiveInteger(
    configuration.bizEvent.bulkChunkSize,
    "bizEvent.bulkChunkSize",
    DEFAULT_BULK_CHUNK_SIZE,
  );

  console.log(
    "============================================================",
  );
  console.log("Biz+ performance event preparation");
  console.log(`ENVIRONMENT=${configuration.env}`);
  console.log(`TEST_DAY=${metadata.testDay}`);
  console.log(`RUN_ID=${metadata.runId}`);
  console.log(`POSITIONS=${metadata.positions}`);
  console.log(
    `LOG_MODE=${VERBOSE_LOGS ? "VERBOSE" : "SUMMARY"}`,
  );
  console.log(
    `COSMOS_DATABASE=${configuration.bizCosmos.database}`,
  );
  console.log(
    `COSMOS_CONTAINER=${configuration.bizCosmos.container}`,
  );
  console.log(`BULK_CHUNK_SIZE=${chunkSize}`);
  console.log(
    "============================================================",
  );

  const client = new CosmosClient({
    endpoint: configuration.bizCosmos.endpoint,
    key: cosmosKey,
    userAgentSuffix:
      "gpd-technical-support-performance-test",
  });

  const container = client
    .database(configuration.bizCosmos.database)
    .container(configuration.bizCosmos.container);

  const { resource: containerDefinition } =
    await container.read();

  const partitionKeyPaths =
    containerDefinition?.partitionKey?.paths ?? [];

  if (!partitionKeyPaths.includes("/id")) {
    fail(
      `Expected Biz+ partition key /id, found: ` +
        `${partitionKeyPaths.join(", ")}`,
    );
  }

  const cleanup = await deleteExistingEvents(
    container,
    buildDayPrefix(metadata.testDay),
    chunkSize,
  );

  const seed = await upsertManifestEvents(
    container,
    metadata,
    configuration,
    chunkSize,
  );

  if (seed.inserted !== Number(metadata.positions)) {
    fail(
      `Biz+ seed failed: expected ${metadata.positions} ` +
        `upserts, executed ${seed.inserted}`,
    );
  }

  await validateCurrentRun(container, metadata);

  console.log("BIZ_TEST_DATA_READY:");
  console.log(`  TEST_DAY=${metadata.testDay}`);
  console.log(`  RUN_ID=${metadata.runId}`);
  console.log(`  EVENTS=${seed.inserted}`);
  console.log(
    `  PREVIOUS_EVENTS_DELETED=${cleanup.deleted}`,
  );
  console.log(
    `  DELETE_REQUEST_CHARGE=` +
      `${cleanup.requestCharge.toFixed(2)}`,
  );
  console.log(
    `  UPSERT_REQUEST_CHARGE=` +
      `${seed.requestCharge.toFixed(2)}`,
  );
}

main().catch((error) => {
  console.error(`ERROR: ${error.message}`);
  process.exit(1);
});