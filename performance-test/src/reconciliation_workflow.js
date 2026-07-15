import http from "k6/http";
import { check, fail } from "k6";
import { SharedArray } from "k6/data";
import { Counter, Trend } from "k6/metrics";
import { joinUrl } from "./modules/helpers.js";

export let options = JSON.parse(open(__ENV.TEST_TYPE));

const varsArray = new SharedArray("vars", function () {
  return JSON.parse(open(__ENV.VARS)).environment;
});

const vars = varsArray[0];
const reconciliationConfiguration =
  vars.reconciliation || {};

const verboseLogsValue =
  __ENV.VERBOSE_LOGS || "";

const verboseLogs =
  String(verboseLogsValue)
    .trim()
    .toLowerCase() === "true";

const positionsToCreate = Number(
  __ENV.POSITIONS_TO_CREATE || "0"
);

const rootUrl = joinUrl(
  vars.host,
  vars.basePath
);

const reconciliationUrl = joinUrl(
  rootUrl,
  vars.reconciliationPath
);

const reconciliationTriggerDuration = new Trend(
  "reconciliation_trigger_duration",
  true
);

const reconciliationTriggerAccepted = new Counter(
  "reconciliation_trigger_accepted"
);

function requiredValue(value, name) {
  if (
    value === undefined ||
    value === null ||
    String(value).trim() === ""
  ) {
    fail(name + " is required");
  }

  return value;
}

function parseResponseBody(response) {
  try {
    return response.json();
  } catch (_) {
    return null;
  }
}

function responsePreview(response) {
  const body =
    response.body === undefined ||
    response.body === null
      ? ""
      : response.body;

  return String(body).slice(0, 2000);
}

function getRun(responseBody) {
  if (
    responseBody === null ||
    !Array.isArray(responseBody.runs) ||
    responseBody.runs.length === 0
  ) {
    return null;
  }

  return responseBody.runs[0];
}

export function setup() {
  const testDay = requiredValue(
    __ENV.TEST_DAY,
    "TEST_DAY"
  );

  requiredValue(
    __ENV.API_SUBSCRIPTION_KEY,
    "API_SUBSCRIPTION_KEY"
  );

  const serviceTypes =
    reconciliationConfiguration.serviceTypes;

  if (!Array.isArray(serviceTypes)) {
    fail(
      "reconciliation.serviceTypes must be configured"
    );
  }

  if (serviceTypes.length === 0) {
    fail(
      "At least one reconciliation service type is required"
    );
  }

  return {
    testDay: testDay,
    serviceTypes: serviceTypes,
    force:
      reconciliationConfiguration.force === true,
    triggerTimeout:
      reconciliationConfiguration.triggerTimeout ||
      "30s"
  };
}

export default function (data) {
  const requestId =
    "gpdts-perf-" +
    data.testDay +
    "-" +
    Date.now() +
    "-" +
    __VU +
    "-" +
    __ITER;

  const headers = {
    "Content-Type": "application/json",
    "X-Request-Id": requestId
  };

  const subscriptionHeader =
    vars.subscriptionHeader ||
    "Ocp-Apim-Subscription-Key";

  headers[subscriptionHeader] =
    __ENV.API_SUBSCRIPTION_KEY;

  const request = {
    from: data.testDay,
    to: data.testDay,
    serviceTypes: data.serviceTypes,
    force: data.force
  };

  if (verboseLogs) {
    console.log(
      "RECONCILIATION_REQUEST=" +
        JSON.stringify(
          {
            url: reconciliationUrl,
            requestId: requestId,
            body: request
          },
          null,
          2
        )
    );
  }

  const response = http.post(
    reconciliationUrl,
    JSON.stringify(request),
    {
      headers: headers,
      timeout: data.triggerTimeout,
      tags: {
        gpsMethod:
          "position-status-reconciliation",
        testDay: data.testDay
      }
    }
  );

  reconciliationTriggerDuration.add(
    response.timings.duration
  );

  const responseBody =
    parseResponseBody(response);

  const run = getRun(responseBody);

  const successful = check(response, {
    "trigger response status is 202": function (
      result
    ) {
      return result.status === 202;
    },

    "reconciliation request is accepted":
      function () {
        return (
          responseBody !== null &&
          responseBody.accepted === true
        );
      },

    "response contains exactly one run":
      function () {
        return (
          responseBody !== null &&
          Array.isArray(responseBody.runs) &&
          responseBody.runs.length === 1
        );
      },

    "run day matches TEST_DAY": function () {
      return (
        run !== null &&
        run.day === data.testDay
      );
    },

    "run contains logicalRunKey": function () {
      return (
        run !== null &&
        typeof run.logicalRunKey === "string" &&
        run.logicalRunKey.length > 0
      );
    },

    "run contains executionId": function () {
      return (
        run !== null &&
        typeof run.executionId === "string" &&
        run.executionId.length > 0
      );
    },

    "run has an accepted initial status":
      function () {
        return (
          run !== null &&
          (
            run.status === "CREATED" ||
            run.status === "RUNNING"
          )
        );
      }
  });

  if (successful) {
    reconciliationTriggerAccepted.add(1);
  }

  const responseHeaders =
    response.headers || {};

  const responseRequestId =
    responseHeaders["X-Request-Id"] ||
    responseHeaders["x-request-id"] ||
    null;

  if (verboseLogs) {
    console.log(
      "RECONCILIATION_RESPONSE=" +
        JSON.stringify(
          {
            status: response.status,
            durationMs:
              response.timings.duration,
            headers: {
              requestId: responseRequestId
            },
            body: responseBody
          },
          null,
          2
        )
    );
  }

  const executionId =
    run !== null &&
    typeof run.executionId === "string"
      ? run.executionId
      : "N/A";

  console.log(
    "RECONCILIATION_TRIGGER " +
      "status=" +
      response.status +
      " durationMs=" +
      Math.round(response.timings.duration) +
      " positions=" +
      positionsToCreate +
      " executionId=" +
      executionId
  );

  if (
    run !== null &&
    typeof run.executionId === "string"
  ) {
    console.log(
      "RECONCILIATION_EXECUTION_ID=" +
        run.executionId
    );
  }

  if (
    run !== null &&
    typeof run.logicalRunKey === "string"
  ) {
    console.log(
      "RECONCILIATION_LOGICAL_RUN_KEY=" +
        run.logicalRunKey
    );
  }

  if (!successful) {
    fail(
      "Reconciliation trigger failed. " +
        "HTTP status=" +
        response.status +
        ", response=" +
        responsePreview(response)
    );
  }
}