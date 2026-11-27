package it.gov.pagopa.gpd.technicalsupport.service.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.azure.cosmos.CosmosException;
import it.gov.pagopa.gpd.technicalsupport.config.reconciliation.ReconciliationProperties;
import it.gov.pagopa.gpd.technicalsupport.exception.ReconciliationRangeLeaseBusyException;
import it.gov.pagopa.gpd.technicalsupport.exception.ReconciliationRangeLeaseLostException;
import it.gov.pagopa.gpd.technicalsupport.model.gpd.ServiceType;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.ReconciliationRangeRequest;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.ReconciliationRangeSnapshot;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.cosmos.ReconciliationRangeDocument;
import it.gov.pagopa.gpd.technicalsupport.service.reconciliation.mapper.ReconciliationRangeDocumentFactory;
import it.gov.pagopa.gpd.technicalsupport.service.reconciliation.store.cosmos.CosmosReconciliationRangeStore;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ReconciliationRangeLeaseTest {
  private static final UUID OWNER_A = UUID.fromString("c3875b73-5aeb-4b6d-a3c1-a5ad38033bb9");
  private static final UUID OWNER_B = UUID.fromString("f211d754-912c-41e1-9797-99a039a955de");
  private CosmosReconciliationRangeStore store;
  private ReconciliationRangeLease lease;
  private ReconciliationRangeSnapshot persisted;
  private Instant now;
  private int writes;
  private String executionId;

  @BeforeEach
  void setUp() {
    now = Instant.parse("2026-11-27T09:00:00Z");
    Clock clock = mock(Clock.class);
    when(clock.instant()).thenAnswer(call -> now);
    Clock fixed = Clock.fixed(now, ZoneOffset.UTC);
    var factory = new ReconciliationRangeDocumentFactory(new ReconciliationRangeIdentity(),
        new ReconciliationRangeValidator(new ReconciliationProperties(), fixed), fixed);
    var request = new ReconciliationRangeRequest(LocalDate.of(2026, Month.SEPTEMBER, 1),
        LocalDate.of(2026, Month.SEPTEMBER, 30), List.of(ServiceType.GPD));
    var document = factory.newCreated("dev", "lease-test", request);
    executionId = document.executionId();
    persisted = new ReconciliationRangeSnapshot(document, "etag-0");
    store = mock(CosmosReconciliationRangeStore.class);
    when(store.get(executionId)).thenAnswer(call -> persisted);
    when(store.replace(any(ReconciliationRangeSnapshot.class), any(ReconciliationRangeDocument.class)))
        .thenAnswer(call -> persist(call.getArgument(0), call.getArgument(1)));
    lease = new ReconciliationRangeLease(store, clock);
  }

  private CosmosException cosmosError(int code) {
    var error = mock(CosmosException.class);
    when(error.getStatusCode()).thenReturn(code);
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

  @Test
  void acquireSetsOwnerAndHeartbeatWithoutChangingProgress() {
    var initial = persisted.document();
    var result = lease.acquire(executionId, OWNER_A, 120);
    assertThat(result.document().ownerId()).isEqualTo(OWNER_A.toString());
    assertThat(result.document().status()).isEqualTo("RUNNING");
    assertThat(result.document().leaseExpiresAt()).isEqualTo(now.plusSeconds(120).toString());
    assertThat(result.document().heartbeatAt()).isEqualTo(now.toString());
    assertThat(result.document().completedDays()).isZero();
    assertThat(result.document().remainingDays()).isEqualTo(30);
    assertThat(result.document().createdAt()).isEqualTo(initial.createdAt());
    assertThat(initial.ownerId()).isNull();
  }

  @Test
  void activeLeaseCannotBeAcquiredByAnotherOrTheSameOwner() {
    lease.acquire(executionId, OWNER_A, 120);
    for (UUID owner : List.of(OWNER_A, OWNER_B)) {
      assertThatThrownBy(() -> lease.acquire(executionId, owner, 120))
          .isInstanceOf(ReconciliationRangeLeaseBusyException.class);
    }
    assertThat(writes).isEqualTo(1);
  }

  @Test
  void renewExtendsExpirationAndChangesEtag() {
    var first = lease.acquire(executionId, OWNER_A, 120);
    now = now.plusSeconds(30);
    var renewed = lease.renew(first, OWNER_A, 120);
    assertThat(renewed.etag()).isNotEqualTo(first.etag());
    assertThat(renewed.document().leaseExpiresAt()).isEqualTo(now.plusSeconds(120).toString());
    assertThat(renewed.document().heartbeatAt()).isEqualTo(now.toString());
  }

  @ParameterizedTest
  @ValueSource(strings = {"renew", "release"})
  void staleSnapshotCannotOverwriteNewerHeartbeat(String operation) {
    var first = lease.acquire(executionId, OWNER_A, 120);
    var renewed = lease.renew(first, OWNER_A, 120);
    assertThatThrownBy(() -> operate(operation, first, OWNER_A))
        .isInstanceOf(ReconciliationRangeLeaseLostException.class);
    assertThat(persisted).isSameAs(renewed);
    assertThat(writes).isEqualTo(2);
  }

  private void operate(String operation, ReconciliationRangeSnapshot snapshot, UUID owner) {
    if ("renew".equals(operation)) {
      lease.renew(snapshot, owner, 120);
    } else {
      lease.release(snapshot, owner);
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"renew", "release"})
  void ownerCannotWriteAtExactExpiration(String operation) {
    var first = lease.acquire(executionId, OWNER_A, 120);
    now = now.plusSeconds(120);
    assertThatThrownBy(() -> operate(operation, first, OWNER_A))
        .isInstanceOf(ReconciliationRangeLeaseLostException.class);
    assertThat(writes).isEqualTo(1);
  }

  @ParameterizedTest
  @ValueSource(strings = {"renew", "release"})
  void wrongOwnerCannotWrite(String operation) {
    var first = lease.acquire(executionId, OWNER_A, 120);
    assertThatThrownBy(() -> operate(operation, first, OWNER_B))
        .isInstanceOf(ReconciliationRangeLeaseLostException.class);
    assertThat(writes).isEqualTo(1);
  }

  @Test
  void takeoverAfterExpirationPreservesProgressAndDispatch() {
    var first = lease.acquire(executionId, OWNER_A, 120);
    var advanced = first.document().toBuilder().completedDays(5).remainingDays(25)
        .currentDay("2026-09-06").currentDispatch(Map.of("intentId", "original-intent")).build();
    persisted = new ReconciliationRangeSnapshot(advanced, first.etag());
    now = now.plusSeconds(121);
    var second = lease.acquire(executionId, OWNER_B, 120);
    assertThat(second.document().ownerId()).isEqualTo(OWNER_B.toString());
    assertThat(second.document().completedDays()).isEqualTo(5);
    assertThat(second.document().remainingDays()).isEqualTo(25);
    assertThat(second.document().currentDay()).isEqualTo("2026-09-06");
    assertThat(second.document().currentDispatch()).containsEntry("intentId", "original-intent");
    assertThatThrownBy(() -> lease.release(first, OWNER_A))
        .isInstanceOf(ReconciliationRangeLeaseLostException.class);
    assertThat(persisted).isSameAs(second);
  }

  @Test
  void releaseAllowsAnotherOwnerWithoutCompletingRange() {
    var released = lease.release(lease.acquire(executionId, OWNER_A, 120), OWNER_A);
    assertThat(released.document().ownerId()).isNull();
    assertThat(released.document().leaseExpiresAt()).isNull();
    assertThat(released.document().status()).isEqualTo("RUNNING");
    assertThat(lease.acquire(executionId, OWNER_B, 120).document().ownerId()).isEqualTo(OWNER_B.toString());
  }

  @Test
  void competingAcquisitionsUseTheSameEtagAndOnlyOneWins() {
    var original = persisted;
    doReturn(original).when(store).get(executionId);
    lease.acquire(executionId, OWNER_A, 120);
    assertThatThrownBy(() -> lease.acquire(executionId, OWNER_B, 120))
        .isInstanceOf(ReconciliationRangeLeaseLostException.class);
    assertThat(persisted.document().ownerId()).isEqualTo(OWNER_A.toString());
    assertThat(writes).isEqualTo(1);
  }

  @ParameterizedTest
  @ValueSource(strings = {"COMPLETED", "FAILED", "BLOCKED", "UNKNOWN"})
  void terminalOrUnsupportedStatusCannotBeAcquired(String status) {
    persisted = new ReconciliationRangeSnapshot(persisted.document().toBuilder().status(status).build(), "etag-0");
    assertThatThrownBy(() -> lease.acquire(executionId, OWNER_A, 120))
        .isInstanceOf(IllegalStateException.class);
    assertThat(writes).isZero();
  }

  @Test
  void invalidSchemaAndOwnershipAreNotReset() {
    var original = persisted.document();
    var invalid = List.of(original.toBuilder().schemaVersion(2).build(),
        original.toBuilder().documentType("DAY").build(),
        original.toBuilder().ownerId(OWNER_A.toString()).build(),
        original.toBuilder().leaseExpiresAt(now.toString()).build(),
        original.toBuilder().ownerId("invalid-owner").leaseExpiresAt(now.toString()).build(),
        original.toBuilder().ownerId(OWNER_A.toString()).leaseExpiresAt("2026-11-27T09:00:00").build());
    for (var document : invalid) {
      persisted = new ReconciliationRangeSnapshot(document, "etag-0");
      assertThatThrownBy(() -> lease.acquire(executionId, OWNER_A, 120))
          .isInstanceOf(IllegalStateException.class);
    }
    assertThat(writes).isZero();
  }

  @Test
  void invalidDurationOrMissingOwnerFailsBeforeReadingStore() {
    for (long seconds : new long[] {0, -1}) {
      assertThatThrownBy(() -> lease.acquire(executionId, OWNER_A, seconds))
          .isInstanceOf(IllegalArgumentException.class);
    }
    assertThatThrownBy(() -> lease.acquire(executionId, null, 120))
        .isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(store);
  }

  @Test
  void cosmosOutageIsPropagatedWithoutSuccessOrRetry() {
    var unavailable = cosmosError(503);
    doThrow(unavailable).when(store).replace(any(ReconciliationRangeSnapshot.class), any(ReconciliationRangeDocument.class));
    assertThatThrownBy(() -> lease.acquire(executionId, OWNER_A, 120)).isSameAs(unavailable);
    assertThat(writes).isZero();
  }

  @Test
  void assertOwnedDoesNotWriteAndRejectsExpiredOwnership() {
    var held = lease.acquire(executionId, OWNER_A, 120);
    lease.assertOwned(held, OWNER_A);
    now = now.plusSeconds(120);
    assertThatThrownBy(() -> lease.assertOwned(held, OWNER_A))
        .isInstanceOf(ReconciliationRangeLeaseLostException.class);
    assertThat(writes).isEqualTo(1);
  }

  @Test
  void lostResponseAfterCommittedAcquisitionDoesNotAllowAnotherAcquisition() {
    var unavailable = cosmosError(503);
    doAnswer(call -> {
      persist(call.getArgument(0), call.getArgument(1));
      throw unavailable;
    }).when(store).replace(any(ReconciliationRangeSnapshot.class), any(ReconciliationRangeDocument.class));
    assertThatThrownBy(() -> lease.acquire(executionId, OWNER_A, 120)).isSameAs(unavailable);
    assertThatThrownBy(() -> lease.acquire(executionId, OWNER_A, 120))
        .isInstanceOf(ReconciliationRangeLeaseBusyException.class);
    assertThat(writes).isEqualTo(1);
  }
}
