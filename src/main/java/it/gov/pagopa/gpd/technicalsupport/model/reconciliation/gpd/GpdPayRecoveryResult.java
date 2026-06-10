package it.gov.pagopa.gpd.technicalsupport.model.reconciliation.gpd;

public record GpdPayRecoveryResult(
    boolean succeeded,
    String errorCode,
    String errorMessage) {

  public static GpdPayRecoveryResult success() {
    return new GpdPayRecoveryResult(true, null, null);
  }

  public static GpdPayRecoveryResult failed(String errorCode, String errorMessage) {
    return new GpdPayRecoveryResult(false, errorCode, errorMessage);
  }

  public static GpdPayRecoveryResult failed(Throwable error) {
    return new GpdPayRecoveryResult(
        false,
        error == null ? null : error.getClass().getSimpleName(),
        error == null ? null : error.getMessage());
  }
}