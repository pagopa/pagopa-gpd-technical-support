package it.gov.pagopa.gpd.technicalsupport.model.reconciliation.biz;

public record BizPositiveEventLookupResult(
    BizPositiveEvent event,
    BizPositiveEventLookupStatus status,
    String errorCode,
    String errorMessage) {

  public static BizPositiveEventLookupResult found(BizPositiveEvent event) {
    return new BizPositiveEventLookupResult(
        event,
        BizPositiveEventLookupStatus.FOUND,
        null,
        null);
  }

  public static BizPositiveEventLookupResult notFound() {
    return new BizPositiveEventLookupResult(
        null,
        BizPositiveEventLookupStatus.NOT_FOUND,
        null,
        null);
  }

  public static BizPositiveEventLookupResult failed(Throwable error) {
    return new BizPositiveEventLookupResult(
        null,
        BizPositiveEventLookupStatus.FAILED,
        error == null ? null : error.getClass().getSimpleName(),
        error == null ? null : error.getMessage());
  }

  public static BizPositiveEventLookupResult failed(String errorCode, String errorMessage) {
    return new BizPositiveEventLookupResult(
        null,
        BizPositiveEventLookupStatus.FAILED,
        errorCode,
        errorMessage);
  }
}