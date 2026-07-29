\set ON_ERROR_STOP on

TRUNCATE TABLE
  apd.transfer_metadata,
  apd.payment_option_metadata,
  apd.transfer,
  apd.payment_option,
  apd.payment_position
RESTART IDENTITY CASCADE;

SELECT nextval('apd.payment_pos_seq') AS own_pp_id \gset
SELECT nextval('apd.payment_opt_seq') AS own_po_id \gset
SELECT nextval('apd.transfer_seq') AS own_transfer_id \gset
SELECT nextval('apd.payment_opt_metadata_seq') AS own_po_metadata_id \gset
SELECT nextval('apd.transfer_metadata_seq') AS own_transfer_metadata_id \gset

INSERT INTO apd.payment_position (
  id, iupd, organization_fiscal_code, service_type, status, inserted_date, archived
) VALUES (
  :own_pp_id,
  'GPDTS_PERF_LOCAL_OWN',
  '99999999982',
  'GPD',
  'VALID',
  :'test_day'::date + time '01:00:00',
  false
);

INSERT INTO apd.payment_option (
  id, payment_position_id, organization_fiscal_code, nav, iuv, status, payment_plan_id, archived
) VALUES (
  :own_po_id,
  :own_pp_id,
  '99999999982',
  '301000000000000001',
  '01000000000000001',
  'PO_UNPAID',
  NULL,
  false
);

INSERT INTO apd.transfer (id, payment_option_id)
VALUES (:own_transfer_id, :own_po_id);

INSERT INTO apd.payment_option_metadata (id, payment_option_id, key, value)
VALUES (:own_po_metadata_id, :own_po_id, 'fixture', 'owned');

INSERT INTO apd.transfer_metadata (id, transfer_id, key, value)
VALUES (:own_transfer_metadata_id, :own_transfer_id, 'fixture', 'owned');

SELECT nextval('apd.payment_pos_seq') AS foreign_pp_id \gset
SELECT nextval('apd.payment_opt_seq') AS foreign_po_id \gset
SELECT nextval('apd.transfer_seq') AS foreign_transfer_id \gset
SELECT nextval('apd.payment_opt_metadata_seq') AS foreign_po_metadata_id \gset
SELECT nextval('apd.transfer_metadata_seq') AS foreign_transfer_metadata_id \gset

INSERT INTO apd.payment_position (
  id, iupd, organization_fiscal_code, service_type, status, inserted_date, archived
) VALUES (
  :foreign_pp_id,
  'FOREIGN_LOCAL_CANDIDATE',
  '77777777777',
  'GPD',
  'VALID',
  :'test_day'::date + time '02:00:00',
  false
);

INSERT INTO apd.payment_option (
  id, payment_position_id, organization_fiscal_code, nav, iuv, status, payment_plan_id, archived
) VALUES (
  :foreign_po_id,
  :foreign_pp_id,
  '77777777777',
  '301000000000000002',
  '01000000000000002',
  'PO_UNPAID',
  NULL,
  false
);

INSERT INTO apd.transfer (id, payment_option_id)
VALUES (:foreign_transfer_id, :foreign_po_id);

INSERT INTO apd.payment_option_metadata (id, payment_option_id, key, value)
VALUES (:foreign_po_metadata_id, :foreign_po_id, 'fixture', 'foreign-candidate');

INSERT INTO apd.transfer_metadata (id, transfer_id, key, value)
VALUES (:foreign_transfer_metadata_id, :foreign_transfer_id, 'fixture', 'foreign-candidate');

SELECT nextval('apd.payment_pos_seq') AS non_candidate_pp_id \gset
SELECT nextval('apd.payment_opt_seq') AS non_candidate_po_id \gset

INSERT INTO apd.payment_position (
  id, iupd, organization_fiscal_code, service_type, status, inserted_date, archived
) VALUES (
  :non_candidate_pp_id,
  'FOREIGN_LOCAL_NON_CANDIDATE',
  '77777777777',
  'GPD',
  'PAID',
  :'test_day'::date + time '03:00:00',
  false
);

INSERT INTO apd.payment_option (
  id, payment_position_id, organization_fiscal_code, nav, iuv, status, payment_plan_id, archived
) VALUES (
  :non_candidate_po_id,
  :non_candidate_pp_id,
  '77777777777',
  '301000000000000003',
  '01000000000000003',
  'PO_PAID',
  NULL,
  false
);

SELECT 'LOCAL_FIXTURE_READY' AS result;
