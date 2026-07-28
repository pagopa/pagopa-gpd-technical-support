package it.gov.pagopa.gpd.technicalsupport.service.reconciliation.pay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.biz.BizPositiveEvent;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.gpd.GpdPayPaymentOptionRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class GpdPayRequestMapperTest {

  private final GpdPayRequestMapper mapper = new GpdPayRequestMapper();

  @Test
  void toPayRequest_shouldMapBizEventFields() {
    BizPositiveEvent event = eventWithFee("0.0");

    GpdPayPaymentOptionRequest request = mapper.toPayRequest(event);

    assertThat(request.paymentDate()).isEqualTo("2026-06-10T14:50:20.524566");
    assertThat(request.paymentMethod()).isEqualTo("other");
    assertThat(request.pspCode()).isEqualTo("ABI03034");
    assertThat(request.pspTaxCode()).isEqualTo("91010030400");
    assertThat(request.pspCompany()).isEqualTo("Banca Agricola Commerciale SpA");
    assertThat(request.idReceipt()).isEqualTo("receipt-id");
    assertThat(request.fee()).isEqualTo("0");
  }

  @ParameterizedTest
  @CsvSource({
    "'0.0', '0'",
    "'0.00', '0'",
    "'0.2', '20'",
    "'0.20', '20'",
    "'0.45', '45'",
    "'1.0', '100'",
    "'1.00', '100'",
    "'12.34', '1234'",
    "' 0.45 ', '45'",
    "'0', '0'",
    "'1', '1'",
    "'20', '20'",
    "'45', '45'",
    "'100', '100'",
    "' 45 ', '45'"
  })
  void toPayRequest_shouldNormalizeFeeToCents(
      String bizFee,
      String expectedFeeInCents) {

    GpdPayPaymentOptionRequest request =
        mapper.toPayRequest(eventWithFee(bizFee));

    assertThat(request.fee()).isEqualTo(expectedFeeInCents);
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"", " "})
  void toPayRequest_shouldDefaultNullOrBlankFeeToZero(String bizFee) {
    GpdPayPaymentOptionRequest request = mapper.toPayRequest(eventWithFee(bizFee));

    assertThat(request.fee()).isEqualTo("0");
  }

  @ParameterizedTest
  @ValueSource(strings = {"invalid", "0.001", "1.999", "0,45"})
  void toPayRequest_shouldRejectInvalidFee(String bizFee) {
	  BizPositiveEvent event = eventWithFee(bizFee);

	  assertThatThrownBy(() -> mapper.toPayRequest(event))
	      .isInstanceOf(IllegalArgumentException.class)
	      .hasMessageContaining("Invalid Biz fee");
  }

  @ParameterizedTest
  @ValueSource(strings = {"-1", "-0.01"})
  void toPayRequest_shouldRejectNegativeFee(String bizFee) {
	  BizPositiveEvent event = eventWithFee(bizFee);

	  assertThatThrownBy(() -> mapper.toPayRequest(event))
	      .isInstanceOf(IllegalArgumentException.class)
	      .hasMessageContaining("cannot be negative");
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

  private BizPositiveEvent eventWithFee(String fee) {
    return new BizPositiveEvent(
        "biz-event-id",
        "receipt-id",
        "77777777777",
        "302131563536065220",
        "02131563536065220",
        "iur",
        "2026-06-10T14:50:20.524566",
        "other",
        fee,
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