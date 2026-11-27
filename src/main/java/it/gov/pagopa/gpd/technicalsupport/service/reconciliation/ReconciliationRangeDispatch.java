package it.gov.pagopa.gpd.technicalsupport.service.reconciliation;

import com.azure.cosmos.CosmosException;
import it.gov.pagopa.gpd.technicalsupport.exception.ReconciliationRangeDispatchConflictException;
import it.gov.pagopa.gpd.technicalsupport.exception.ReconciliationRangeLeaseLostException;
import it.gov.pagopa.gpd.technicalsupport.model.gpd.ServiceType;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.ReconciliationRangeDispatchPreparation;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.ReconciliationRangeSnapshot;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.ReconciliationRunStatus;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.cosmos.ReconciliationRangeDocument;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.cosmos.ReconciliationRunDocument;
import it.gov.pagopa.gpd.technicalsupport.service.reconciliation.store.cosmos.CosmosReconciliationRangeStore;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/** Persists dispatch intent and observations; does not send requests or advance the range. */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "reconciliation.cosmos", name = "enabled", havingValue = "true")
public class ReconciliationRangeDispatch {
  private static final String PREPARED = "PREPARED";
  private static final String ACKNOWLEDGED = "ACKNOWLEDGED";
  private static final String OBSERVED = "OBSERVED";
  private static final String DAILY_EXECUTION_ID = "dailyExecutionId";
  private static final String UPDATED_AT = "updatedAt";

  private final CosmosReconciliationRangeStore store;
  private final ReconciliationRangeLease lease;
  private final Clock clock;

  public ReconciliationRangeDispatchPreparation prepare(
      ReconciliationRangeSnapshot current, UUID ownerId) {
    lease.assertOwned(current, ownerId);
    var context = currentDay(current.document());
    if (intent(current.document(), context) != null) {
      return new ReconciliationRangeDispatchPreparation(current, false);
    }
    String now = clock.instant().toString();
    Map<String, Object> prepared = new LinkedHashMap<>();
    prepared.put("intentId", UUID.randomUUID().toString());
    prepared.put("day", context.day());
    prepared.put("logicalRunKey", context.logicalRunKey());
    prepared.put("state", PREPARED);
    prepared.put(DAILY_EXECUTION_ID, null);
    prepared.put("createdAt", now);
    prepared.put(UPDATED_AT, now);
    return new ReconciliationRangeDispatchPreparation(write(current, ownerId, prepared), true);
  }

  public ReconciliationRangeSnapshot acknowledge(
      ReconciliationRangeSnapshot current, UUID ownerId, String dailyExecutionId) {
    lease.assertOwned(current, ownerId);
    var context = currentDay(current.document());
    var previous = requireIntent(current.document(), context);
    assertExecutionId(previous, dailyExecutionId);
    if (!PREPARED.equals(previous.get("state"))) {
      return current;
    }
    return write(current, ownerId, transition(previous, ACKNOWLEDGED, dailyExecutionId));
  }

  public ReconciliationRangeSnapshot observe(
      ReconciliationRangeSnapshot current, UUID ownerId, ReconciliationRunDocument dailyRun) {
    lease.assertOwned(current, ownerId);
    var context = currentDay(current.document());
    var previous = requireIntent(current.document(), context);
    assertDailyRun(dailyRun, context, current.document().serviceTypesKey());
    assertExecutionId(previous, dailyRun.executionId());
    if (OBSERVED.equals(previous.get("state"))) {
      return current;
    }
    return write(current, ownerId, transition(previous, OBSERVED, dailyRun.executionId()));
  }

  private Map<String, Object> transition(
      Map<String, Object> previous, String state, String executionId) {
    Map<String, Object> updated = new LinkedHashMap<>(previous);
    updated.put("state", state);
    updated.put(DAILY_EXECUTION_ID, executionId);
    updated.put(UPDATED_AT, clock.instant().toString());
    return updated;
  }

  private DayContext currentDay(ReconciliationRangeDocument document) {
    LocalDate start = parseDay(document.from());
    LocalDate end = parseDay(document.to());
    LocalDate day = parseDay(document.currentDay());
    long total = ChronoUnit.DAYS.between(start, end) + 1;
    int completed = document.completedDays();
    if (completed < 0 || completed >= total || document.totalDays() != total
        || document.remainingDays() != total - completed) {
      throw new ReconciliationRangeDispatchConflictException("Inconsistent range progress");
    }
    if (!day.equals(start.plusDays(completed)) || !day.toString().equals(document.currentDay())) {
      throw new ReconciliationRangeDispatchConflictException("Current day does not match progress");
    }
    String servicesKey = canonicalServices(document);
    return new DayContext(day.toString(), day + "__" + servicesKey);
  }

  private LocalDate parseDay(String value) {
    if (!nonBlank(value)) {
      throw new ReconciliationRangeDispatchConflictException("Missing range date");
    }
    try {
      return LocalDate.parse(value);
    } catch (DateTimeParseException error) {
      throw new ReconciliationRangeDispatchConflictException("Invalid range date", error);
    }
  }

  private String canonicalServices(ReconciliationRangeDocument document) {
    var services = document.serviceTypes();
    if (services == null || services.isEmpty() || services.stream().anyMatch(Objects::isNull)) {
      throw new ReconciliationRangeDispatchConflictException("Missing range service types");
    }
    String key = services.stream().map(this::canonicalService)
        .distinct().sorted().collect(Collectors.joining("|"));
    if (!key.equals(document.serviceTypesKey())) {
      throw new ReconciliationRangeDispatchConflictException("Inconsistent service types key");
    }
    return key;
  }

  private String canonicalService(String value) {
    try {
      return ServiceType.valueOf(value).name();
    } catch (IllegalArgumentException error) {
      throw new ReconciliationRangeDispatchConflictException("Unknown range service type", error);
    }
  }

  private Map<String, Object> requireIntent(
      ReconciliationRangeDocument document, DayContext context) {
    var existing = intent(document, context);
    if (existing == null) {
      throw new ReconciliationRangeDispatchConflictException("Persist dispatch intent first");
    }
    return existing;
  }

  private Map<String, Object> intent(ReconciliationRangeDocument document, DayContext context) {
    var existing = document.currentDispatch();
    if (existing == null) {
      return null;
    }
    if (!nonBlank(existing.get("intentId")) || !context.day().equals(existing.get("day"))
        || !context.logicalRunKey().equals(existing.get("logicalRunKey"))) {
      throw new ReconciliationRangeDispatchConflictException("Invalid persisted dispatch identity");
    }
    Object state = existing.get("state");
    Object reference = existing.get(DAILY_EXECUTION_ID);
    boolean prepared = PREPARED.equals(state) && reference == null;
    boolean referenced = (ACKNOWLEDGED.equals(state) || OBSERVED.equals(state)) && nonBlank(reference);
    if (!existing.containsKey(DAILY_EXECUTION_ID) || !(prepared || referenced)) {
      throw new ReconciliationRangeDispatchConflictException("Inconsistent persisted dispatch state");
    }
    return existing;
  }

  private boolean nonBlank(Object value) {
    return value instanceof String text && !text.isBlank();
  }

  private void assertExecutionId(Map<String, Object> intent, String executionId) {
    if (!nonBlank(executionId)) {
      throw new ReconciliationRangeDispatchConflictException("Missing daily executionId");
    }
    Object previous = intent.get(DAILY_EXECUTION_ID);
    if (previous != null && !previous.equals(executionId)) {
      throw new ReconciliationRangeDispatchConflictException("Observed daily executionId changed");
    }
  }

  private void assertDailyRun(
      ReconciliationRunDocument run, DayContext context, String servicesKey) {
    if (run == null) {
      throw new ReconciliationRangeDispatchConflictException("Missing daily run");
    }
    boolean matching = context.logicalRunKey().equals(run.id())
        && context.logicalRunKey().equals(run.logicalRunKey())
        && context.day().equals(run.day()) && servicesKey.equals(run.serviceTypesKey());
    if (!matching || run.status() == null || run.status() == ReconciliationRunStatus.SKIPPED) {
      throw new ReconciliationRangeDispatchConflictException("Invalid daily run identity or status");
    }
  }

  private ReconciliationRangeSnapshot write(
      ReconciliationRangeSnapshot current, UUID ownerId, Map<String, Object> dispatch) {
    lease.assertOwned(current, ownerId);
    var updated = current.document().toBuilder()
        .currentDispatch(Collections.unmodifiableMap(new LinkedHashMap<>(dispatch)))
        .updatedAt(clock.instant().toString()).build();
    try {
      return store.replace(current, updated);
    } catch (CosmosException error) {
      if (error.getStatusCode() == 412) {
        throw new ReconciliationRangeLeaseLostException("Dispatch snapshot changed; stop processing", error);
      }
      throw error;
    }
  }

  private record DayContext(String day, String logicalRunKey) {}
}
