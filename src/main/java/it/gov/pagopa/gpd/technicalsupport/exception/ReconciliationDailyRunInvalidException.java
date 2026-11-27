package it.gov.pagopa.gpd.technicalsupport.exception;

public class ReconciliationDailyRunInvalidException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  public ReconciliationDailyRunInvalidException(String message) {
    super(message);
  }

  public ReconciliationDailyRunInvalidException(String message, Throwable cause) {
    super(message, cause);
  }
}
