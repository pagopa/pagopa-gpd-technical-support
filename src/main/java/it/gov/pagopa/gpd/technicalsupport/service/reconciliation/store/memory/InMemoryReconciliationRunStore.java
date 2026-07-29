package it.gov.pagopa.gpd.technicalsupport.service.reconciliation.store.memory;

import it.gov.pagopa.gpd.technicalsupport.model.gpd.ServiceType;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.ReconciliationRunCreationResult;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.ReconciliationRunStatus;
import it.gov.pagopa.gpd.technicalsupport.service.reconciliation.ReconciliationCounters;
import it.gov.pagopa.gpd.technicalsupport.service.reconciliation.key.ReconciliationKeyBuilder;
import it.gov.pagopa.gpd.technicalsupport.service.reconciliation.store.ReconciliationRunStore;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(
	    prefix = "reconciliation.cosmos",
	    name = "enabled",
	    havingValue = "false",
	    matchIfMissing = true)
public class InMemoryReconciliationRunStore implements ReconciliationRunStore {

  private final ReconciliationKeyBuilder keyBuilder;
  private final Clock clock;

  private final Map<String, StoredRun> runs = new ConcurrentHashMap<>();

  @Override
  public ReconciliationRunCreationResult createOrEvaluateRun(
      LocalDate day,
      List<ServiceType> serviceTypes,
      boolean force) {

    String logicalRunKey = keyBuilder.logicalRunKey(day, serviceTypes);
    StoredRun existingRun = runs.get(logicalRunKey);

    if (existingRun != null && existingRun.status() == ReconciliationRunStatus.RUNNING && !force) {
      return toCreationResult(existingRun, false);
    }

    if (existingRun != null && existingRun.status() == ReconciliationRunStatus.DONE && !force) {
      return new ReconciliationRunCreationResult(
          existingRun.day(),
          existingRun.serviceTypes(),
          existingRun.logicalRunKey(),
          existingRun.executionId(),
          ReconciliationRunStatus.SKIPPED,
          false);
    }

    String executionId =
        keyBuilder.executionId(
            logicalRunKey, OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC));

    StoredRun newRun =
        new StoredRun(
            day,
            List.copyOf(serviceTypes),
            logicalRunKey,
            executionId,
            ReconciliationRunStatus.CREATED,
            ReconciliationCounters.empty(),
            null);

    runs.put(logicalRunKey, newRun);

    return toCreationResult(newRun, true);
  }

  @Override
  public void markRunning(String logicalRunKey, String executionId) {
    runs.computeIfPresent(
        logicalRunKey,
        (key, existingRun) -> {
          if (!existingRun.executionId().equals(executionId)) {
            return existingRun;
          }

          return existingRun.withStatus(ReconciliationRunStatus.RUNNING);
        });
  }

  @Override
  public void markDone(
      String logicalRunKey, String executionId, ReconciliationCounters counters) {

    runs.computeIfPresent(
        logicalRunKey,
        (key, existingRun) -> {
          if (!existingRun.executionId().equals(executionId)) {
            return existingRun;
          }

          return existingRun
              .withStatus(ReconciliationRunStatus.DONE)
              .withCounters(counters)
              .withErrorMessage(null);
        });
  }

  @Override
  public void markFailed(
      String logicalRunKey,
      String executionId,
      ReconciliationCounters counters,
      Throwable error) {

    runs.computeIfPresent(
        logicalRunKey,
        (key, existingRun) -> {
          if (!existingRun.executionId().equals(executionId)) {
            return existingRun;
          }

          String errorMessage = error == null ? null : error.getMessage();

          return existingRun
              .withStatus(ReconciliationRunStatus.FAILED)
              .withCounters(counters)
              .withErrorMessage(errorMessage);
        });
  }

  private ReconciliationRunCreationResult toCreationResult(
      StoredRun storedRun, boolean shouldStart) {

    return new ReconciliationRunCreationResult(
        storedRun.day(),
        storedRun.serviceTypes(),
        storedRun.logicalRunKey(),
        storedRun.executionId(),
        storedRun.status(),
        shouldStart);
  }

  private record StoredRun(
      LocalDate day,
      List<ServiceType> serviceTypes,
      String logicalRunKey,
      String executionId,
      ReconciliationRunStatus status,
      ReconciliationCounters counters,
      String errorMessage) {

    StoredRun withStatus(ReconciliationRunStatus newStatus) {
      return new StoredRun(
          day, serviceTypes, logicalRunKey, executionId, newStatus, counters, errorMessage);
    }

    StoredRun withCounters(ReconciliationCounters newCounters) {
      return new StoredRun(
          day, serviceTypes, logicalRunKey, executionId, status, newCounters, errorMessage);
    }

    StoredRun withErrorMessage(String newErrorMessage) {
      return new StoredRun(
          day, serviceTypes, logicalRunKey, executionId, status, counters, newErrorMessage);
    }
  }
}