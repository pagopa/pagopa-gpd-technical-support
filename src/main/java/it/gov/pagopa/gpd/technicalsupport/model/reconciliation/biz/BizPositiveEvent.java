package it.gov.pagopa.gpd.technicalsupport.model.reconciliation.biz;

public record BizPositiveEvent(
    String eventId,
    String receiptId,
    String ec,
    String nav,
    String iuv,
    String iur,
    String paymentDateTime,
    String paymentMethod,
    String fee,
    Long eventTimestamp,
    String eventStatus,
    String serviceIdentifier,
    String pspCode,
    String pspTaxCode,
    String pspCompany,
    String idBrokerPsp,
    String idChannel) {}