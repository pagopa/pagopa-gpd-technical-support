package it.gov.pagopa.gpd.technicalsupport.service.reconciliation.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import it.gov.pagopa.gpd.technicalsupport.config.reconciliation.ReconciliationProperties;
import it.gov.pagopa.gpd.technicalsupport.model.gpd.DebtPositionStatus;
import it.gov.pagopa.gpd.technicalsupport.model.gpd.PaymentOptionStatus;
import it.gov.pagopa.gpd.technicalsupport.model.gpd.ServiceType;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.ReconciliationOutcome;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.ReconciliationStatus;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.apd.ReconciliationCandidate;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.biz.BizPositiveEvent;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.cosmos.ReconciliationReportDocument;
import it.gov.pagopa.gpd.technicalsupport.service.reconciliation.key.ReconciliationReportKeyBuilder;

import java.time.LocalDate;
import java.time.Month;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class ReconciliationReportDocumentMapperTest {

  private final ReconciliationProperties properties = new ReconciliationProperties();

  private final ReconciliationReportDocumentMapper mapper =
      new ReconciliationReportDocumentMapper(new ReconciliationReportKeyBuilder(properties));

  @Test
  void recoveredReport_shouldCreateReportWithExpectedKeys() {
    OffsetDateTime now = OffsetDateTime.of(2026, 5, 26, 10, 0, 0, 0, ZoneOffset.UTC);

    ReconciliationCandidate candidate = candidate();
    BizPositiveEvent event = bizPositiveEvent(candidate);

    ReconciliationReportDocument report =
        mapper.recoveredReport(
            "2026-05-20__GPD|WISP__20260526T100000Z",
            "2026-05-20__GPD|WISP",
            candidate,
            event,
            now);

    assertThat(report.id())
        .isEqualTo("2026-05-20__GPD|WISP__20260526T100000Z__payment-option-id");
    assertThat(report.pk()).startsWith("2026-05-20__GPD__");
    assertThat(report.executionId()).isEqualTo("2026-05-20__GPD|WISP__20260526T100000Z");
    assertThat(report.logicalRunKey()).isEqualTo("2026-05-20__GPD|WISP");
    assertThat(report.day()).isEqualTo("2026-05-20");
    assertThat(report.serviceType()).isEqualTo(ServiceType.GPD);
    assertThat(report.paymentPositionId()).isEqualTo("payment-position-id");
    assertThat(report.paymentOptionId()).isEqualTo("payment-option-id");
    assertThat(report.ec()).isEqualTo("77777777777");
    assertThat(report.nav()).isEqualTo("302131563536065220");
    assertThat(report.iuv()).isEqualTo("02131563536065220");
    assertThat(report.bizId()).isEqualTo("biz-event-id");
    assertThat(report.ppStatus()).isEqualTo(DebtPositionStatus.VALID);
    assertThat(report.poStatus()).isEqualTo(PaymentOptionStatus.PO_UNPAID);
    assertThat(report.reconciliationStatus()).isEqualTo(ReconciliationStatus.RECOVERED);
    assertThat(report.outcome()).isEqualTo(ReconciliationOutcome.POSITIVE_EVENT_FOUND_PAY_EXECUTED);
    assertThat(report.payInvoked()).isTrue();
    assertThat(report.paySucceeded()).isTrue();
    assertThat(report.errorCode()).isNull();
    assertThat(report.errorMessage()).isNull();
    assertThat(report.createdAt()).isEqualTo(now.toString());
    assertThat(report.updatedAt()).isEqualTo(now.toString());
  }

  @Test
  void technicalFailureReport_shouldCreateReportWithFailureDetails() {
    OffsetDateTime now = OffsetDateTime.of(2026, 5, 26, 10, 0, 0, 0, ZoneOffset.UTC);

    ReconciliationCandidate candidate = candidate();

    ReconciliationReportDocument report =
        mapper.technicalFailureReport(
            "2026-05-20__GPD|WISP__20260526T100000Z",
            "2026-05-20__GPD|WISP",
            candidate,
            "CosmosException",
            "Biz lookup failed",
            now);

    assertThat(report.id())
        .isEqualTo("2026-05-20__GPD|WISP__20260526T100000Z__payment-option-id");
    assertThat(report.pk()).startsWith("2026-05-20__GPD__");
    assertThat(report.bizId()).isNull();
    assertThat(report.reconciliationStatus()).isEqualTo(ReconciliationStatus.TECHNICAL_FAILURE);
    assertThat(report.outcome()).isEqualTo(ReconciliationOutcome.BIZ_LOOKUP_FAILED);
    assertThat(report.payInvoked()).isFalse();
    assertThat(report.paySucceeded()).isFalse();
    assertThat(report.errorCode()).isEqualTo("CosmosException");
    assertThat(report.errorMessage()).isEqualTo("Biz lookup failed");
    assertThat(report.createdAt()).isEqualTo(now.toString());
    assertThat(report.updatedAt()).isEqualTo(now.toString());
  }

  @Test
  void manualRequiredReport_shouldCreateManualRequiredReport() {
    OffsetDateTime now = OffsetDateTime.of(2026, 5, 26, 10, 0, 0, 0, ZoneOffset.UTC);

    ReconciliationCandidate candidate = candidate(DebtPositionStatus.EXPIRED);
    BizPositiveEvent event = bizPositiveEvent(candidate);

    ReconciliationReportDocument report =
        mapper.manualRequiredReport(
            "2026-05-20__GPD|WISP__20260526T100000Z",
            "2026-05-20__GPD|WISP",
            candidate,
            event,
            ReconciliationOutcome.POSITIVE_EVENT_FOUND_EXPIRED_MANUAL_REQUIRED,
            now);

    assertThat(report.id())
        .isEqualTo("2026-05-20__GPD|WISP__20260526T100000Z__payment-option-id");
    assertThat(report.pk()).startsWith("2026-05-20__GPD__");
    assertThat(report.bizId()).isEqualTo("biz-event-id");
    assertThat(report.reconciliationStatus()).isEqualTo(ReconciliationStatus.MANUAL_REQUIRED);
    assertThat(report.outcome()).isEqualTo(ReconciliationOutcome.POSITIVE_EVENT_FOUND_EXPIRED_MANUAL_REQUIRED);
    assertThat(report.payInvoked()).isFalse();
    assertThat(report.paySucceeded()).isFalse();
    assertThat(report.errorCode()).isNull();
    assertThat(report.errorMessage()).isNull();
    assertThat(report.createdAt()).isEqualTo(now.toString());
    assertThat(report.updatedAt()).isEqualTo(now.toString());
  }

  @Test
  void payFailedReport_shouldCreateTechnicalFailureReportWithPayFailureOutcome() {
    OffsetDateTime now = OffsetDateTime.of(2026, 5, 26, 10, 0, 0, 0, ZoneOffset.UTC);

    ReconciliationCandidate candidate = candidate();
    BizPositiveEvent event = bizPositiveEvent(candidate);

    ReconciliationReportDocument report =
        mapper.payFailedReport(
            "2026-05-20__GPD|WISP__20260526T100000Z",
            "2026-05-20__GPD|WISP",
            candidate,
            event,
            "PayRecoveryException",
            "PAY recovery failed",
            now);

    assertThat(report.id())
        .isEqualTo("2026-05-20__GPD|WISP__20260526T100000Z__payment-option-id");
    assertThat(report.pk()).startsWith("2026-05-20__GPD__");
    assertThat(report.bizId()).isEqualTo("biz-event-id");
    assertThat(report.reconciliationStatus()).isEqualTo(ReconciliationStatus.TECHNICAL_FAILURE);
    assertThat(report.outcome()).isEqualTo(ReconciliationOutcome.POSITIVE_EVENT_FOUND_PAY_FAILED);
    assertThat(report.payInvoked()).isTrue();
    assertThat(report.paySucceeded()).isFalse();
    assertThat(report.errorCode()).isEqualTo("PayRecoveryException");
    assertThat(report.errorMessage()).isEqualTo("PAY recovery failed");
    assertThat(report.createdAt()).isEqualTo(now.toString());
    assertThat(report.updatedAt()).isEqualTo(now.toString());
  }

  private ReconciliationCandidate candidate() {
    return candidate(DebtPositionStatus.VALID);
  }

  private ReconciliationCandidate candidate(DebtPositionStatus ppStatus) {
    return new ReconciliationCandidate(
        LocalDate.of(2026, Month.MAY, 20),
        ServiceType.GPD,
        "payment-position-id",
        "payment-option-id",
        "77777777777",
        "302131563536065220",
        "02131563536065220",
        ppStatus,
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
        "TEST",
        "ABI03034",
        "91010030400",
        "Banca Agricola Commerciale SpA",
        "97249640588",
        "97249640588_01");
  }
}
