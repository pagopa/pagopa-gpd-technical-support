import http from 'k6/http';
import { check, fail } from 'k6';
import { SharedArray } from 'k6/data';
import { joinUrl } from './modules/helpers.js';

export let options = JSON.parse(open(__ENV.TEST_TYPE));

const varsArray = new SharedArray('vars', function () {
  return JSON.parse(open(`${__ENV.VARS}`)).environment;
});

// SharedArray currently exposes array values only.
const vars = varsArray[0];
const rootUrl = joinUrl(vars.host, vars.basePath);
const reconciliationUrl = joinUrl(rootUrl, vars.reconciliationPath);

export function setup() {
  if (!__ENV.TEST_DAY) {
    fail('TEST_DAY is required');
  }

  if (!vars.reconciliation?.serviceTypes?.length) {
    fail('At least one reconciliation service type must be configured');
  }

  return {
    testDay: __ENV.TEST_DAY,
    serviceTypes: vars.reconciliation.serviceTypes,
  };
}

export default function (data) {
  const headers = {
    'Content-Type': 'application/json',
  };

  if (__ENV.API_SUBSCRIPTION_KEY) {
    headers[vars.subscriptionHeader] = __ENV.API_SUBSCRIPTION_KEY;
  }

  const payload = JSON.stringify({
    from: data.testDay,
    to: data.testDay,
    serviceTypes: data.serviceTypes,
    force: false,
  });

  const response = http.post(reconciliationUrl, payload, {
    headers,
    tags: {
      gpsMethod: 'position-status-reconciliation',
    },
  });

  let responseBody = null;
  try {
    responseBody = response.json();
  } catch (_) {
    // The status assertion below remains the authoritative trigger check.
  }

  check(response, {
    'reconciliation trigger status is 202': (r) => r.status === 202,
    'reconciliation request is accepted': () => responseBody?.accepted === true,
    'reconciliation response contains a run': () =>
      Array.isArray(responseBody?.runs) && responseBody.runs.length === 1,
  });
}
