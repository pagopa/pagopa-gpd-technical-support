package it.gov.pagopa.gpd.technicalsupport.service.reconciliation.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.biz.BizPositiveEvent;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.biz.cosmos.BizPositiveEventDocument;
import org.junit.jupiter.api.Test;

class BizPositiveEventMapperTest {

  private final BizPositiveEventMapper mapper = new BizPositiveEventMapper();

  @Test
  void toDomain_shouldMapFullDocument() {
    BizPositiveEventDocument document = fullDocument();

    BizPositiveEvent result = mapper.toDomain(document);

    BizPositiveEvent expected =
        new BizPositiveEvent(
            "biz-event-id",
            "receipt-id",
            "77777777777",
            "302131563536065220",
            "02131563536065220",
            "iur-from-debtor-position",
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

    assertThat(result).isEqualTo(expected);
  }

  @Test
  void toDomain_shouldUsePaymentInfoIurWhenDebtorPositionIurIsNull() {
    BizPositiveEventDocument document =
        new BizPositiveEventDocument(
            "biz-event-id",
            "receipt-id",
            new BizPositiveEventDocument.DebtorPosition(
                "302131563536065220",
                "02131563536065220",
                null),
            new BizPositiveEventDocument.Creditor("77777777777"),
            psp("91010030400", "09999999999"),
            paymentInfo("iur-from-payment-info"),
            1781168213044L,
            new BizPositiveEventDocument.Properties("NDP004UAT"),
            "DONE");

    BizPositiveEvent result = mapper.toDomain(document);

    BizPositiveEvent expected =
        new BizPositiveEvent(
            "biz-event-id",
            "receipt-id",
            "77777777777",
            "302131563536065220",
            "02131563536065220",
            "iur-from-payment-info",
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

    assertThat(result).isEqualTo(expected);
  }

  @Test
  void toDomain_shouldUsePspPartitaIvaWhenPspFiscalCodeIsNull() {
    BizPositiveEventDocument document =
        new BizPositiveEventDocument(
            "biz-event-id",
            "receipt-id",
            new BizPositiveEventDocument.DebtorPosition(
                "302131563536065220",
                "02131563536065220",
                "iur"),
            new BizPositiveEventDocument.Creditor("77777777777"),
            psp(null, "09999999999"),
            paymentInfo("iur"),
            1781168213044L,
            new BizPositiveEventDocument.Properties("NDP004UAT"),
            "DONE");

    BizPositiveEvent result = mapper.toDomain(document);

    BizPositiveEvent expected =
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
            "09999999999",
            "Banca Agricola Commerciale SpA",
            "97249640588",
            "97249640588_01");

    assertThat(result).isEqualTo(expected);
  }

  @Test
  void toDomain_shouldUsePspPartitaIvaWhenPspFiscalCodeIsBlank() {
    BizPositiveEventDocument document =
        new BizPositiveEventDocument(
            "biz-event-id",
            "receipt-id",
            new BizPositiveEventDocument.DebtorPosition(
                "302131563536065220",
                "02131563536065220",
                "iur"),
            new BizPositiveEventDocument.Creditor("77777777777"),
            psp("   ", "09999999999"),
            paymentInfo("iur"),
            1781168213044L,
            new BizPositiveEventDocument.Properties("NDP004UAT"),
            "DONE");

    BizPositiveEvent result = mapper.toDomain(document);

    BizPositiveEvent expected =
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
            "09999999999",
            "Banca Agricola Commerciale SpA",
            "97249640588",
            "97249640588_01");

    assertThat(result).isEqualTo(expected);
  }

  @Test
  void toDomain_shouldHandleNullableNestedObjects() {
    BizPositiveEventDocument document =
        new BizPositiveEventDocument(
            "biz-event-id",
            "receipt-id",
            null,
            null,
            null,
            null,
            1781168213044L,
            null,
            "DONE");

    BizPositiveEvent result = mapper.toDomain(document);

    BizPositiveEvent expected =
        new BizPositiveEvent(
            "biz-event-id",
            "receipt-id",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            1781168213044L,
            "DONE",
            null,
            null,
            null,
            null,
            null,
            null);

    assertThat(result).isEqualTo(expected);
  }

  private BizPositiveEventDocument fullDocument() {
    return new BizPositiveEventDocument(
        "biz-event-id",
        "receipt-id",
        new BizPositiveEventDocument.DebtorPosition(
            "302131563536065220",
            "02131563536065220",
            "iur-from-debtor-position"),
        new BizPositiveEventDocument.Creditor("77777777777"),
        psp("91010030400", "09999999999"),
        paymentInfo("iur-from-payment-info"),
        1781168213044L,
        new BizPositiveEventDocument.Properties("NDP004UAT"),
        "DONE");
  }

  private BizPositiveEventDocument.Psp psp(String pspFiscalCode, String pspPartitaIva) {
    return new BizPositiveEventDocument.Psp(
        "ABI03034",
        "97249640588",
        "97249640588_01",
        "Banca Agricola Commerciale SpA",
        pspPartitaIva,
        pspFiscalCode,
        "NA");
  }

  private BizPositiveEventDocument.PaymentInfo paymentInfo(String iur) {
    return new BizPositiveEventDocument.PaymentInfo(
        "2026-06-10T14:50:20.524566",
        "other",
        "0.0",
        "payment-token",
        iur);
  }
}