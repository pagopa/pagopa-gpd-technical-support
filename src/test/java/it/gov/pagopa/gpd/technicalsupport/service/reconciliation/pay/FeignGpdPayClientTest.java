package it.gov.pagopa.gpd.technicalsupport.service.reconciliation.pay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

import it.gov.pagopa.gpd.technicalsupport.client.gpd.GpdPayApiClient;
import it.gov.pagopa.gpd.technicalsupport.config.reconciliation.gpd.GpdPayProperties;
import it.gov.pagopa.gpd.technicalsupport.model.gpd.DebtPositionStatus;
import it.gov.pagopa.gpd.technicalsupport.model.gpd.PaymentOptionStatus;
import it.gov.pagopa.gpd.technicalsupport.model.gpd.ServiceType;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.apd.ReconciliationCandidate;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.biz.BizPositiveEvent;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.gpd.GpdPaidPaymentOptionResponse;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.gpd.GpdPayPaymentOptionRequest;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.gpd.GpdPayRecoveryResult;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class FeignGpdPayClientTest {

  private final GpdPayApiClient gpdPayApiClient = mock(GpdPayApiClient.class);

  private final GpdPayRequestMapper requestMapper = new GpdPayRequestMapper();

  private final GpdPayProperties properties = gpdPayProperties();

  private final FeignGpdPayClient client =
      new FeignGpdPayClient(gpdPayApiClient, properties, requestMapper);

  @Test
  void executePayRecovery_shouldCallGpdPayApiAndReturnSuccess() {
    ReconciliationCandidate candidate = candidate();
    BizPositiveEvent event = bizPositiveEvent(candidate);

    when(gpdPayApiClient.payPaymentOption(
            Mockito.eq("test-api-key"),
            Mockito.eq(candidate.ec()),
            Mockito.eq(candidate.nav()),
            Mockito.any(GpdPayPaymentOptionRequest.class)))
        .thenReturn(
            new GpdPaidPaymentOptionResponse(
                candidate.nav(),
                candidate.iuv(),
                "100",
                "PO_PAID",
                candidate.serviceType().name()));

    GpdPayRecoveryResult result = client.executePayRecovery(candidate, event);

    assertThat(result.succeeded()).isTrue();
    assertThat(result.errorCode()).isNull();
    assertThat(result.errorMessage()).isNull();

    ArgumentCaptor<GpdPayPaymentOptionRequest> requestCaptor =
        ArgumentCaptor.forClass(GpdPayPaymentOptionRequest.class);

    verify(gpdPayApiClient)
        .payPaymentOption(
            Mockito.eq("test-api-key"),
            Mockito.eq("77777777777"),
            Mockito.eq("302131563536065220"),
            requestCaptor.capture());

    GpdPayPaymentOptionRequest request = requestCaptor.getValue();

    assertThat(request.paymentDate()).isEqualTo("2026-06-10T14:50:20.524566");
    assertThat(request.paymentMethod()).isEqualTo("other");
    assertThat(request.pspCode()).isEqualTo("ABI03034");
    assertThat(request.pspTaxCode()).isEqualTo("91010030400");
    assertThat(request.pspCompany()).isEqualTo("Banca Agricola Commerciale SpA");
    assertThat(request.idReceipt()).isEqualTo("receipt-id");
    assertThat(request.fee()).isEqualTo("0");
  }

  @Test
  void executePayRecovery_shouldReturnFailedWhenGpdPayApiThrowsException() {
    ReconciliationCandidate candidate = candidate();
    BizPositiveEvent event = bizPositiveEvent(candidate);

    when(gpdPayApiClient.payPaymentOption(
            Mockito.eq("test-api-key"),
            Mockito.eq(candidate.ec()),
            Mockito.eq(candidate.nav()),
            Mockito.any(GpdPayPaymentOptionRequest.class)))
        .thenThrow(new RuntimeException("PAY recovery failed"));

    GpdPayRecoveryResult result = client.executePayRecovery(candidate, event);

    assertThat(result.succeeded()).isFalse();
    assertThat(result.errorCode()).isEqualTo("RuntimeException");
    assertThat(result.errorMessage()).isEqualTo("PAY recovery failed");

    verify(gpdPayApiClient)
        .payPaymentOption(
            Mockito.eq("test-api-key"),
            Mockito.eq("77777777777"),
            Mockito.eq("302131563536065220"),
            Mockito.any(GpdPayPaymentOptionRequest.class));
  }

  private GpdPayProperties gpdPayProperties() {
    GpdPayProperties prop = new GpdPayProperties();
    prop.setBaseUrl("http://localhost");
    prop.setApiKey("test-api-key");
    return prop;
  }

  private ReconciliationCandidate candidate() {
    return new ReconciliationCandidate(
        LocalDate.of(2026, 5, 20),
        ServiceType.GPD,
        "payment-position-id",
        "payment-option-id",
        "77777777777",
        "302131563536065220",
        "02131563536065220",
        DebtPositionStatus.VALID,
        PaymentOptionStatus.PO_UNPAID,
        "payment-plan-id");
  }

  private BizPositiveEvent bizPositiveEvent(ReconciliationCandidate candidate) {
    return new BizPositiveEvent(
        "biz-event-id",
        "receipt-id",
        candidate.ec(),
        candidate.nav(),
        candidate.iuv(),
        "iur",
        "2026-06-10T14:50:20.524566",
        "other",
        "0.0",
        1781168213044L,
        "DONE",
        "NDP004UAT",
        "ABI03034",
        "91010030400",
        "Banca Agricola Commerciale SpA",
        "97249640588",
        "97249640588_01");
  }
}