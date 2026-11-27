package it.gov.pagopa.gpd.technicalsupport.service.reconciliation;

import it.gov.pagopa.gpd.technicalsupport.config.reconciliation.cosmos.ReconciliationCosmosProperties;
import it.gov.pagopa.gpd.technicalsupport.model.gpd.ServiceType;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.ReconciliationDailyRunSnapshot;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.ReconciliationRangeDayCounters;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.ReconciliationRangeSnapshot;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.ReconciliationRunStatus;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.cosmos.ReconciliationRangeDayDocument;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.cosmos.ReconciliationRangeDayDocument.DailyRunReference;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.cosmos.ReconciliationRangeDocument;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.cosmos.ReconciliationRunDocument;
import it.gov.pagopa.gpd.technicalsupport.service.reconciliation.store.cosmos.CosmosReconciliationRangeCheckpointStore;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "reconciliation.cosmos", name = "enabled", havingValue = "true")
public class ReconciliationRangeCheckpoint {
  private static final String COMPLETED = "COMPLETED";
  private final ReconciliationRangeLease lease;
  private final CosmosReconciliationRangeCheckpointStore checkpointStore;
  private final ReconciliationCosmosProperties properties;
  private final Clock clock;

  public ReconciliationRangeSnapshot completeDay(
      ReconciliationRangeSnapshot current, UUID ownerId, ReconciliationDailyRunSnapshot daily) {
    lease.assertOwned(current, ownerId);
    var document = current.document();
    LocalDate day = currentDay(document);
    if (daily == null) {
      throw new IllegalArgumentException("Missing daily run snapshot");
    }
    var run = daily.document();
    String logicalKey = day + "__" + document.serviceTypesKey();
    var dailyCompletedAt =
        validateRun(run, day, logicalKey, document.serviceTypesKey());
    String intentId = observedIntent(document.currentDispatch(), day, logicalKey, run.executionId());
    var counters = ReconciliationRangeDayCounters.from(run);
    String now = clock.instant().toString();
    var checkpoint = ReconciliationRangeDayDocument.builder()
        .id("day__" + day).documentType("DAY").schemaVersion(1)
        .executionId(document.executionId()).day(day.toString())
        .serviceTypesKey(document.serviceTypesKey()).status(COMPLETED)
        .dailyRun(new DailyRunReference(properties.getRunsContainerName(), day.toString(),
            logicalKey, run.executionId(), daily.etag()))
        .dailyCounters(counters)
        .dispatchIntentId(intentId)
        .dailyCompletedAt(
            dailyCompletedAt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
        .createdAt(now).updatedAt(now).completedAt(now).build();
    var updated = advance(document, day, now);
    lease.assertOwned(current, ownerId);
    return checkpointStore.commit(current, checkpoint, updated);
  }

  private LocalDate currentDay(ReconciliationRangeDocument document) {
    LocalDate start = LocalDate.parse(document.from());
    LocalDate end = LocalDate.parse(document.to());
    long total = ChronoUnit.DAYS.between(start, end) + 1;
    int completed = document.completedDays();
    if (completed < 0 || completed >= total || document.totalDays() != total
        || document.remainingDays() != total - completed) {
      throw new IllegalArgumentException("Inconsistent range progress");
    }
    LocalDate expected = start.plusDays(completed);
    if (!expected.toString().equals(document.currentDay())) {
      throw new IllegalArgumentException("Current day does not match range progress");
    }
    validateServices(document);
    return expected;
  }

  private void validateServices(ReconciliationRangeDocument document) {
    var services = document.serviceTypes();
    if (services == null || services.isEmpty() || services.stream().anyMatch(Objects::isNull)) {
      throw new IllegalArgumentException("Missing range services");
    }
    String key = services.stream().map(ServiceType::valueOf).map(Enum::name)
        .distinct().sorted().collect(Collectors.joining("|"));
    if (!key.equals(document.serviceTypesKey())) {
      throw new IllegalArgumentException("Inconsistent service types key");
    }
  }

  private OffsetDateTime validateRun(
      ReconciliationRunDocument run, LocalDate day, String logicalKey, String servicesKey) {
    boolean matching = logicalKey.equals(run.id()) && logicalKey.equals(run.logicalRunKey())
        && day.toString().equals(run.day()) && servicesKey.equals(run.serviceTypesKey());
    if (!matching || run.status() != ReconciliationRunStatus.DONE || !nonBlank(run.executionId())) {
      throw new IllegalArgumentException("Expected the DONE daily run for the current day and services");
    }
    if (!nonBlank(run.completedAt())) {
      throw new IllegalArgumentException("Missing daily completion timestamp");
    }
    return OffsetDateTime.parse(run.completedAt());
  }

  private String observedIntent(
      Map<String, Object> intent, LocalDate day, String logicalKey, String dailyExecutionId) {
    if (intent == null) {
      throw new IllegalArgumentException("Missing persisted dispatch intent");
    }
    boolean matching = "OBSERVED".equals(intent.get("state"))
        && day.toString().equals(intent.get("day"))
        && logicalKey.equals(intent.get("logicalRunKey"))
        && dailyExecutionId.equals(intent.get("dailyExecutionId"));
    if (!matching || !nonBlank(intent.get("intentId"))) {
      throw new IllegalArgumentException("Daily run does not match the observed dispatch intent");
    }
    return (String) intent.get("intentId");
  }

  private boolean nonBlank(Object value) {
    return value instanceof String text && !text.isBlank();
  }

  private ReconciliationRangeDocument advance(
      ReconciliationRangeDocument document, LocalDate day, String now) {
    int completed = document.completedDays() + 1;
    boolean last = completed == document.totalDays();
    var builder = document.toBuilder().completedDays(completed)
        .remainingDays(document.totalDays() - completed).updatedAt(now).currentDispatch(null);
    if (last) {
      builder.status(COMPLETED).completedAt(now).currentDay(null).ownerId(null).leaseExpiresAt(null);
    } else {
      builder.currentDay(day.plusDays(1).toString());
    }
    return builder.build();
  }
}
