package it.gov.pagopa.gpd.technicalsupport.model.reconciliation.gpd;

public record GpdPayPaymentOptionRequest(
    String paymentDate,
    String paymentMethod,
    String pspCode,
    String pspTaxCode,
    String pspCompany,
    String idReceipt,
    String fee) {}