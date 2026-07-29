\set ON_ERROR_STOP on

\echo 'Foreign candidate summary:'
SELECT
  pp.service_type,
  pp.status AS payment_position_status,
  po.status AS payment_option_status,
  count(*) AS candidate_count
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
      )
GROUP BY pp.service_type, pp.status, po.status
ORDER BY pp.service_type, pp.status;

\echo 'First 20 foreign candidates:'
SELECT
  pp.id AS payment_position_id,
  pp.iupd,
  pp.organization_fiscal_code,
  pp.service_type,
  pp.status AS payment_position_status,
  po.id AS payment_option_id,
  po.nav,
  po.status AS payment_option_status,
  po.payment_plan_id
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
      )
ORDER BY pp.id, po.id
LIMIT 20;
