package it.gov.pagopa.gpd.technicalsupport.service.reconciliation.pay;

import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.biz.BizPositiveEvent;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.gpd.GpdPayPaymentOptionRequest;
import org.springframework.stereotype.Component;

@Component
public class GpdPayRequestMapper {

  public GpdPayPaymentOptionRequest toPayRequest(BizPositiveEvent event) {
    return new GpdPayPaymentOptionRequest(
        event.paymentDateTime(),
        defaultIfBlank(event.paymentMethod(), "other"),
        event.pspCode(),
        event.pspTaxCode(),
        defaultIfBlank(event.pspCompany(), "UNKNOWN"),
        event.receiptId(),
        defaultIfBlank(event.fee(), "0"));
  }

  private String defaultIfBlank(String value, String defaultValue) {
    return value == null || value.isBlank() ? defaultValue : value;
  }
}