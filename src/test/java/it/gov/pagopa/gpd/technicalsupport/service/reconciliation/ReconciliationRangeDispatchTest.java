package it.gov.pagopa.gpd.technicalsupport.service.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.azure.cosmos.CosmosException;
import it.gov.pagopa.gpd.technicalsupport.config.reconciliation.ReconciliationProperties;
import it.gov.pagopa.gpd.technicalsupport.exception.ReconciliationRangeDispatchConflictException;
import it.gov.pagopa.gpd.technicalsupport.exception.ReconciliationRangeLeaseLostException;
import it.gov.pagopa.gpd.technicalsupport.model.gpd.ServiceType;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.ReconciliationRangeRequest;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.ReconciliationRangeSnapshot;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.ReconciliationRunStatus;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.cosmos.ReconciliationRangeDocument;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.cosmos.ReconciliationRunDocument;
import it.gov.pagopa.gpd.technicalsupport.service.reconciliation.mapper.ReconciliationRangeDocumentFactory;
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

class ReconciliationRangeDispatchTest {
  private static final UUID OWNER = UUID.fromString("c3875b73-5aeb-4b6d-a3c1-a5ad38033bb9");
  private static final UUID OTHER = UUID.fromString("f211d754-912c-41e1-9797-99a039a955de");
  private static final String DAILY_EXECUTION = "2026-09-01__GPD__20261127T090000Z";
  private CosmosReconciliationRangeStore store;
  private ReconciliationRangeLease lease;
  private ReconciliationRangeDispatch dispatch;
  private ReconciliationRangeSnapshot persisted;
  private ReconciliationRunDocument daily;
  private Instant now;
  private int writes;

  @BeforeEach
  void setUp() {
    now = Instant.parse("2026-11-27T09:00:00Z");
    Clock clock = mock(Clock.class);
    when(clock.instant()).thenAnswer(call -> now);
    Clock fixed = Clock.fixed(now, ZoneOffset.UTC);
    var factory = new ReconciliationRangeDocumentFactory(new ReconciliationRangeIdentity(),
        new ReconciliationRangeValidator(new ReconciliationProperties(), fixed), fixed);
    var request = new ReconciliationRangeRequest(LocalDate.of(2026, Month.SEPTEMBER, 1),
        LocalDate.of(2026, Month.SEPTEMBER, 3), List.of(ServiceType.GPD));
    var document = factory.newCreated("dev", "dispatch-test", request).toBuilder()
        .status("RUNNING").ownerId(OWNER.toString())
        .leaseExpiresAt(now.plusSeconds(120).toString()).build();
    persisted = new ReconciliationRangeSnapshot(document, "etag-0");
    store = mock(CosmosReconciliationRangeStore.class);
    when(store.get(document.executionId())).thenAnswer(call -> persisted);
    when(store.replace(any(ReconciliationRangeSnapshot.class), any(ReconciliationRangeDocument.class)))
        .thenAnswer(call -> persist(call.getArgument(0), call.getArgument(1)));
    lease = new ReconciliationRangeLease(store, clock);
    dispatch = new ReconciliationRangeDispatch(store, lease, clock);
    daily = ReconciliationRunDocument.builder().id("2026-09-01__GPD")
        .logicalRunKey("2026-09-01__GPD").day("2026-09-01")
        .serviceTypesKey("GPD").serviceTypes(List.of(ServiceType.GPD))
        .executionId(DAILY_EXECUTION).status(ReconciliationRunStatus.RUNNING).build();
  }

  private CosmosException cosmosError(int status) {
    var error = mock(CosmosException.class);
    when(error.getStatusCode()).thenReturn(status);
    return error;
  }

  private ReconciliationRangeSnapshot persist(
      ReconciliationRangeSnapshot before, ReconciliationRangeDocument updated) {
    if (!before.etag().equals(persisted.etag())) {
      throw cosmosError(412);
    }
    writes++;
    persisted = new ReconciliationRangeSnapshot(updated, "etag-" + writes);
    return persisted;
  }

  private ReconciliationRangeSnapshot prepared() {
    return dispatch.prepare(persisted, OWNER).snapshot();
  }

  @Test
  void preparePersistsPythonCompatibleIntentWithoutAdvancing() {
    var result = dispatch.prepare(persisted, OWNER);
    assertThat(result.created()).isTrue();
    var document = result.snapshot().document();
    var intent = document.currentDispatch();
    assertThat(intent).hasSize(7).containsEntry("day", "2026-09-01")
        .containsEntry("logicalRunKey", "2026-09-01__GPD")
        .containsEntry("state", "PREPARED").containsEntry("dailyExecutionId", null)
        .containsEntry("createdAt", now.toString()).containsEntry("updatedAt", now.toString());
    String intentId = (String) intent.get("intentId");
    assertThat(UUID.fromString(intentId)).hasToString(intentId);
    assertThat(document.completedDays()).isZero();
    assertThat(document.remainingDays()).isEqualTo(3);
    assertThat(document.currentDay()).isEqualTo("2026-09-01");
    assertThat(document.ownerId()).isEqualTo(OWNER.toString());
    assertThat(document.leaseExpiresAt()).isEqualTo(now.plusSeconds(120).toString());
  }

  @Test
  void repeatedPreparationDoesNotWriteOrReplaceIntent() {
    var first = prepared();
    var second = dispatch.prepare(first, OWNER);
    assertThat(second.created()).isFalse();
    assertThat(second.snapshot()).isSameAs(first);
    assertThat(writes).isEqualTo(1);
  }

  @Test
  void newOwnerReusesIntentAfterLeaseExpires() {
    var first = prepared();
    now = now.plusSeconds(121);
    String executionId = first.document().executionId();
    var acquired = lease.acquire(executionId, OTHER, 120);
    var resumed = dispatch.prepare(acquired, OTHER);
    assertThat(resumed.created()).isFalse();
    assertThat(resumed.snapshot().document().currentDispatch())
        .isEqualTo(first.document().currentDispatch());
    assertThat(writes).isEqualTo(2);
  }

  @Test
  void acknowledgementPersistsReferenceAndRetainsIntentAndCreationTime() {
    var first = prepared();
    now = now.plusSeconds(5);
    var acknowledged = dispatch.acknowledge(first, OWNER, DAILY_EXECUTION);
    assertThat(acknowledged.document().currentDispatch())
        .containsEntry("state", "ACKNOWLEDGED")
        .containsEntry("dailyExecutionId", DAILY_EXECUTION)
        .containsEntry("intentId", first.document().currentDispatch().get("intentId"))
        .containsEntry("createdAt", first.document().currentDispatch().get("createdAt"))
        .containsEntry("updatedAt", now.toString());
    assertThat(first.document().currentDispatch()).containsEntry("state", "PREPARED");
    assertThat(writes).isEqualTo(2);
  }

  @Test
  void repeatedAcknowledgementAndPreparationDoNotWrite() {
    var first = prepared();
    var acknowledged = dispatch.acknowledge(first, OWNER, DAILY_EXECUTION);
    assertThat(dispatch.acknowledge(acknowledged, OWNER, DAILY_EXECUTION)).isSameAs(acknowledged);
    assertThat(dispatch.prepare(acknowledged, OWNER).created()).isFalse();
    assertThat(writes).isEqualTo(2);
  }

  @ParameterizedTest
  @EnumSource(value = ReconciliationRunStatus.class, names = {"CREATED", "RUNNING", "DONE", "FAILED"})
  void observationAcceptsPersistedDailyStatusesWithoutAdvancing(ReconciliationRunStatus status) {
    var first = prepared();
    var run = daily.toBuilder().status(status).build();
    var observed = dispatch.observe(first, OWNER, run);
    assertThat(observed.document().currentDispatch()).containsEntry("state", "OBSERVED")
        .containsEntry("dailyExecutionId", DAILY_EXECUTION);
    assertThat(observed.document().completedDays()).isZero();
    assertThat(observed.document().status()).isEqualTo("RUNNING");
    assertThat(dispatch.observe(observed, OWNER, run)).isSameAs(observed);
    assertThat(writes).isEqualTo(2);
  }

  @Test
  void observationAfterAcknowledgementPreservesDailyIdentity() {
    var first = prepared();
    var acknowledged = dispatch.acknowledge(first, OWNER, DAILY_EXECUTION);
    var observed = dispatch.observe(acknowledged, OWNER, daily);
    assertThat(observed.document().currentDispatch()).containsEntry("state", "OBSERVED");
    assertThat(dispatch.acknowledge(observed, OWNER, DAILY_EXECUTION)).isSameAs(observed);
    assertThat(writes).isEqualTo(3);
  }

  @Test
  void acknowledgementCannotReplacePreviouslyObservedExecution() {
    var observed = dispatch.observe(prepared(), OWNER, daily);
    assertThatThrownBy(() -> dispatch.acknowledge(observed, OWNER, "another-execution"))
        .isInstanceOf(ReconciliationRangeDispatchConflictException.class);
    assertThat(writes).isEqualTo(2);
  }

  @Test
  void observationCannotReplaceAcknowledgedExecution() {
    var acknowledged = dispatch.acknowledge(prepared(), OWNER, DAILY_EXECUTION);
    var changed = daily.toBuilder().executionId("another-execution").build();
    assertThatThrownBy(() -> dispatch.observe(acknowledged, OWNER, changed))
        .isInstanceOf(ReconciliationRangeDispatchConflictException.class);
    assertThat(writes).isEqualTo(2);
  }

  @Test
  void acknowledgementAndObservationRequirePersistedIntent() {
    var initial = persisted;
    assertThatThrownBy(() -> dispatch.acknowledge(initial, OWNER, DAILY_EXECUTION))
        .isInstanceOf(ReconciliationRangeDispatchConflictException.class);
    assertThatThrownBy(() -> dispatch.observe(initial, OWNER, daily))
        .isInstanceOf(ReconciliationRangeDispatchConflictException.class);
    assertThat(writes).isZero();
  }

  @Test
  void wrongOrMissingDailyRunIsRejectedWithoutWriting() {
    var first = prepared();
    var invalid = List.of(daily.toBuilder().id("wrong").build(),
        daily.toBuilder().logicalRunKey("wrong").build(),
        daily.toBuilder().day("2026-09-02").build(),
        daily.toBuilder().serviceTypesKey("WISP").build(),
        daily.toBuilder().executionId(null).build(),
        daily.toBuilder().executionId(" ").build(),
        daily.toBuilder().status(null).build(),
        daily.toBuilder().status(ReconciliationRunStatus.SKIPPED).build());
    for (var run : invalid) {
      assertThatThrownBy(() -> dispatch.observe(first, OWNER, run))
          .isInstanceOf(ReconciliationRangeDispatchConflictException.class);
    }
    assertThatThrownBy(() -> dispatch.observe(first, OWNER, null))
        .isInstanceOf(ReconciliationRangeDispatchConflictException.class);
    assertThat(writes).isEqualTo(1);
  }

  @Test
  void missingAcknowledgedExecutionIsRejected() {
    var first = prepared();
    assertThatThrownBy(() -> dispatch.acknowledge(first, OWNER, null))
        .isInstanceOf(ReconciliationRangeDispatchConflictException.class);
    assertThatThrownBy(() -> dispatch.acknowledge(first, OWNER, " "))
        .isInstanceOf(ReconciliationRangeDispatchConflictException.class);
    assertThat(writes).isEqualTo(1);
  }

  @Test
  void inconsistentPersistedIntentsAreNeverReset() {
    var first = prepared();
    var changes = List.of(Map.of("day", "2026-09-02"), Map.of("logicalRunKey", "wrong"),
        Map.of("intentId", " "), Map.of("state", "UNKNOWN"), Map.of("state", "OBSERVED"),
        Map.of("state", "ACKNOWLEDGED"), Map.of("dailyExecutionId", "unexpected-reference"));
    for (var change : changes) {
      var intent = new LinkedHashMap<>(first.document().currentDispatch());
      intent.putAll(change);
      var inconsistent = new ReconciliationRangeSnapshot(
          first.document().toBuilder().currentDispatch(intent).build(), first.etag());
      assertThatThrownBy(() -> dispatch.prepare(inconsistent, OWNER))
          .isInstanceOf(ReconciliationRangeDispatchConflictException.class);
    }
    assertThat(writes).isEqualTo(1);
  }

  @Test
  void inconsistentProgressAndServicesAreRejected() {
    var original = persisted;
    var document = original.document();
    var invalid = List.of(document.toBuilder().completedDays(-1).build(),
        document.toBuilder().completedDays(3).remainingDays(0).build(),
        document.toBuilder().totalDays(4).build(),
        document.toBuilder().remainingDays(2).build(),
        document.toBuilder().currentDay("2026-09-02").build(),
        document.toBuilder().serviceTypesKey("WISP").build(),
        document.toBuilder().serviceTypes(List.of()).build(),
        document.toBuilder().serviceTypes(List.of("UNKNOWN")).build(),
        document.toBuilder().serviceTypes(null).build(),
        document.toBuilder().currentDay(null).build(),
        document.toBuilder().from("invalid-date").build());
    for (var changed : invalid) {
      var snapshot = new ReconciliationRangeSnapshot(changed, original.etag());
      assertThatThrownBy(() -> dispatch.prepare(snapshot, OWNER))
          .isInstanceOf(ReconciliationRangeDispatchConflictException.class);
    }
    assertThat(writes).isZero();
  }

  @Test
  void staleSnapshotCannotPrepareOrObserve() {
    var stale = persisted;
    var first = prepared();
    assertThatThrownBy(() -> dispatch.prepare(stale, OWNER))
        .isInstanceOf(ReconciliationRangeLeaseLostException.class);
    var renewed = lease.renew(first, OWNER, 120);
    assertThatThrownBy(() -> dispatch.observe(first, OWNER, daily))
        .isInstanceOf(ReconciliationRangeLeaseLostException.class);
    assertThat(persisted).isSameAs(renewed);
    assertThat(writes).isEqualTo(2);
  }

  @ParameterizedTest
  @ValueSource(strings = {"prepare", "acknowledge", "observe"})
  void expiredOwnerCannotWrite(String operation) {
    var first = prepared();
    now = now.plusSeconds(120);
    assertThatThrownBy(() -> operate(operation, first, OWNER))
        .isInstanceOf(ReconciliationRangeLeaseLostException.class);
    assertThat(writes).isEqualTo(1);
  }

  @ParameterizedTest
  @ValueSource(strings = {"prepare", "acknowledge", "observe"})
  void wrongOwnerCannotWrite(String operation) {
    var first = prepared();
    assertThatThrownBy(() -> operate(operation, first, OTHER))
        .isInstanceOf(ReconciliationRangeLeaseLostException.class);
    assertThat(writes).isEqualTo(1);
  }

  private void operate(String operation, ReconciliationRangeSnapshot snapshot, UUID owner) {
    switch (operation) {
      case "prepare" -> dispatch.prepare(snapshot, owner);
      case "acknowledge" -> dispatch.acknowledge(snapshot, owner, DAILY_EXECUTION);
      case "observe" -> dispatch.observe(snapshot, owner, daily);
      default -> throw new IllegalArgumentException("Unknown test operation");
    }
  }

  @ParameterizedTest
  @ValueSource(ints = {408, 503})
  void cosmosFailureIsPropagatedWithoutRetry(int status) {
    var failure = cosmosError(status);
    doThrow(failure).when(store).replace(any(ReconciliationRangeSnapshot.class), any(ReconciliationRangeDocument.class));
    var original = persisted;
    assertThatThrownBy(() -> dispatch.prepare(original, OWNER)).isSameAs(failure);
    assertThat(writes).isZero();
    verify(store, times(1)).replace(any(ReconciliationRangeSnapshot.class), any(ReconciliationRangeDocument.class));
  }

  @Test
  void lostResponseAfterCommitRequiresReloadAndReusesOriginalIntent() {
    var failure = cosmosError(503);
    doAnswer(call -> {
      persist(call.getArgument(0), call.getArgument(1));
      throw failure;
    }).when(store).replace(any(ReconciliationRangeSnapshot.class), any(ReconciliationRangeDocument.class));
    var original = persisted;
    assertThatThrownBy(() -> dispatch.prepare(original, OWNER)).isSameAs(failure);
    var reloaded = store.get(original.document().executionId());
    var resumed = dispatch.prepare(reloaded, OWNER);
    assertThat(resumed.created()).isFalse();
    assertThat(resumed.snapshot()).isSameAs(reloaded);
    assertThat(writes).isEqualTo(1);
  }
}
