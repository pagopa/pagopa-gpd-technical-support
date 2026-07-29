\set ON_ERROR_STOP on

\echo 'Running APD preflight checks...'

SELECT current_database() AS database,
       current_user AS database_user,
       current_setting('TimeZone') AS timezone,
       version() AS postgres_version,
       pg_is_in_recovery() AS is_read_replica;

SELECT (
  :'test_day'::date <= current_date - :'min_processing_delay_days'::integer
) AS valid_test_day
\gset

\if :valid_test_day
\else
  \echo 'ERROR: TEST_DAY does not respect the configured minimum processing delay.'
  \quit 11
\endif

SELECT (:'marker_prefix' = 'GPDTS_PERF_') AS valid_marker_prefix
\gset

\if :valid_marker_prefix
\else
  \echo 'ERROR: performanceDataPrefix must remain exactly GPDTS_PERF_.'
  \quit 12
\endif

WITH requested_service_types AS (
  SELECT value
  FROM unnest(string_to_array(:'service_types', ',')) AS value
)
SELECT
  count(*) > 0
  AND bool_and(value = ANY (ARRAY['ACA', 'GPD', 'WISP']))
  AS valid_service_types
FROM requested_service_types
\gset

\if :valid_service_types
\else
  \echo 'ERROR: serviceTypes must contain only ACA, GPD and/or WISP.'
  \quit 13
\endif

SELECT (
  to_regclass(format('%I.payment_position', :'schema_name')) IS NOT NULL
  AND to_regclass(format('%I.payment_option', :'schema_name')) IS NOT NULL
  AND to_regclass(format('%I.transfer', :'schema_name')) IS NOT NULL
  AND to_regclass(format('%I.payment_option_metadata', :'schema_name')) IS NOT NULL
  AND to_regclass(format('%I.transfer_metadata', :'schema_name')) IS NOT NULL
) AS required_tables_exist
\gset

\if :required_tables_exist
\else
  \echo 'ERROR: one or more required APD tables do not exist.'
  \quit 14
\endif

SELECT NOT pg_is_in_recovery() AS writable_server
\gset

\if :writable_server
\else
  \echo 'ERROR: the connected PostgreSQL instance is a read replica.'
  \quit 15
\endif

SELECT (
  has_table_privilege(current_user, format('%I.payment_position', :'schema_name'), 'SELECT')
  AND has_table_privilege(current_user, format('%I.payment_position', :'schema_name'), 'INSERT')
  AND has_table_privilege(current_user, format('%I.payment_position', :'schema_name'), 'DELETE')
  AND has_table_privilege(current_user, format('%I.payment_option', :'schema_name'), 'SELECT')
  AND has_table_privilege(current_user, format('%I.payment_option', :'schema_name'), 'INSERT')
  AND has_table_privilege(current_user, format('%I.payment_option', :'schema_name'), 'DELETE')
  AND has_table_privilege(current_user, format('%I.transfer', :'schema_name'), 'SELECT')
  AND has_table_privilege(current_user, format('%I.transfer', :'schema_name'), 'INSERT')
  AND has_table_privilege(current_user, format('%I.transfer', :'schema_name'), 'DELETE')
  AND has_table_privilege(current_user, format('%I.payment_option_metadata', :'schema_name'), 'SELECT')
  AND has_table_privilege(current_user, format('%I.payment_option_metadata', :'schema_name'), 'DELETE')
  AND has_table_privilege(current_user, format('%I.transfer_metadata', :'schema_name'), 'SELECT')
  AND has_table_privilege(current_user, format('%I.transfer_metadata', :'schema_name'), 'DELETE')
) AS required_privileges_exist
\gset

\if :required_privileges_exist
\else
  \echo 'ERROR: current user needs the required SELECT/INSERT/DELETE privileges on APD test tables.'
  \quit 16
\endif

\echo 'APD preflight checks completed successfully.'
