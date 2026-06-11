package it.gov.pagopa.gpd.technicalsupport.model.reconciliation.biz.cosmos;

public record BizPositiveEventDocument(
    String id,
    String receiptId,
    DebtorPosition debtorPosition,
    Creditor creditor,
    Psp psp,
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

  public record Psp(
      String idPsp,
      String idBrokerPsp,
      String idChannel,
      String psp,
      String pspPartitaIVA,
      String pspFiscalCode,
      String channelDescription) {}

  public record PaymentInfo(
      String paymentDateTime,
      String paymentMethod,
      String fee,
      String paymentToken,
      String IUR) {}

  public record Properties(
      String serviceIdentifier) {}
}