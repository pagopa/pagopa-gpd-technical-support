\set ON_ERROR_STOP on

BEGIN;
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '30min';

CREATE TEMP TABLE gpdts_owned_payment_positions (
  id bigint PRIMARY KEY
) ON COMMIT DROP;

INSERT INTO gpdts_owned_payment_positions (id)
SELECT pp.id
FROM :"schema_name".payment_position pp
WHERE pp.inserted_date >= :'test_day'::date
  AND pp.inserted_date <  :'test_day'::date + INTERVAL '1 day'
  AND pp.iupd LIKE :'marker_prefix' || '%';

CREATE TEMP TABLE gpdts_owned_payment_options (
  id bigint PRIMARY KEY
) ON COMMIT DROP;

INSERT INTO gpdts_owned_payment_options (id)
SELECT po.id
FROM :"schema_name".payment_option po
JOIN gpdts_owned_payment_positions pp ON pp.id = po.payment_position_id;

CREATE TEMP TABLE gpdts_owned_transfers (
  id bigint PRIMARY KEY
) ON COMMIT DROP;

INSERT INTO gpdts_owned_transfers (id)
SELECT t.id
FROM :"schema_name".transfer t
JOIN gpdts_owned_payment_options po ON po.id = t.payment_option_id;

SELECT
  (SELECT count(*) FROM gpdts_owned_payment_positions) AS payment_positions_found,
  (SELECT count(*) FROM gpdts_owned_payment_options) AS payment_options_found,
  (SELECT count(*) FROM gpdts_owned_transfers) AS transfers_found;

WITH deleted AS (
  DELETE FROM :"schema_name".transfer_metadata tm
  USING gpdts_owned_transfers t
  WHERE tm.transfer_id = t.id
  RETURNING 1
)
SELECT 'transfer_metadata' AS entity, count(*) AS deleted_rows FROM deleted;

WITH deleted AS (
  DELETE FROM :"schema_name".payment_option_metadata pom
  USING gpdts_owned_payment_options po
  WHERE pom.payment_option_id = po.id
  RETURNING 1
)
SELECT 'payment_option_metadata' AS entity, count(*) AS deleted_rows FROM deleted;

WITH deleted AS (
  DELETE FROM :"schema_name".transfer t
  USING gpdts_owned_transfers target
  WHERE t.id = target.id
  RETURNING 1
)
SELECT 'transfer' AS entity, count(*) AS deleted_rows FROM deleted;

WITH deleted AS (
  DELETE FROM :"schema_name".payment_option po
  USING gpdts_owned_payment_options target
  WHERE po.id = target.id
  RETURNING 1
)
SELECT 'payment_option' AS entity, count(*) AS deleted_rows FROM deleted;

WITH deleted AS (
  DELETE FROM :"schema_name".payment_position pp
  USING gpdts_owned_payment_positions target
  WHERE pp.id = target.id
  RETURNING 1
)
SELECT 'payment_position' AS entity, count(*) AS deleted_rows FROM deleted;

COMMIT;
