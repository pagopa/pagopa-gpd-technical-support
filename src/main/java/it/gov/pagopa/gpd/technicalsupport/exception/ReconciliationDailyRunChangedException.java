package it.gov.pagopa.gpd.technicalsupport.exception;

public class ReconciliationDailyRunChangedException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  public ReconciliationDailyRunChangedException(String message) {
    super(message);
  }

  public ReconciliationDailyRunChangedException(String message, Throwable cause) {
    super(message, cause);
  }
}
