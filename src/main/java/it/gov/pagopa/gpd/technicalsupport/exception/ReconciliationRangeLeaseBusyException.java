package it.gov.pagopa.gpd.technicalsupport.exception;

public class ReconciliationRangeLeaseBusyException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  public ReconciliationRangeLeaseBusyException(String message) {
    super(message);
  }
}
