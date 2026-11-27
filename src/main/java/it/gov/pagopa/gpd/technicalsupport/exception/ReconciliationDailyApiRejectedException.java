package it.gov.pagopa.gpd.technicalsupport.exception;

public class ReconciliationDailyApiRejectedException extends RuntimeException {
  private static final long serialVersionUID = 1L;
  private final int statusCode;

  public ReconciliationDailyApiRejectedException(int statusCode) {
    super("Reconciliation API rejected the request; HTTP " + statusCode);
    this.statusCode = statusCode;
  }

  public int getStatusCode() {
    return statusCode;
  }
}
