package it.gov.pagopa.gpd.technicalsupport.service.reconciliation;

import it.gov.pagopa.gpd.technicalsupport.model.gpd.ServiceType;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.ReconciliationRunCreationResult;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.ReconciliationRunStatus;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class InMemoryReconciliationRunStore implements ReconciliationRunStore {

  private final ReconciliationKeyBuilder keyBuilder;
  private final Clock clock;

  private final Map<String, ReconciliationRunCreationResult> runs = new ConcurrentHashMap<>();

  @Override
  public ReconciliationRunCreationResult createOrEvaluateRun(
      LocalDate day,
      List<ServiceType> serviceTypes,
      boolean force) {

    String logicalRunKey = keyBuilder.logicalRunKey(day, serviceTypes);

    ReconciliationRunCreationResult existingRun = runs.get(logicalRunKey);

    if (existingRun != null && existingRun.status() == ReconciliationRunStatus.DONE && !force) {
      return new ReconciliationRunCreationResult(
          day,
          serviceTypes,
          logicalRunKey,
          existingRun.executionId(),
          ReconciliationRunStatus.SKIPPED,
          false);
    }

    String executionId =
        keyBuilder.executionId(
            logicalRunKey, OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC));

    ReconciliationRunCreationResult result =
        new ReconciliationRunCreationResult(
            day,
            serviceTypes,
            logicalRunKey,
            executionId,
            ReconciliationRunStatus.CREATED,
            true);

    runs.put(logicalRunKey, result);
    return result;
  }
}