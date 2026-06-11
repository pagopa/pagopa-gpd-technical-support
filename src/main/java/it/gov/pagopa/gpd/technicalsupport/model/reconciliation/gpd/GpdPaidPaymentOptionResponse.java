package it.gov.pagopa.gpd.technicalsupport.model.reconciliation.gpd;

public record GpdPaidPaymentOptionResponse(
    String nav,
    String iuv,
    String amount,
    String status,
    String serviceType) {}