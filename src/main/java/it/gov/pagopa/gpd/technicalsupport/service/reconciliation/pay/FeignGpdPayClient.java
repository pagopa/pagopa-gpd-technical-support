package it.gov.pagopa.gpd.technicalsupport.service.reconciliation.pay;

import it.gov.pagopa.gpd.technicalsupport.client.gpd.GpdPayApiClient;
import it.gov.pagopa.gpd.technicalsupport.config.reconciliation.gpd.GpdPayProperties;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.apd.ReconciliationCandidate;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.biz.BizPositiveEvent;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.gpd.GpdPayPaymentOptionRequest;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.gpd.GpdPayRecoveryResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class FeignGpdPayClient implements GpdPayClient {

  private final GpdPayApiClient gpdPayApiClient;
  private final GpdPayProperties properties;
  private final GpdPayRequestMapper requestMapper;

  @Override
  public GpdPayRecoveryResult executePayRecovery(
      ReconciliationCandidate candidate,
      BizPositiveEvent event) {

    try {
      GpdPayPaymentOptionRequest request = requestMapper.toPayRequest(event);

      log.info(
          "Executing GPD PAY recovery. paymentPositionId={}, paymentOptionId={}, ec={}, nav={}, bizEventId={}, receiptId={}, pspCode={}, pspTaxCode={}",
          candidate.paymentPositionId(),
          candidate.paymentOptionId(),
          candidate.ec(),
          candidate.nav(),
          event.eventId(),
          event.receiptId(),
          event.pspCode(),
          event.pspTaxCode());

      gpdPayApiClient.payPaymentOption(
          properties.getApiKey(),
          candidate.ec(),
          candidate.nav(),
          request);

      return GpdPayRecoveryResult.success();

    } catch (Exception e) {
      log.error(
          "GPD PAY recovery failed. paymentPositionId={}, paymentOptionId={}, ec={}, nav={}, bizEventId={}",
          candidate.paymentPositionId(),
          candidate.paymentOptionId(),
          candidate.ec(),
          candidate.nav(),
          event.eventId(),
          e);

      return GpdPayRecoveryResult.failed(e);
    }
  }
}