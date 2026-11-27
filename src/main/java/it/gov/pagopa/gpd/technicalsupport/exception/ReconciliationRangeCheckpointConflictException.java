package it.gov.pagopa.gpd.technicalsupport.exception;

public class ReconciliationRangeCheckpointConflictException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  public ReconciliationRangeCheckpointConflictException(String message) {
    super(message);
  }

  public ReconciliationRangeCheckpointConflictException(String message, Throwable cause) {
    super(message, cause);
  }
}
