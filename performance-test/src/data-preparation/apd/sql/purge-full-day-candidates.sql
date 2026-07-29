\set ON_ERROR_STOP on

BEGIN;
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '60min';

CREATE TEMP TABLE gpdts_foreign_candidate_positions (
  id bigint PRIMARY KEY
) ON COMMIT DROP;

INSERT INTO gpdts_foreign_candidate_positions (id)
SELECT DISTINCT pp.id
FROM :"schema_name".payment_position pp
JOIN :"schema_name".payment_option po
  ON po.payment_position_id = pp.id
WHERE pp.inserted_date >= :'test_day'::date
  AND pp.inserted_date <  :'test_day'::date + INTERVAL '1 day'
  AND pp.service_type = ANY (string_to_array(:'service_types', ','))
  AND pp.status IN ('VALID', 'PARTIALLY_PAID', 'EXPIRED', 'INVALID')
  AND po.status = 'PO_UNPAID'
  AND pp.archived = false
  AND po.archived = false
  AND pp.iupd NOT LIKE :'marker_prefix' || '%'
  AND (
        pp.status <> 'PARTIALLY_PAID'
        OR (
            po.payment_plan_id IS NOT NULL
            AND EXISTS (
                SELECT 1
                FROM :"schema_name".payment_option po_paid
                WHERE po_paid.payment_position_id = po.payment_position_id
                  AND po_paid.status = 'PO_PAID'
                  AND po_paid.archived = false
                  AND po_paid.payment_plan_id = po.payment_plan_id
            )
        )
      );

CREATE TEMP TABLE gpdts_foreign_candidate_options (
  id bigint PRIMARY KEY
) ON COMMIT DROP;

INSERT INTO gpdts_foreign_candidate_options (id)
SELECT po.id
FROM :"schema_name".payment_option po
JOIN gpdts_foreign_candidate_positions pp ON pp.id = po.payment_position_id;

CREATE TEMP TABLE gpdts_foreign_candidate_transfers (
  id bigint PRIMARY KEY
) ON COMMIT DROP;

INSERT INTO gpdts_foreign_candidate_transfers (id)
SELECT t.id
FROM :"schema_name".transfer t
JOIN gpdts_foreign_candidate_options po ON po.id = t.payment_option_id;

SELECT
  (SELECT count(*) FROM gpdts_foreign_candidate_positions) AS payment_positions_to_purge,
  (SELECT count(*) FROM gpdts_foreign_candidate_options) AS payment_options_to_purge,
  (SELECT count(*) FROM gpdts_foreign_candidate_transfers) AS transfers_to_purge;

WITH deleted AS (
  DELETE FROM :"schema_name".transfer_metadata tm
  USING gpdts_foreign_candidate_transfers t
  WHERE tm.transfer_id = t.id
  RETURNING 1
)
SELECT 'transfer_metadata' AS entity, count(*) AS deleted_rows FROM deleted;

WITH deleted AS (
  DELETE FROM :"schema_name".payment_option_metadata pom
  USING gpdts_foreign_candidate_options po
  WHERE pom.payment_option_id = po.id
  RETURNING 1
)
SELECT 'payment_option_metadata' AS entity, count(*) AS deleted_rows FROM deleted;

WITH deleted AS (
  DELETE FROM :"schema_name".transfer t
  USING gpdts_foreign_candidate_transfers target
  WHERE t.id = target.id
  RETURNING 1
)
SELECT 'transfer' AS entity, count(*) AS deleted_rows FROM deleted;

WITH deleted AS (
  DELETE FROM :"schema_name".payment_option po
  USING gpdts_foreign_candidate_options target
  WHERE po.id = target.id
  RETURNING 1
)
SELECT 'payment_option' AS entity, count(*) AS deleted_rows FROM deleted;

WITH deleted AS (
  DELETE FROM :"schema_name".payment_position pp
  USING gpdts_foreign_candidate_positions target
  WHERE pp.id = target.id
  RETURNING 1
)
SELECT 'payment_position' AS entity, count(*) AS deleted_rows FROM deleted;

COMMIT;
