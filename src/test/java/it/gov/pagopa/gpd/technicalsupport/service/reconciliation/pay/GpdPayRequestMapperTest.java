package it.gov.pagopa.gpd.technicalsupport.service.reconciliation.pay;

import static org.assertj.core.api.Assertions.assertThat;

import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.biz.BizPositiveEvent;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.gpd.GpdPayPaymentOptionRequest;
import org.junit.jupiter.api.Test;

class GpdPayRequestMapperTest {

  private final GpdPayRequestMapper mapper = new GpdPayRequestMapper();

  @Test
  void toPayRequest_shouldMapBizEventFields() {
    BizPositiveEvent event =
        new BizPositiveEvent(
            "biz-event-id",
            "receipt-id",
            "77777777777",
            "302131563536065220",
            "02131563536065220",
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

    GpdPayPaymentOptionRequest request = mapper.toPayRequest(event);

    assertThat(request.paymentDate()).isEqualTo("2026-06-10T14:50:20.524566");
    assertThat(request.paymentMethod()).isEqualTo("other");
    assertThat(request.pspCode()).isEqualTo("ABI03034");
    assertThat(request.pspTaxCode()).isEqualTo("91010030400");
    assertThat(request.pspCompany()).isEqualTo("Banca Agricola Commerciale SpA");
    assertThat(request.idReceipt()).isEqualTo("receipt-id");
    assertThat(request.fee()).isEqualTo("0.0");
  }

  @Test
  void toPayRequest_shouldApplyDefaultsWhenOptionalFieldsAreBlank() {
    BizPositiveEvent event =
        new BizPositiveEvent(
            "biz-event-id",
            "receipt-id",
            "77777777777",
            "302131563536065220",
            "02131563536065220",
            "iur",
            "2026-06-10T14:50:20.524566",
            " ",
            null,
            1781168213044L,
            "DONE",
            "NDP004UAT",
            null,
            null,
            " ",
            null,
            null);

    GpdPayPaymentOptionRequest request = mapper.toPayRequest(event);

    assertThat(request.paymentDate()).isEqualTo("2026-06-10T14:50:20.524566");
    assertThat(request.paymentMethod()).isEqualTo("other");
    assertThat(request.pspCode()).isNull();
    assertThat(request.pspTaxCode()).isNull();
    assertThat(request.pspCompany()).isEqualTo("UNKNOWN");
    assertThat(request.idReceipt()).isEqualTo("receipt-id");
    assertThat(request.fee()).isEqualTo("0");
  }
}