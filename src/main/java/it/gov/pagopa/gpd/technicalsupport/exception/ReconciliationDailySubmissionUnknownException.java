package it.gov.pagopa.gpd.technicalsupport.exception;

public class ReconciliationDailySubmissionUnknownException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  public ReconciliationDailySubmissionUnknownException(String message) {
    super(message);
  }

  public ReconciliationDailySubmissionUnknownException(String message, Throwable cause) {
    super(message, cause);
  }
}
