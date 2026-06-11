package it.gov.pagopa.gpd.technicalsupport.client.gpd;

import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.gpd.GpdPaidPaymentOptionResponse;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.gpd.GpdPayPaymentOptionRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(
    name = "gpdPayApiClient",
    url = "${reconciliation.gpd-pay.base-url:http://localhost}")
public interface GpdPayApiClient {

  @PostMapping(
      value = "/organizations/{organizationFiscalCode}/paymentoptions/{nav}/pay",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  GpdPaidPaymentOptionResponse payPaymentOption(
      @RequestHeader(name = "Ocp-Apim-Subscription-Key") String apiKey,
      @PathVariable String organizationFiscalCode,
      @PathVariable String nav,
      @RequestBody GpdPayPaymentOptionRequest request);
}