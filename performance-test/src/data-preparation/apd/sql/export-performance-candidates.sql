\set ON_ERROR_STOP on

SELECT json_build_object(
  'runId', :'run_id',
  'ordinal', right(pp.iupd, 8)::integer,
  'paymentPositionId', pp.id,
  'paymentOptionId', po.id,
  'paymentPositionStatus', pp.status,
  'organizationFiscalCode', pp.organization_fiscal_code,
  'iupd', pp.iupd,
  'iuv', po.iuv,
  'nav', po.nav,
  'amountInCents', po.amount
)::text
FROM :"schema_name".payment_position pp
JOIN :"schema_name".payment_option po
  ON po.payment_position_id = pp.id
WHERE pp.inserted_date >= :'test_day'::date
  AND pp.inserted_date < :'test_day'::date + INTERVAL '1 day'
  AND pp.iupd LIKE :'marker_prefix' || :'run_id' || '_%'
  AND pp.archived = false
  AND po.archived = false
ORDER BY right(pp.iupd, 8)::integer;