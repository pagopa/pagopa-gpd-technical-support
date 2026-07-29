\set ON_ERROR_STOP on

DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM apd.payment_position WHERE iupd = 'FOREIGN_LOCAL_CANDIDATE'
  ) THEN
    RAISE EXCEPTION 'Foreign candidate was not deleted by explicit full-day purge';
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM apd.payment_position WHERE iupd = 'FOREIGN_LOCAL_NON_CANDIDATE'
  ) THEN
    RAISE EXCEPTION 'Foreign non-candidate fixture was unexpectedly deleted';
  END IF;

  IF EXISTS (
    SELECT 1
    FROM apd.payment_option po
    JOIN apd.payment_position pp ON pp.id = po.payment_position_id
    WHERE pp.iupd = 'FOREIGN_LOCAL_CANDIDATE'
  ) THEN
    RAISE EXCEPTION 'Foreign candidate payment options were not deleted';
  END IF;
END
$$;

SELECT 'FULL_PURGE_ASSERTIONS_PASSED' AS result;
