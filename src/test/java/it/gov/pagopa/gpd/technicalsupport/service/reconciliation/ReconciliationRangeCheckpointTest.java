package it.gov.pagopa.gpd.technicalsupport.service.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.gov.pagopa.gpd.technicalsupport.config.reconciliation.ReconciliationProperties;
import it.gov.pagopa.gpd.technicalsupport.config.reconciliation.cosmos.ReconciliationCosmosProperties;
import it.gov.pagopa.gpd.technicalsupport.exception.ReconciliationRangeLeaseLostException;
import it.gov.pagopa.gpd.technicalsupport.model.gpd.ServiceType;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.ReconciliationDailyRunSnapshot;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.ReconciliationRangeDayCounters;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.ReconciliationRangeRequest;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.ReconciliationRangeSnapshot;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.ReconciliationRunStatus;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.cosmos.ReconciliationRangeDayDocument;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.cosmos.ReconciliationRangeDocument;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.cosmos.ReconciliationRunDocument;
import it.gov.pagopa.gpd.technicalsupport.service.reconciliation.mapper.ReconciliationRangeDocumentFactory;
import it.gov.pagopa.gpd.technicalsupport.service.reconciliation.store.cosmos.CosmosReconciliationRangeCheckpointStore;
import it.gov.pagopa.gpd.technicalsupport.service.reconciliation.store.cosmos.CosmosReconciliationRangeStore;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class ReconciliationRangeCheckpointTest {
  private static final UUID OWNER = UUID.fromString("c3875b73-5aeb-4b6d-a3c1-a5ad38033bb9");
  private static final UUID OTHER = UUID.fromString("f211d754-912c-41e1-9797-99a039a955de");
  private static final String DAILY_EXECUTION = "2026-09-01__GPD__20261127T090000Z";
  private static final Instant NOW = Instant.parse("2026-11-27T09:00:00Z");
  private ReconciliationRangeCheckpoint checkpoint;
  private CosmosReconciliationRangeCheckpointStore checkpointStore;
  private ReconciliationRangeSnapshot initial;
  private ReconciliationRunDocument run;
  private ReconciliationRangeDayDocument savedDay;
  private ReconciliationRangeDocument savedSummary;
  private ReconciliationCosmosProperties properties;
  private Clock clock;

  @BeforeEach
  void setUp() {
    clock = mock(Clock.class);
    when(clock.instant()).thenReturn(NOW);
    var fixed = Clock.fixed(NOW, ZoneOffset.UTC);
    var factory = new ReconciliationRangeDocumentFactory(new ReconciliationRangeIdentity(),
        new ReconciliationRangeValidator(new ReconciliationProperties(), fixed), fixed);
    var request = new ReconciliationRangeRequest(LocalDate.of(2026, Month.SEPTEMBER, 1),
        LocalDate.of(2026, Month.SEPTEMBER, 3), List.of(ServiceType.GPD));
    var document = factory.newCreated("dev", "checkpoint-test", request).toBuilder()
        .status("RUNNING").ownerId(OWNER.toString())
        .leaseExpiresAt(NOW.plusSeconds(120).toString())
        .currentDispatch(Map.of("intentId", "intent-1", "day", "2026-09-01",
            "logicalRunKey", "2026-09-01__GPD", "state", "OBSERVED",
            "dailyExecutionId", DAILY_EXECUTION)).build();
    initial = new ReconciliationRangeSnapshot(document, "before-etag");
    run = ReconciliationRunDocument.builder().id("2026-09-01__GPD")
        .logicalRunKey("2026-09-01__GPD").day("2026-09-01")
        .serviceTypesKey("GPD").executionId(DAILY_EXECUTION)
        .status(ReconciliationRunStatus.DONE).scanned(10).positiveEventsFound(10)
        .reconciliationCases(10).recovered(10).payExecuted(10)
        .completedAt(NOW.minusSeconds(1).toString()).build();
    var rangeStore = mock(CosmosReconciliationRangeStore.class);
    var lease = new ReconciliationRangeLease(rangeStore, clock);
    checkpointStore = mock(CosmosReconciliationRangeCheckpointStore.class);
    when(checkpointStore.commit(any(ReconciliationRangeSnapshot.class),
        any(ReconciliationRangeDayDocument.class), any(ReconciliationRangeDocument.class)))
        .thenAnswer(call -> {
          savedDay = call.getArgument(1);
          savedSummary = call.getArgument(2);
          return new ReconciliationRangeSnapshot(savedSummary, "after-etag");
        });
    properties = new ReconciliationCosmosProperties();
    checkpoint = new ReconciliationRangeCheckpoint(lease, checkpointStore, properties, clock);
  }

  private ReconciliationDailyRunSnapshot snapshot(ReconciliationRunDocument daily) {
    return new ReconciliationDailyRunSnapshot(daily, "daily-etag");
  }

  @Test
  void completionArchivesSnapshotAndAdvancesExactlyOneDay() {
    var result = checkpoint.completeDay(initial, OWNER, snapshot(run));
    assertThat(savedDay.id()).isEqualTo("day__2026-09-01");
    assertThat(savedDay.documentType()).isEqualTo("DAY");
    assertThat(savedDay.schemaVersion()).isEqualTo(1);
    assertThat(savedDay.status()).isEqualTo("COMPLETED");
    assertThat(savedDay.executionId()).isEqualTo(initial.document().executionId());
    assertThat(savedDay.dispatchIntentId()).isEqualTo("intent-1");
    assertThat(savedDay.dailyRun().container()).isEqualTo("gpd-reconciliation-runs");
    assertThat(savedDay.dailyRun().partitionKey()).isEqualTo("2026-09-01");
    assertThat(savedDay.dailyRun().id()).isEqualTo(run.id());
    assertThat(savedDay.dailyRun().executionId()).isEqualTo(DAILY_EXECUTION);
    assertThat(savedDay.dailyRun().etag()).isEqualTo("daily-etag");
    assertThat(savedDay.dailyCounters().recovered()).isEqualTo(10);
    assertThat(savedDay.dailyCompletedAt()).isEqualTo(run.completedAt());
    assertThat(savedDay.createdAt()).isEqualTo(NOW.toString());
    assertThat(result.etag()).isEqualTo("after-etag");
    assertThat(savedSummary.completedDays()).isEqualTo(1);
    assertThat(savedSummary.remainingDays()).isEqualTo(2);
    assertThat(savedSummary.currentDay()).isEqualTo("2026-09-02");
    assertThat(savedSummary.currentDispatch()).isNull();
    assertThat(savedSummary.ownerId()).isEqualTo(OWNER.toString());
    assertThat(savedSummary.leaseExpiresAt()).isEqualTo(initial.document().leaseExpiresAt());
    assertThat(savedSummary.status()).isEqualTo("RUNNING");
    assertThat(initial.document().completedDays()).isZero();
    assertThat(initial.document().currentDispatch()).isNotNull();
  }

  @Test
  void lastDayCompletesRangeAndReleasesOwner() {
    var last = initial.document().toBuilder().to("2026-09-01").totalDays(1).remainingDays(1).build();
    var current = new ReconciliationRangeSnapshot(last, initial.etag());
    checkpoint.completeDay(current, OWNER, snapshot(run));
    assertThat(savedSummary.completedDays()).isEqualTo(1);
    assertThat(savedSummary.remainingDays()).isZero();
    assertThat(savedSummary.status()).isEqualTo("COMPLETED");
    assertThat(savedSummary.currentDay()).isNull();
    assertThat(savedSummary.currentDispatch()).isNull();
    assertThat(savedSummary.ownerId()).isNull();
    assertThat(savedSummary.leaseExpiresAt()).isNull();
    assertThat(savedSummary.completedAt()).isEqualTo(NOW.toString());
  }

  @Test
  void manualCasesDoNotBlockSuccessfulDailyCompletion() {
    var manual = run.toBuilder().recovered(8).notRecovered(2).manualRequired(2).build();
    checkpoint.completeDay(initial, OWNER, snapshot(manual));
    assertThat(savedDay.dailyCounters().manualRequired()).isEqualTo(2);
    assertThat(savedSummary.completedDays()).isEqualTo(1);
  }

  @Test
  void emptyDailyRunCanComplete() {
    var empty = run.toBuilder().scanned(0).positiveEventsFound(0)
        .reconciliationCases(0).recovered(0).payExecuted(0).build();
    checkpoint.completeDay(initial, OWNER, snapshot(empty));
    assertThat(savedDay.dailyCounters().scanned()).isZero();
  }

  @Test
  void referenceUsesConfiguredDailyContainer() {
    properties.setRunsContainerName("custom-daily-container");
    checkpoint.completeDay(initial, OWNER, snapshot(run));
    assertThat(savedDay.dailyRun().container()).isEqualTo("custom-daily-container");
  }

  @ParameterizedTest
  @ValueSource(strings = {"scanned", "positiveEventsFound", "reconciliationCases", "recovered",
      "notRecovered", "manualRequired", "technicalFailures", "payExecuted", "payFailed"})
  void negativeCounterBlocksCommit(String field) {
    var builder = run.toBuilder();
    switch (field) {
      case "scanned" -> builder.scanned(-1);
      case "positiveEventsFound" -> builder.positiveEventsFound(-1);
      case "reconciliationCases" -> builder.reconciliationCases(-1);
      case "recovered" -> builder.recovered(-1);
      case "notRecovered" -> builder.notRecovered(-1);
      case "manualRequired" -> builder.manualRequired(-1);
      case "technicalFailures" -> builder.technicalFailures(-1);
      case "payExecuted" -> builder.payExecuted(-1);
      case "payFailed" -> builder.payFailed(-1);
      default -> throw new IllegalArgumentException("Unknown counter");
    }
    var invalid = snapshot(builder.build());
    assertThatThrownBy(() -> checkpoint.completeDay(initial, OWNER, invalid))
        .isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(checkpointStore);
  }

  @ParameterizedTest
  @ValueSource(strings = {"technicalFailures", "payFailed"})
  void technicalFailuresBlockCommit(String field) {
    var builder = run.toBuilder();
    if ("technicalFailures".equals(field)) {
      builder.technicalFailures(1).notRecovered(1);
    } else {
      builder.payFailed(1);
    }
    var invalid = snapshot(builder.build());
    assertThatThrownBy(() -> checkpoint.completeDay(initial, OWNER, invalid))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("technical failures");
    verifyNoInteractions(checkpointStore);
  }

  @Test
  void inconsistentNotRecoveredBlocksCommit() {
    var invalid = snapshot(run.toBuilder().notRecovered(1).build());
    assertThatThrownBy(() -> checkpoint.completeDay(initial, OWNER, invalid))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("notRecovered");
    verifyNoInteractions(checkpointStore);
  }

  @ParameterizedTest
  @EnumSource(value = ReconciliationRunStatus.class, names = {"CREATED", "RUNNING", "FAILED", "SKIPPED"})
  void unfinishedOrFailedRunBlocksCommit(ReconciliationRunStatus status) {
    var invalid = snapshot(run.toBuilder().status(status).build());
    assertThatThrownBy(() -> checkpoint.completeDay(initial, OWNER, invalid))
        .isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(checkpointStore);
  }

  @Test
  void wrongDailyIdentityOrMissingCompletionBlocksCommit() {
    var invalid = List.of(run.toBuilder().id("wrong").build(),
        run.toBuilder().logicalRunKey("wrong").build(),
        run.toBuilder().day("2026-09-02").build(),
        run.toBuilder().serviceTypesKey("WISP").build(),
        run.toBuilder().executionId(null).build(),
        run.toBuilder().status(null).build(),
        run.toBuilder().completedAt(null).build(),
        run.toBuilder().completedAt(" ").build());
    for (var daily : invalid) {
      var input = snapshot(daily);
      assertThatThrownBy(() -> checkpoint.completeDay(initial, OWNER, input))
          .isInstanceOf(IllegalArgumentException.class);
    }
    verifyNoInteractions(checkpointStore);
  }

  @Test
  void completionTimestampMustContainAnOffset() {
    var invalid = snapshot(run.toBuilder().completedAt("2026-11-27T09:00:00").build());
    assertThatThrownBy(() -> checkpoint.completeDay(initial, OWNER, invalid))
        .isInstanceOf(java.time.format.DateTimeParseException.class);
    verifyNoInteractions(checkpointStore);
  }

  @Test
  void missingOrMismatchedObservedIntentBlocksCommit() {
    var input = snapshot(run);
    var missing = new ReconciliationRangeSnapshot(
        initial.document().toBuilder().currentDispatch(null).build(), initial.etag());
    assertThatThrownBy(() -> checkpoint.completeDay(missing, OWNER, input))
        .isInstanceOf(IllegalArgumentException.class);
    var changes = List.of(Map.of("state", "PREPARED"), Map.of("state", "ACKNOWLEDGED"),
        Map.of("day", "2026-09-02"), Map.of("logicalRunKey", "wrong"),
        Map.of("dailyExecutionId", "another-execution"), Map.of("intentId", " "));
    for (var change : changes) {
      var intent = new LinkedHashMap<>(initial.document().currentDispatch());
      intent.putAll(change);
      var current = new ReconciliationRangeSnapshot(
          initial.document().toBuilder().currentDispatch(intent).build(), initial.etag());
      assertThatThrownBy(() -> checkpoint.completeDay(current, OWNER, input))
          .isInstanceOf(IllegalArgumentException.class);
    }
    verifyNoInteractions(checkpointStore);
  }

  @Test
  void inconsistentRangeProgressOrServicesBlocksCommit() {
    var document = initial.document();
    var invalid = List.of(document.toBuilder().completedDays(-1).build(),
        document.toBuilder().completedDays(3).build(),
        document.toBuilder().totalDays(4).build(),
        document.toBuilder().remainingDays(1).build(),
        document.toBuilder().currentDay("2026-09-02").build(),
        document.toBuilder().serviceTypesKey("WISP").build(),
        document.toBuilder().serviceTypes(List.of()).build());
    var input = snapshot(run);
    for (var changed : invalid) {
      var current = new ReconciliationRangeSnapshot(changed, initial.etag());
      assertThatThrownBy(() -> checkpoint.completeDay(current, OWNER, input))
          .isInstanceOf(IllegalArgumentException.class);
    }
    verifyNoInteractions(checkpointStore);
  }

  @Test
  void wrongOwnerCannotCommit() {
    var input = snapshot(run);
    assertThatThrownBy(() -> checkpoint.completeDay(initial, OTHER, input))
        .isInstanceOf(ReconciliationRangeLeaseLostException.class);
    verifyNoInteractions(checkpointStore);
  }

  @Test
  void expiredOwnerCannotCommit() {
    when(clock.instant()).thenReturn(NOW.plusSeconds(120));
    var input = snapshot(run);
    assertThatThrownBy(() -> checkpoint.completeDay(initial, OWNER, input))
        .isInstanceOf(ReconciliationRangeLeaseLostException.class);
    verifyNoInteractions(checkpointStore);
  }

  @Test
  void ownershipIsRecheckedImmediatelyBeforeCommit() {
    when(clock.instant()).thenReturn(NOW, NOW, NOW.plusSeconds(120));
    var input = snapshot(run);
    assertThatThrownBy(() -> checkpoint.completeDay(initial, OWNER, input))
        .isInstanceOf(ReconciliationRangeLeaseLostException.class);
    verifyNoInteractions(checkpointStore);
  }

  @Test
  void dailySnapshotRequiresDocumentAndEtag() {
    assertThatThrownBy(() -> new ReconciliationDailyRunSnapshot(null, "etag"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new ReconciliationDailyRunSnapshot(run, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new ReconciliationDailyRunSnapshot(run, " "))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> checkpoint.completeDay(initial, OWNER, null))
        .isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(checkpointStore);
  }

  @Test
  void checkpointJsonMatchesExistingSchema() throws Exception {
    checkpoint.completeDay(initial, OWNER, snapshot(run));
    var mapper = new ObjectMapper();
    var json = mapper.valueToTree(savedDay);
    assertThat(json.size()).isEqualTo(14);
    assertThat(json.get("dailyRun").get("etag").asText()).isEqualTo("daily-etag");
    assertThat(json.get("dailyCounters").size()).isEqualTo(9);
    assertThat(mapper.treeToValue(json, ReconciliationRangeDayDocument.class)).isEqualTo(savedDay);
    assertThat(savedDay.dailyCounters()).isEqualTo(ReconciliationRangeDayCounters.from(run));
  }
}
