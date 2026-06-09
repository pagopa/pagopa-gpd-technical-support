package it.gov.pagopa.gpd.technicalsupport.service.reconciliation;

import it.gov.pagopa.gpd.technicalsupport.model.gpd.ServiceType;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.ReconciliationRunStatus;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.cosmos.ReconciliationRunDocument;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReconciliationRunDocumentMapper {

  private final ReconciliationKeyBuilder keyBuilder;

  public ReconciliationRunDocument newCreatedRun(
      LocalDate day,
      List<ServiceType> serviceTypes,
      String executionId,
      OffsetDateTime now) {

    String serviceTypesKey = keyBuilder.serviceTypesKey(serviceTypes);
    String logicalRunKey = keyBuilder.logicalRunKey(day, serviceTypes);

    return ReconciliationRunDocument.builder()
        .id(logicalRunKey)
        .day(day.toString())
        .serviceTypes(List.copyOf(serviceTypes))
        .serviceTypesKey(serviceTypesKey)
        .logicalRunKey(logicalRunKey)
        .executionId(executionId)
        .status(ReconciliationRunStatus.CREATED)
        .scanned(0)
        .positiveEventsFound(0)
        .reconciliationCases(0)
        .recovered(0)
        .notRecovered(0)
        .manualRequired(0)
        .technicalFailures(0)
        .payExecuted(0)
        .payFailed(0)
        .createdAt(now)
        .updatedAt(now)
        .build();
  }

  public ReconciliationRunDocument running(ReconciliationRunDocument document, OffsetDateTime now) {
    return document.toBuilder()
        .status(ReconciliationRunStatus.RUNNING)
        .startedAt(now)
        .updatedAt(now)
        .build();
  }

  public ReconciliationRunDocument done(
      ReconciliationRunDocument document, ReconciliationCounters counters, OffsetDateTime now) {
    return document.toBuilder()
        .status(ReconciliationRunStatus.DONE)
        .scanned(counters.scanned())
        .positiveEventsFound(counters.positiveEventsFound())
        .reconciliationCases(counters.reconciliationCases())
        .recovered(counters.recovered())
        .notRecovered(counters.notRecovered())
        .manualRequired(counters.manualRequired())
        .technicalFailures(counters.technicalFailures())
        .payExecuted(counters.payExecuted())
        .payFailed(counters.payFailed())
        .errorCode(null)
        .errorMessage(null)
        .completedAt(now)
        .updatedAt(now)
        .build();
  }

  public ReconciliationRunDocument failed(
      ReconciliationRunDocument document,
      ReconciliationCounters counters,
      Throwable error,
      OffsetDateTime now) {

    return document.toBuilder()
        .status(ReconciliationRunStatus.FAILED)
        .scanned(counters.scanned())
        .positiveEventsFound(counters.positiveEventsFound())
        .reconciliationCases(counters.reconciliationCases())
        .recovered(counters.recovered())
        .notRecovered(counters.notRecovered())
        .manualRequired(counters.manualRequired())
        .technicalFailures(counters.technicalFailures())
        .payExecuted(counters.payExecuted())
        .payFailed(counters.payFailed())
        .errorCode(error == null ? null : error.getClass().getSimpleName())
        .errorMessage(error == null ? null : error.getMessage())
        .completedAt(now)
        .updatedAt(now)
        .build();
  }
}