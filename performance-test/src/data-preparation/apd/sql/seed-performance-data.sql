\set ON_ERROR_STOP on

BEGIN;

SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '30min';

CREATE TEMP TABLE gpdts_seed_data ON COMMIT DROP AS
WITH requested_distribution AS (
  SELECT
    :'positions_to_create'::integer AS total_count,
    GREATEST(
      1,
      (:'positions_to_create'::integer * 1) / 100
    ) AS expired_count,
    GREATEST(
      1,
      (:'positions_to_create'::integer * 1) / 100
    ) AS invalid_count
),
parameters AS (
  SELECT
    total_count,
    total_count - expired_count - invalid_count AS valid_count,
    expired_count,
    invalid_count
  FROM requested_distribution
),
allocated_ids AS (
  SELECT
    series.ordinal,
    parameters.total_count,
    parameters.valid_count,
    parameters.expired_count,
    parameters.invalid_count,
    nextval(
      format('%I.payment_pos_seq', :'schema_name')::regclass
    ) AS payment_position_id,
    nextval(
      format('%I.payment_opt_seq', :'schema_name')::regclass
    ) AS payment_option_id,
    nextval(
      format('%I.transfer_seq', :'schema_name')::regclass
    ) AS transfer_database_id
  FROM parameters
  CROSS JOIN generate_series(
    1,
    parameters.total_count
  ) AS series(ordinal)
),
enriched_data AS (
  SELECT
    allocated_ids.*,
    CASE
      WHEN ordinal <= valid_count THEN 'VALID'
      WHEN ordinal <= valid_count + expired_count THEN 'EXPIRED'
      ELSE 'INVALID'
    END AS payment_position_status,
    :'marker_prefix'
      || :'run_id'
      || '_'
      || lpad(ordinal::text, 8, '0') AS iupd,
    '01'
      || lpad(payment_option_id::text, 15, '0') AS iuv,
    :'test_day'::date
      + time '12:00:00'
      + ordinal * interval '1 millisecond' AS inserted_date
  FROM allocated_ids
)
SELECT
  enriched_data.*,
  '3' || iuv AS nav
FROM enriched_data;

INSERT INTO :"schema_name".payment_position (
  id,
  iupd,
  organization_fiscal_code,
  pull,
  pay_stand_in,
  type,
  fiscal_code,
  full_name,
  street_name,
  civic_number,
  postal_code,
  city,
  province,
  region,
  country,
  email,
  phone,
  service_type,
  company_name,
  office_name,
  inserted_date,
  publish_date,
  min_due_date,
  max_due_date,
  status,
  last_updated_date,
  payment_date,
  version,
  validity_date,
  switch_to_expired,
  archived
)
SELECT
  payment_position_id,
  iupd,
  :'organization_fiscal_code',
  true,
  true,
  'F',
  'TSTFSC00A00H501X',
  'GPDTS Performance Test',
  'Via Test',
  '1',
  '00100',
  'Roma',
  'RM',
  'Lazio',
  'IT',
  NULL,
  NULL,
  'GPD',
  'GPD Technical Support',
  'Performance Test',
  inserted_date,
  inserted_date,
  (current_date + 30) + time '23:59:59',
  (current_date + 30) + time '23:59:59',
  payment_position_status,
  inserted_date,
  NULL,
  0,
  NULL,
  false,
  false
FROM gpdts_seed_data;

INSERT INTO :"schema_name".payment_option (
  id,
  nav,
  iuv,
  organization_fiscal_code,
  payment_plan_id,
  amount,
  description,
  payment_option_description,
  is_partial_payment,
  validity_date,
  due_date,
  retention_date,
  payment_date,
  reporting_date,
  inserted_date,
  payment_method,
  fee,
  notification_fee,
  psp_code,
  psp_tax_code,
  psp_company,
  receipt_id,
  flow_reporting_id,
  status,
  last_updated_date,
  last_updated_date_notification_fee,
  type,
  fiscal_code,
  full_name,
  street_name,
  civic_number,
  postal_code,
  city,
  province,
  region,
  country,
  email,
  phone,
  send_sync,
  switch_to_expired,
  payment_position_id,
  archived
)
SELECT
  payment_option_id,
  nav,
  iuv,
  :'organization_fiscal_code',
  'SINGLE_OPTION',
  10000,
  'GPDTS performance payment option',
  'GPDTS performance payment option',
  false,
  NULL,
  (current_date + 30) + time '23:59:59',
  NULL,
  NULL,
  NULL,
  inserted_date,
  NULL,
  0,
  0,
  NULL,
  NULL,
  NULL,
  NULL,
  NULL,
  'PO_UNPAID',
  inserted_date,
  NULL,
  'F',
  'TSTFSC00A00H501X',
  'GPDTS Performance Test',
  'Via Test',
  '1',
  '00100',
  'Roma',
  'RM',
  'Lazio',
  'IT',
  NULL,
  NULL,
  false,
  false,
  payment_position_id,
  false
FROM gpdts_seed_data;

INSERT INTO :"schema_name".transfer (
  id,
  organization_fiscal_code,
  transfer_id,
  iuv,
  amount,
  remittance_information,
  category,
  iban,
  postal_iban,
  hash_document,
  stamp_type,
  provincial_residence,
  company_name,
  inserted_date,
  status,
  last_updated_date,
  payment_option_id
)
SELECT
  transfer_database_id,
  :'organization_fiscal_code',
  '1',
  iuv,
  10000,
  'GPDTS performance test ' || :'run_id' || ' #' || ordinal,
  '9/0101108TS/',
  NULL,
  NULL,
  NULL,
  NULL,
  NULL,
  'GPD Technical Support',
  inserted_date,
  'T_UNREPORTED',
  inserted_date,
  payment_option_id
FROM gpdts_seed_data;

SELECT
  count(*) AS inserted_payment_positions
FROM gpdts_seed_data;

COMMIT;