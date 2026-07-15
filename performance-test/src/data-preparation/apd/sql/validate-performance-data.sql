\set ON_ERROR_STOP on

DROP TABLE IF EXISTS gpdts_seed_validation;

CREATE TEMP TABLE gpdts_seed_validation AS
WITH owned_positions AS (
  SELECT pp.*
  FROM :"schema_name".payment_position pp
  WHERE pp.inserted_date >= :'test_day'::date
    AND pp.inserted_date < :'test_day'::date + INTERVAL '1 day'
    AND pp.iupd LIKE :'marker_prefix' || :'run_id' || '_%'
),
owned_options AS (
  SELECT po.*
  FROM :"schema_name".payment_option po
  JOIN owned_positions pp
    ON pp.id = po.payment_position_id
),
owned_transfers AS (
  SELECT t.*
  FROM :"schema_name".transfer t
  JOIN owned_options po
    ON po.id = t.payment_option_id
),
owned_candidates AS (
  SELECT po.id
  FROM owned_positions pp
  JOIN owned_options po
    ON po.payment_position_id = pp.id
  WHERE pp.service_type = ANY (string_to_array(:'service_types', ','))
    AND pp.status IN ('VALID', 'PARTIALLY_PAID', 'EXPIRED', 'INVALID')
    AND po.status = 'PO_UNPAID'
    AND pp.archived = false
    AND po.archived = false
)
SELECT
  :'positions_to_create'::integer AS expected_positions,
  
  GREATEST(
  1,
  (:'positions_to_create'::integer * 1) / 100
) AS expected_expired_positions,

GREATEST(
  1,
  (:'positions_to_create'::integer * 1) / 100
) AS expected_invalid_positions,

:'positions_to_create'::integer
  - GREATEST(
      1,
      (:'positions_to_create'::integer * 1) / 100
    )
  - GREATEST(
      1,
      (:'positions_to_create'::integer * 1) / 100
    )
  AS expected_valid_positions,

  (SELECT count(*) FROM owned_positions)
    AS payment_positions,

  (SELECT count(*) FROM owned_options)
    AS payment_options,

  (SELECT count(*) FROM owned_transfers)
    AS transfers,

  (SELECT count(*) FROM owned_candidates)
    AS reconciliation_candidates,

  (SELECT count(*) FROM owned_positions WHERE status = 'VALID')
    AS valid_positions,

  (SELECT count(*) FROM owned_positions WHERE status = 'EXPIRED')
    AS expired_positions,

  (SELECT count(*) FROM owned_positions WHERE status = 'INVALID')
    AS invalid_positions,

  (SELECT count(*)
   FROM owned_options
   WHERE status = 'PO_UNPAID'
     AND archived = false)
    AS unpaid_options,

  (SELECT count(*)
   FROM owned_transfers
   WHERE status = 'T_UNREPORTED')
    AS unreported_transfers,

  (SELECT count(*)
   FROM owned_options
   WHERE length(iuv) = 17
     AND length(nav) = 18
     AND nav = '3' || iuv)
    AS valid_navs;

TABLE gpdts_seed_validation;

SELECT (
  expected_positions = payment_positions
  AND expected_positions = payment_options
  AND expected_positions = transfers
  AND expected_positions = reconciliation_candidates
  AND expected_positions = unpaid_options
  AND expected_positions = unreported_transfers
  AND expected_positions = valid_navs
  AND valid_positions = expected_valid_positions
  AND expired_positions = expected_expired_positions
  AND invalid_positions = expected_invalid_positions
) AS seed_valid
FROM gpdts_seed_validation
\gset

\if :seed_valid
  \echo 'APD_SEED_VALIDATION_PASSED'
\else
  \echo 'ERROR: APD seed validation failed.'
  \quit 31
\endif

\echo 'First 10 inserted performance candidates:'

SELECT
  pp.id AS payment_position_id,
  pp.iupd,
  pp.status AS payment_position_status,
  po.id AS payment_option_id,
  po.iuv,
  po.nav,
  po.status AS payment_option_status,
  t.id AS transfer_database_id,
  t.status AS transfer_status
FROM :"schema_name".payment_position pp
JOIN :"schema_name".payment_option po
  ON po.payment_position_id = pp.id
JOIN :"schema_name".transfer t
  ON t.payment_option_id = po.id
WHERE pp.inserted_date >= :'test_day'::date
  AND pp.inserted_date < :'test_day'::date + INTERVAL '1 day'
  AND pp.iupd LIKE :'marker_prefix' || :'run_id' || '_%'
ORDER BY po.id
LIMIT 10;