\set ON_ERROR_STOP on

DROP TABLE IF EXISTS gpdts_result_validation;

CREATE TEMP TABLE gpdts_result_validation AS
WITH parameters AS (
  SELECT
    :'positions_to_create'::integer
      AS expected_positions,

    GREATEST(
      1,
      (:'positions_to_create'::integer * 1) / 100
    ) AS expected_expired,

    GREATEST(
      1,
      (:'positions_to_create'::integer * 1) / 100
    ) AS expected_invalid
),
expected AS (
  SELECT
    expected_positions,
    expected_expired,
    expected_invalid,
    expected_positions
      - expected_expired
      - expected_invalid
      AS expected_paid
  FROM parameters
),
owned_positions AS (
  SELECT
    pp.id AS payment_position_id,
    pp.iupd,
    right(pp.iupd, 8)::integer AS ordinal,
    pp.status AS payment_position_status
  FROM :"schema_name".payment_position pp
  WHERE pp.inserted_date >= :'test_day'::date
    AND pp.inserted_date
      < :'test_day'::date + INTERVAL '1 day'
    AND pp.iupd LIKE
      :'marker_prefix' || :'run_id' || '_%'
),
owned_graph AS (
  SELECT
    pp.payment_position_id,
    pp.iupd,
    pp.ordinal,
    pp.payment_position_status,
    po.id AS payment_option_id,
    po.status AS payment_option_status,
    po.nav,
    po.iuv
  FROM owned_positions pp
  JOIN :"schema_name".payment_option po
    ON po.payment_position_id =
       pp.payment_position_id
)
SELECT
  e.expected_positions,
  e.expected_paid,
  e.expected_expired,
  e.expected_invalid,

  count(g.payment_position_id)
    AS payment_positions,

  count(g.payment_option_id)
    AS payment_options,

  count(*) FILTER (
    WHERE g.ordinal <= e.expected_paid
      AND g.payment_position_status = 'PAID'
  ) AS paid_positions,

  count(*) FILTER (
    WHERE g.ordinal <= e.expected_paid
      AND g.payment_option_status = 'PO_PAID'
  ) AS paid_options,

  count(*) FILTER (
    WHERE g.ordinal > e.expected_paid
      AND g.ordinal
        <= e.expected_paid + e.expected_expired
      AND g.payment_position_status = 'EXPIRED'
  ) AS expired_positions,

  count(*) FILTER (
    WHERE g.ordinal > e.expected_paid
      AND g.ordinal
        <= e.expected_paid + e.expected_expired
      AND g.payment_option_status = 'PO_UNPAID'
  ) AS expired_unpaid_options,

  count(*) FILTER (
    WHERE g.ordinal
        > e.expected_paid + e.expected_expired
      AND g.payment_position_status = 'INVALID'
  ) AS invalid_positions,

  count(*) FILTER (
    WHERE g.ordinal
        > e.expected_paid + e.expected_expired
      AND g.payment_option_status = 'PO_UNPAID'
  ) AS invalid_unpaid_options,

  count(*) FILTER (
    WHERE NOT (
      (
        g.ordinal <= e.expected_paid
        AND g.payment_position_status = 'PAID'
        AND g.payment_option_status = 'PO_PAID'
      )
      OR
      (
        g.ordinal > e.expected_paid
        AND g.ordinal
          <= e.expected_paid + e.expected_expired
        AND g.payment_position_status = 'EXPIRED'
        AND g.payment_option_status = 'PO_UNPAID'
      )
      OR
      (
        g.ordinal
          > e.expected_paid + e.expected_expired
        AND g.payment_position_status = 'INVALID'
        AND g.payment_option_status = 'PO_UNPAID'
      )
    )
  ) AS unexpected_states

FROM expected e
LEFT JOIN owned_graph g
  ON true
GROUP BY
  e.expected_positions,
  e.expected_paid,
  e.expected_expired,
  e.expected_invalid;

TABLE gpdts_result_validation;

SELECT (
  expected_positions = payment_positions
  AND expected_positions = payment_options
  AND expected_paid = paid_positions
  AND expected_paid = paid_options
  AND expected_expired = expired_positions
  AND expected_expired = expired_unpaid_options
  AND expected_invalid = invalid_positions
  AND expected_invalid = invalid_unpaid_options
  AND unexpected_states = 0
) AS validation_passed
FROM gpdts_result_validation
\gset

\if :validation_passed
  \echo 'APD_RECONCILIATION_VALIDATION_PASSED'
\else
  \echo 'ERROR: APD reconciliation result validation failed.'
  \quit 41
\endif

\if :verbose_logs

\echo 'Final APD performance states:'

SELECT
  pp.id AS payment_position_id,
  pp.iupd,
  pp.status AS payment_position_status,
  po.id AS payment_option_id,
  po.nav,
  po.iuv,
  po.status AS payment_option_status
FROM :"schema_name".payment_position pp
JOIN :"schema_name".payment_option po
  ON po.payment_position_id = pp.id
WHERE pp.inserted_date >= :'test_day'::date
  AND pp.inserted_date
    < :'test_day'::date + INTERVAL '1 day'
  AND pp.iupd LIKE
    :'marker_prefix' || :'run_id' || '_%'
ORDER BY right(pp.iupd, 8)::integer
LIMIT 10;

\else

\echo 'Final APD details omitted because LOG_MODE=SUMMARY.'

\endif