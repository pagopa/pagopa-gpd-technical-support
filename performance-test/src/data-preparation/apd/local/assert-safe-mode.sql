\set ON_ERROR_STOP on

DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM apd.payment_position WHERE iupd LIKE 'GPDTS_PERF_%'
  ) THEN
    RAISE EXCEPTION 'Owned performance data was not deleted';
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM apd.payment_position WHERE iupd = 'FOREIGN_LOCAL_CANDIDATE'
  ) THEN
    RAISE EXCEPTION 'Foreign candidate was deleted without explicit purge authorization';
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM apd.payment_position WHERE iupd = 'FOREIGN_LOCAL_NON_CANDIDATE'
  ) THEN
    RAISE EXCEPTION 'Foreign non-candidate fixture was unexpectedly deleted';
  END IF;
END
$$;

SELECT 'SAFE_MODE_ASSERTIONS_PASSED' AS result;
