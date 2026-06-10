package it.gov.pagopa.gpd.technicalsupport.model.reconciliation.biz.cosmos;

public record BizPositiveEventDocument(
    String id,
    String receiptId,
    DebtorPosition debtorPosition,
    Creditor creditor,
    PaymentInfo paymentInfo,
    Long timestamp,
    Properties properties,
    String eventStatus) {

  public record DebtorPosition(
      String noticeNumber,
      String iuv,
      String iur) {}

  public record Creditor(
      String idPA) {}

  public record PaymentInfo(
      String paymentDateTime,
      String IUR) {}

  public record Properties(
      String serviceIdentifier) {}
}