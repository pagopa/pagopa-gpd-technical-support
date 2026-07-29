package it.gov.pagopa.gpd.technicalsupport.service.reconciliation.mapper;

import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.ReconciliationOutcome;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.ReconciliationStatus;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.apd.ReconciliationCandidate;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.biz.BizPositiveEvent;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.cosmos.ReconciliationReportDocument;
import it.gov.pagopa.gpd.technicalsupport.service.reconciliation.key.ReconciliationReportKeyBuilder;

import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReconciliationReportDocumentMapper {

  private final ReconciliationReportKeyBuilder keyBuilder;

  public ReconciliationReportDocument recoveredReport(
      String executionId,
      String logicalRunKey,
      ReconciliationCandidate candidate,
      BizPositiveEvent event,
      OffsetDateTime now) {

    int bucket = keyBuilder.bucket(candidate.ec(), candidate.nav());

    return ReconciliationReportDocument.builder()
        .id(keyBuilder.reportId(executionId, candidate.paymentOptionId()))
        .pk(keyBuilder.partitionKey(candidate.day(), candidate.serviceType(), bucket))
        .executionId(executionId)
        .logicalRunKey(logicalRunKey)
        .day(candidate.day().toString())
        .serviceType(candidate.serviceType())
        .bucket(bucket)
        .paymentPositionId(candidate.paymentPositionId())
        .paymentOptionId(candidate.paymentOptionId())
        .ec(candidate.ec())
        .nav(candidate.nav())
        .ecNavKey(keyBuilder.ecNavKey(candidate.ec(), candidate.nav()))
        .iuv(candidate.iuv())
        .bizId(event.eventId())
        .ppStatus(candidate.ppStatus())
        .poStatus(candidate.poStatus())
        .reconciliationStatus(ReconciliationStatus.RECOVERED)
        .outcome(ReconciliationOutcome.POSITIVE_EVENT_FOUND_PAY_EXECUTED)
        .payInvoked(true)
        .paySucceeded(true)
        .errorCode(null)
        .errorMessage(null)
        .createdAt(toIsoString(now))
        .updatedAt(toIsoString(now))
        .build();
  }

  public ReconciliationReportDocument technicalFailureReport(
      String executionId,
      String logicalRunKey,
      ReconciliationCandidate candidate,
      String errorCode,
      String errorMessage,
      OffsetDateTime now) {

    int bucket = keyBuilder.bucket(candidate.ec(), candidate.nav());

    return ReconciliationReportDocument.builder()
        .id(keyBuilder.reportId(executionId, candidate.paymentOptionId()))
        .pk(keyBuilder.partitionKey(candidate.day(), candidate.serviceType(), bucket))
        .executionId(executionId)
        .logicalRunKey(logicalRunKey)
        .day(candidate.day().toString())
        .serviceType(candidate.serviceType())
        .bucket(bucket)
        .paymentPositionId(candidate.paymentPositionId())
        .paymentOptionId(candidate.paymentOptionId())
        .ec(candidate.ec())
        .nav(candidate.nav())
        .ecNavKey(keyBuilder.ecNavKey(candidate.ec(), candidate.nav()))
        .iuv(candidate.iuv())
        .bizId(null)
        .ppStatus(candidate.ppStatus())
        .poStatus(candidate.poStatus())
        .reconciliationStatus(ReconciliationStatus.TECHNICAL_FAILURE)
        .outcome(ReconciliationOutcome.BIZ_LOOKUP_FAILED)
        .payInvoked(false)
        .paySucceeded(false)
        .errorCode(errorCode)
        .errorMessage(errorMessage)
        .createdAt(toIsoString(now))
        .updatedAt(toIsoString(now))
        .build();
  }

  public ReconciliationReportDocument manualRequiredReport(
      String executionId,
      String logicalRunKey,
      ReconciliationCandidate candidate,
      BizPositiveEvent event,
      ReconciliationOutcome outcome,
      OffsetDateTime now) {

    int bucket = keyBuilder.bucket(candidate.ec(), candidate.nav());

    return ReconciliationReportDocument.builder()
        .id(keyBuilder.reportId(executionId, candidate.paymentOptionId()))
        .pk(keyBuilder.partitionKey(candidate.day(), candidate.serviceType(), bucket))
        .executionId(executionId)
        .logicalRunKey(logicalRunKey)
        .day(candidate.day().toString())
        .serviceType(candidate.serviceType())
        .bucket(bucket)
        .paymentPositionId(candidate.paymentPositionId())
        .paymentOptionId(candidate.paymentOptionId())
        .ec(candidate.ec())
        .nav(candidate.nav())
        .ecNavKey(keyBuilder.ecNavKey(candidate.ec(), candidate.nav()))
        .iuv(candidate.iuv())
        .bizId(event == null ? null : event.eventId())
        .ppStatus(candidate.ppStatus())
        .poStatus(candidate.poStatus())
        .reconciliationStatus(ReconciliationStatus.MANUAL_REQUIRED)
        .outcome(outcome)
        .payInvoked(false)
        .paySucceeded(false)
        .errorCode(null)
        .errorMessage(null)
        .createdAt(toIsoString(now))
        .updatedAt(toIsoString(now))
        .build();
  }

  public ReconciliationReportDocument payFailedReport(
      String executionId,
      String logicalRunKey,
      ReconciliationCandidate candidate,
      BizPositiveEvent event,
      String errorCode,
      String errorMessage,
      OffsetDateTime now) {

    int bucket = keyBuilder.bucket(candidate.ec(), candidate.nav());

    return ReconciliationReportDocument.builder()
        .id(keyBuilder.reportId(executionId, candidate.paymentOptionId()))
        .pk(keyBuilder.partitionKey(candidate.day(), candidate.serviceType(), bucket))
        .executionId(executionId)
        .logicalRunKey(logicalRunKey)
        .day(candidate.day().toString())
        .serviceType(candidate.serviceType())
        .bucket(bucket)
        .paymentPositionId(candidate.paymentPositionId())
        .paymentOptionId(candidate.paymentOptionId())
        .ec(candidate.ec())
        .nav(candidate.nav())
        .ecNavKey(keyBuilder.ecNavKey(candidate.ec(), candidate.nav()))
        .iuv(candidate.iuv())
        .bizId(event == null ? null : event.eventId())
        .ppStatus(candidate.ppStatus())
        .poStatus(candidate.poStatus())
        .reconciliationStatus(ReconciliationStatus.TECHNICAL_FAILURE)
        .outcome(ReconciliationOutcome.POSITIVE_EVENT_FOUND_PAY_FAILED)
        .payInvoked(true)
        .paySucceeded(false)
        .errorCode(errorCode)
        .errorMessage(errorMessage)
        .createdAt(toIsoString(now))
        .updatedAt(toIsoString(now))
        .build();
  }

  private String toIsoString(OffsetDateTime dateTime) {
    return dateTime == null ? null : dateTime.toString();
  }
}
