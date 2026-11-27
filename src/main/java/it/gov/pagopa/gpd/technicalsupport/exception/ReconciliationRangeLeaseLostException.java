package it.gov.pagopa.gpd.technicalsupport.exception;

public class ReconciliationRangeLeaseLostException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  public ReconciliationRangeLeaseLostException(String message) {
    super(message);
  }

  public ReconciliationRangeLeaseLostException(String message, Throwable cause) {
    super(message, cause);
  }
}
