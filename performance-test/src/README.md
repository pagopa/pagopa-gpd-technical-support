# k6 reconciliation tests

The k6 scripts read their options and environment configuration exactly as in the PagoPA load-test template:

```javascript
export let options = JSON.parse(open(__ENV.TEST_TYPE));

const varsArray = new SharedArray('vars', function () {
  return JSON.parse(open(`${__ENV.VARS}`)).environment;
});

const vars = varsArray[0];
```

`VARS` resolves to:

```text
/scripts/environments/<local|dev|uat|prod>.environment.json
```

`TEST_TYPE` resolves to:

```text
/scripts/test-types/<type>.json
```

The current `reconciliation_workflow.js` triggers one asynchronous reconciliation request when used with `pilot.json`. Polling of the run and end-to-end result validation will be introduced after the APD and Biz+ seed steps.
