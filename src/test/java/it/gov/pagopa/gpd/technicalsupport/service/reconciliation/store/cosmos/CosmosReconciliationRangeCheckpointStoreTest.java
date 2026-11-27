package it.gov.pagopa.gpd.technicalsupport.service.reconciliation.store.cosmos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosException;
import com.azure.cosmos.models.CosmosBatch;
import com.azure.cosmos.models.CosmosBatchItemRequestOptions;
import com.azure.cosmos.models.CosmosBatchOperationResult;
import com.azure.cosmos.models.CosmosBatchResponse;
import com.azure.cosmos.models.CosmosItemOperationType;
import com.azure.cosmos.models.PartitionKey;
import it.gov.pagopa.gpd.technicalsupport.exception.ReconciliationRangeCheckpointConflictException;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.ReconciliationRangeSnapshot;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.cosmos.ReconciliationRangeDayDocument;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.cosmos.ReconciliationRangeDocument;
import java.util.ArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

class CosmosReconciliationRangeCheckpointStoreTest {
  private static final String EXECUTION = "2ec4a982-9faa-4a24-a794-fc28c385ea56";
  private CosmosContainer container;
  private CosmosReconciliationRangeStore rangeStore;
  private CosmosReconciliationRangeCheckpointStore store;
  private ReconciliationRangeSnapshot current;
  private ReconciliationRangeDocument updated;
  private ReconciliationRangeDayDocument day;

  @BeforeEach
  void setUp() {
    container = mock(CosmosContainer.class);
    rangeStore = mock(CosmosReconciliationRangeStore.class);
    store = new CosmosReconciliationRangeCheckpointStore(container, rangeStore);
    var document = ReconciliationRangeDocument.builder().id("summary").documentType("RANGE")
        .schemaVersion(1).executionId(EXECUTION).status("RUNNING")
        .currentDay("2026-09-01").totalDays(3).completedDays(0).remainingDays(3).build();
    current = new ReconciliationRangeSnapshot(document, "summary-etag");
    updated = document.toBuilder().completedDays(1).remainingDays(2).currentDay("2026-09-02").build();
    day = ReconciliationRangeDayDocument.builder().id("day__2026-09-01")
        .documentType("DAY").schemaVersion(1).executionId(EXECUTION).day("2026-09-01")
        .status("COMPLETED").build();
  }

  private CosmosBatchResponse response(int status, int... operationStatuses) {
    var response = mock(CosmosBatchResponse.class);
    var results = new ArrayList<CosmosBatchOperationResult>();
    for (int code : operationStatuses) {
      var result = mock(CosmosBatchOperationResult.class);
      when(result.getStatusCode()).thenReturn(code);
      results.add(result);
    }
    when(response.getStatusCode()).thenReturn(status);
    when(response.isSuccessStatusCode()).thenReturn(status >= 200 && status < 300);
    when(response.getResults()).thenReturn(results);
    return response;
  }

  private CosmosException cosmosError(int status) {
    var error = mock(CosmosException.class);
    when(error.getStatusCode()).thenReturn(status);
    return error;
  }

  @Test
  void commitsCreateAndReplaceInOneBatchThenReloadsPersistedSummary() {
    var success = response(200, 201, 200);
    var persisted = new ReconciliationRangeSnapshot(updated, "persisted-etag");
    when(container.executeCosmosBatch(any(CosmosBatch.class))).thenReturn(success);
    when(rangeStore.get(EXECUTION)).thenReturn(persisted);
    assertThat(store.commit(current, day, updated)).isSameAs(persisted);
    var capture = ArgumentCaptor.forClass(CosmosBatch.class);
    verify(container).executeCosmosBatch(capture.capture());
    var batch = capture.getValue();
    assertThat(batch.getPartitionKeyValue()).isEqualTo(new PartitionKey(EXECUTION));
    assertThat(batch.getOperations()).hasSize(2);
    var create = batch.getOperations().get(0);
    var replace = batch.getOperations().get(1);
    assertThat(create.getOperationType()).isEqualTo(CosmosItemOperationType.CREATE);
    assertThat(replace.getOperationType()).isEqualTo(CosmosItemOperationType.REPLACE);
    ReconciliationRangeDayDocument checkpointBody = create.getItem();
    ReconciliationRangeDocument summaryBody = replace.getItem();
    assertThat(checkpointBody).isSameAs(day);
    assertThat(summaryBody).isSameAs(updated);
    assertThat(replace.getId()).isEqualTo("summary");
    verify(rangeStore).get(EXECUTION);
    verifyNoMoreInteractions(container, rangeStore);
  }

  @Test
  void summaryReplacementUsesTheSnapshotEtag() {
    var success = response(200, 201, 200);
    var batch = mock(CosmosBatch.class);
    when(container.executeCosmosBatch(batch)).thenReturn(success);
    var persisted = new ReconciliationRangeSnapshot(updated, "persisted-etag");
    when(rangeStore.get(EXECUTION)).thenReturn(persisted);
    try (var batches = mockStatic(CosmosBatch.class)) {
      batches.when(() -> CosmosBatch.createCosmosBatch(any(PartitionKey.class))).thenReturn(batch);
      store.commit(current, day, updated);
      var options = ArgumentCaptor.forClass(CosmosBatchItemRequestOptions.class);
      verify(batch).createItemOperation(day);
      verify(batch).replaceItemOperation(eq("summary"), eq(updated), options.capture());
      assertThat(options.getValue().getIfMatchETag()).isEqualTo(current.etag());
    }
  }

  @ParameterizedTest
  @ValueSource(ints = {409, 412})
  void conflictingBatchResponseRequiresReloadWithoutRetry(int status) {
    var failed = response(status, status, 424);
    when(container.executeCosmosBatch(any(CosmosBatch.class))).thenReturn(failed);
    assertThatThrownBy(() -> store.commit(current, day, updated))
        .isInstanceOf(ReconciliationRangeCheckpointConflictException.class);
    verify(container).executeCosmosBatch(any(CosmosBatch.class));
    verifyNoMoreInteractions(container);
    verifyNoInteractions(rangeStore);
    assertThat(current.document().completedDays()).isZero();
  }

  @ParameterizedTest
  @ValueSource(ints = {409, 412})
  void embeddedOperationConflictIsRecognizedUnderFailedDependency(int status) {
    var failed = response(424, 424, status);
    when(container.executeCosmosBatch(any(CosmosBatch.class))).thenReturn(failed);
    assertThatThrownBy(() -> store.commit(current, day, updated))
        .isInstanceOf(ReconciliationRangeCheckpointConflictException.class);
    verifyNoInteractions(rangeStore);
  }

  @ParameterizedTest
  @ValueSource(ints = {409, 412})
  void thrownConflictIsTranslatedAndPreservesCause(int status) {
    var failure = cosmosError(status);
    when(container.executeCosmosBatch(any(CosmosBatch.class))).thenThrow(failure);
    assertThatThrownBy(() -> store.commit(current, day, updated))
        .isInstanceOf(ReconciliationRangeCheckpointConflictException.class).hasCause(failure);
    verifyNoInteractions(rangeStore);
  }

  @ParameterizedTest
  @ValueSource(ints = {408, 429, 503})
  void transportOrServiceFailureIsPropagatedWithoutApplicationRetry(int status) {
    var failure = cosmosError(status);
    when(container.executeCosmosBatch(any(CosmosBatch.class))).thenThrow(failure);
    assertThatThrownBy(() -> store.commit(current, day, updated)).isSameAs(failure);
    verify(container).executeCosmosBatch(any(CosmosBatch.class));
    verifyNoMoreInteractions(container);
    verifyNoInteractions(rangeStore);
  }

  @ParameterizedTest
  @ValueSource(ints = {400, 429, 503})
  void unsuccessfulNonConflictResponseIsNeverReportedAsSuccess(int status) {
    var failure = response(status, status, 424);
    when(container.executeCosmosBatch(any(CosmosBatch.class))).thenReturn(failure);
    assertThatThrownBy(() -> store.commit(current, day, updated))
        .isInstanceOf(IllegalStateException.class);
    verifyNoInteractions(rangeStore);
  }

  @Test
  void incompleteOperationResultsAreNotAccepted() {
    var incomplete = response(200, 201);
    when(container.executeCosmosBatch(any(CosmosBatch.class))).thenReturn(incomplete);
    assertThatThrownBy(() -> store.commit(current, day, updated))
        .isInstanceOf(IllegalStateException.class);
    verifyNoInteractions(rangeStore);
  }

  @Test
  void operationFailureCannotBeHiddenBySuccessfulTopLevelStatus() {
    var failure = response(200, 201, 400);
    when(container.executeCosmosBatch(any(CosmosBatch.class))).thenReturn(failure);
    assertThatThrownBy(() -> store.commit(current, day, updated))
        .isInstanceOf(IllegalStateException.class);
    verifyNoInteractions(rangeStore);
  }

  @Test
  void readFailureAfterSuccessfulCommitDoesNotTriggerAnotherBatch() {
    var success = response(200, 201, 200);
    var failure = cosmosError(503);
    when(container.executeCosmosBatch(any(CosmosBatch.class))).thenReturn(success);
    when(rangeStore.get(EXECUTION)).thenThrow(failure);
    assertThatThrownBy(() -> store.commit(current, day, updated)).isSameAs(failure);
    verify(container).executeCosmosBatch(any(CosmosBatch.class));
    verify(rangeStore).get(EXECUTION);
    verifyNoMoreInteractions(container, rangeStore);
  }

  @Test
  void replayCannotOverwriteExistingCheckpointOrAdvanceAgain() {
    var success = response(200, 201, 200);
    var duplicate = response(409, 409, 424);
    var persisted = new ReconciliationRangeSnapshot(updated, "persisted-etag");
    when(container.executeCosmosBatch(any(CosmosBatch.class))).thenReturn(success, duplicate);
    when(rangeStore.get(EXECUTION)).thenReturn(persisted);
    assertThat(store.commit(current, day, updated)).isSameAs(persisted);
    assertThatThrownBy(() -> store.commit(current, day, updated))
        .isInstanceOf(ReconciliationRangeCheckpointConflictException.class);
    assertThat(persisted.document().completedDays()).isEqualTo(1);
    verify(rangeStore, times(1)).get(EXECUTION);
    verify(container, times(2)).executeCosmosBatch(any(CosmosBatch.class));
  }

  @Test
  void responseLostAfterCommitDoesNotCauseBlindResubmission() {
    var failure = cosmosError(503);
    var persisted = new ReconciliationRangeSnapshot(updated, "persisted-etag");
    when(container.executeCosmosBatch(any(CosmosBatch.class))).thenAnswer(call -> {
      when(rangeStore.get(EXECUTION)).thenReturn(persisted);
      throw failure;
    });
    assertThatThrownBy(() -> store.commit(current, day, updated)).isSameAs(failure);
    verifyNoInteractions(rangeStore);
    assertThat(rangeStore.get(EXECUTION)).isSameAs(persisted);
    verify(container, times(1)).executeCosmosBatch(any(CosmosBatch.class));
  }

  @Test
  void documentsFromDifferentPartitionsAreRejectedBeforeCosmos() {
    var otherRange = updated.toBuilder().executionId("another-range").build();
    assertThatThrownBy(() -> store.commit(current, day, otherRange))
        .isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(container, rangeStore);
  }
}
