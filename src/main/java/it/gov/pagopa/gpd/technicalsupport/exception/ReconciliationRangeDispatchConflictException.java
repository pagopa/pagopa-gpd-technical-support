package it.gov.pagopa.gpd.technicalsupport.exception;

public class ReconciliationRangeDispatchConflictException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  public ReconciliationRangeDispatchConflictException(String message) {
    super(message);
  }

  public ReconciliationRangeDispatchConflictException(String message, Throwable cause) {
    super(message, cause);
  }
}
