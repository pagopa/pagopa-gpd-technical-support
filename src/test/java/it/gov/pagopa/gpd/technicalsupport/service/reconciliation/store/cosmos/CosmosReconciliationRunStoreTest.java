package it.gov.pagopa.gpd.technicalsupport.service.reconciliation.store.cosmos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosException;
import com.azure.cosmos.models.CosmosItemRequestOptions;
import com.azure.cosmos.models.CosmosItemResponse;
import com.azure.cosmos.models.PartitionKey;
import it.gov.pagopa.gpd.technicalsupport.model.gpd.ServiceType;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.ReconciliationRunCreationResult;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.ReconciliationRunStatus;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.cosmos.ReconciliationRunDocument;
import it.gov.pagopa.gpd.technicalsupport.service.reconciliation.ReconciliationCounters;
import it.gov.pagopa.gpd.technicalsupport.service.reconciliation.key.ReconciliationKeyBuilder;
import it.gov.pagopa.gpd.technicalsupport.service.reconciliation.mapper.ReconciliationRunDocumentMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;

class CosmosReconciliationRunStoreTest {

  private CosmosContainer runsContainer;
  private CosmosReconciliationRunStore store;

  @BeforeEach
  void setUp() {
    runsContainer = mock(CosmosContainer.class);

    Clock clock = Clock.fixed(Instant.parse("2026-05-26T10:00:00Z"), ZoneOffset.UTC);

    ReconciliationKeyBuilder keyBuilder = new ReconciliationKeyBuilder();
    ReconciliationRunDocumentMapper mapper = new ReconciliationRunDocumentMapper(keyBuilder);

    store = new CosmosReconciliationRunStore(runsContainer, keyBuilder, mapper, clock);
  }

  @Test
  void createOrEvaluateRun_shouldCreateRunWhenNotExists() {
    CosmosException notFound = mock(CosmosException.class);
    when(notFound.getStatusCode()).thenReturn(404);

    when(runsContainer.readItem(
            eq("2026-05-20__GPD|WISP"),
            any(PartitionKey.class),
            eq(ReconciliationRunDocument.class)))
        .thenThrow(notFound);

    ReconciliationRunCreationResult result =
        store.createOrEvaluateRun(
            LocalDate.of(2026, Month.MAY, 20),
            List.of(ServiceType.WISP, ServiceType.GPD),
            false);

    assertThat(result.logicalRunKey()).isEqualTo("2026-05-20__GPD|WISP");
    assertThat(result.executionId()).isEqualTo("2026-05-20__GPD|WISP__20260526T100000Z");
    assertThat(result.status()).isEqualTo(ReconciliationRunStatus.CREATED);
    assertThat(result.shouldStart()).isTrue();

    verify(runsContainer)
        .createItem(
            any(ReconciliationRunDocument.class),
            any(PartitionKey.class),
            any(CosmosItemRequestOptions.class));
  }

  @Test
  void createOrEvaluateRun_shouldSkipDoneRunWhenForceIsFalse() {
    ReconciliationRunDocument existingRun =
        ReconciliationRunDocument.builder()
            .id("2026-05-20__GPD|WISP")
            .day("2026-05-20")
            .serviceTypes(List.of(ServiceType.GPD, ServiceType.WISP))
            .serviceTypesKey("GPD|WISP")
            .logicalRunKey("2026-05-20__GPD|WISP")
            .executionId("2026-05-20__GPD|WISP__20260526T090000Z")
            .status(ReconciliationRunStatus.DONE)
            .build();

    @SuppressWarnings("unchecked")
    CosmosItemResponse<ReconciliationRunDocument> response =
        mock(CosmosItemResponse.class);

    when(response.getItem()).thenReturn(existingRun);

    when(runsContainer.readItem(
            eq("2026-05-20__GPD|WISP"),
            any(PartitionKey.class),
            eq(ReconciliationRunDocument.class)))
        .thenReturn(response);

    ReconciliationRunCreationResult result =
        store.createOrEvaluateRun(
            LocalDate.of(2026, Month.MAY, 20),
            List.of(ServiceType.WISP, ServiceType.GPD),
            false);

    assertThat(result.status()).isEqualTo(ReconciliationRunStatus.SKIPPED);
    assertThat(result.shouldStart()).isFalse();
    assertThat(result.executionId()).isEqualTo("2026-05-20__GPD|WISP__20260526T090000Z");
  }

  @Test
  void markDone_shouldUpdateRunWithCounters() {
    ReconciliationRunDocument existingRun =
        ReconciliationRunDocument.builder()
            .id("2026-05-20__GPD")
            .day("2026-05-20")
            .serviceTypes(List.of(ServiceType.GPD))
            .serviceTypesKey("GPD")
            .logicalRunKey("2026-05-20__GPD")
            .executionId("2026-05-20__GPD__20260526T100000Z")
            .status(ReconciliationRunStatus.RUNNING)
            .build();

    @SuppressWarnings("unchecked")
    CosmosItemResponse<ReconciliationRunDocument> response =
        mock(CosmosItemResponse.class);

    when(response.getItem()).thenReturn(existingRun);

    when(runsContainer.readItem(
            eq("2026-05-20__GPD"),
            any(PartitionKey.class),
            eq(ReconciliationRunDocument.class)))
        .thenReturn(response);

    ReconciliationCounters counters =
        new ReconciliationCounters(10, 2, 1, 1, 0, 0, 1, 0);

    store.markDone(
        "2026-05-20__GPD",
        "2026-05-20__GPD__20260526T100000Z",
        counters);

    verify(runsContainer)
        .upsertItem(
            Mockito.argThat(
                (ReconciliationRunDocument document) ->
                    document.status() == ReconciliationRunStatus.DONE
                        && document.scanned() == 10
                        && document.recovered() == 1),
            any(PartitionKey.class),
            any(CosmosItemRequestOptions.class));
  }
  @ParameterizedTest
  @CsvSource({"CREATED,false", "CREATED,true", "RUNNING,false", "RUNNING,true"})
  void activeRun_shouldNeverStartAnotherWorker(ReconciliationRunStatus status, boolean force) {
    ReconciliationRunDocument existing = document(status);
    CosmosItemResponse<ReconciliationRunDocument> existingResponse = response(existing);
    when(runsContainer.readItem(eq("2026-05-20__GPD"), any(PartitionKey.class),
        eq(ReconciliationRunDocument.class))).thenReturn(existingResponse);

    ReconciliationRunCreationResult result =
        store.createOrEvaluateRun(LocalDate.parse("2026-05-20"), List.of(ServiceType.GPD), force);

    assertThat(result.shouldStart()).isFalse();
    assertThat(result.executionId()).isEqualTo("previous-execution");
    assertThat(result.status()).isEqualTo(status);
    verify(runsContainer, never()).createItem(any(), any(PartitionKey.class), any());
    verify(runsContainer, never()).replaceItem(any(), any(String.class), any(PartitionKey.class), any());
  }

  @Test
  void concurrentFirstSubmission_shouldReturnWinningRunWithoutDispatch() {
    CosmosException notFound = cosmosError(404);
    CosmosException conflict = cosmosError(409);
    CosmosItemResponse<ReconciliationRunDocument> winnerResponse =
        response(document(ReconciliationRunStatus.CREATED));
    when(runsContainer.readItem(eq("2026-05-20__GPD"), any(PartitionKey.class),
        eq(ReconciliationRunDocument.class)))
        .thenThrow(notFound).thenReturn(winnerResponse);
    when(runsContainer.createItem(any(ReconciliationRunDocument.class), any(PartitionKey.class), any()))
        .thenThrow(conflict);

    ReconciliationRunCreationResult result =
        store.createOrEvaluateRun(LocalDate.parse("2026-05-20"), List.of(ServiceType.GPD), false);

    assertThat(result.shouldStart()).isFalse();
    assertThat(result.executionId()).isEqualTo("previous-execution");
  }

  @ParameterizedTest
  @CsvSource({"FAILED,false", "FAILED,true", "DONE,true"})
  void restartingTerminalRun_shouldUseConditionalReplace(ReconciliationRunStatus status, boolean force) {
    CosmosItemResponse<ReconciliationRunDocument> existingResponse = response(document(status));
    when(runsContainer.readItem(eq("2026-05-20__GPD"), any(PartitionKey.class),
        eq(ReconciliationRunDocument.class))).thenReturn(existingResponse);

    ReconciliationRunCreationResult result =
        store.createOrEvaluateRun(LocalDate.parse("2026-05-20"), List.of(ServiceType.GPD), force);

    assertThat(result.shouldStart()).isTrue();
    verify(runsContainer).replaceItem(any(ReconciliationRunDocument.class), eq("2026-05-20__GPD"),
        any(PartitionKey.class), Mockito.argThat(options -> "etag-1".equals(options.getIfMatchETag())));
  }

  @Test
  void concurrentTerminalRestart_shouldNotOverwriteWinner() {
    CosmosItemResponse<ReconciliationRunDocument> failedResponse =
        response(document(ReconciliationRunStatus.FAILED));
    CosmosItemResponse<ReconciliationRunDocument> winnerResponse =
        response(document(ReconciliationRunStatus.CREATED));
    CosmosException preconditionFailed = cosmosError(412);
    when(runsContainer.readItem(eq("2026-05-20__GPD"), any(PartitionKey.class),
        eq(ReconciliationRunDocument.class)))
        .thenReturn(failedResponse)
        .thenReturn(winnerResponse);
    when(runsContainer.replaceItem(any(ReconciliationRunDocument.class), eq("2026-05-20__GPD"),
        any(PartitionKey.class), any())).thenThrow(preconditionFailed);

    ReconciliationRunCreationResult result =
        store.createOrEvaluateRun(LocalDate.parse("2026-05-20"), List.of(ServiceType.GPD), false);

    assertThat(result.shouldStart()).isFalse();
    assertThat(result.status()).isEqualTo(ReconciliationRunStatus.CREATED);
  }

  @ParameterizedTest
  @ValueSource(ints = {429, 500, 503})
  void createFailure_shouldPropagateWithoutPretendingRunWasAcquired(int status) {
    CosmosException notFound = cosmosError(404);
    when(runsContainer.readItem(eq("2026-05-20__GPD"), any(PartitionKey.class),
        eq(ReconciliationRunDocument.class))).thenThrow(notFound);
    CosmosException failure = cosmosError(status);
    when(runsContainer.createItem(any(ReconciliationRunDocument.class), any(PartitionKey.class), any()))
        .thenThrow(failure);

    assertThatThrownBy(() -> store.createOrEvaluateRun(
        LocalDate.parse("2026-05-20"), List.of(ServiceType.GPD), false)).isSameAs(failure);
  }

  private ReconciliationRunDocument document(ReconciliationRunStatus status) {
    return ReconciliationRunDocument.builder()
        .id("2026-05-20__GPD").day("2026-05-20")
        .logicalRunKey("2026-05-20__GPD").serviceTypes(List.of(ServiceType.GPD))
        .executionId("previous-execution").status(status).build();
  }

  @SuppressWarnings("unchecked")
  private CosmosItemResponse<ReconciliationRunDocument> response(ReconciliationRunDocument document) {
    CosmosItemResponse<ReconciliationRunDocument> response = mock(CosmosItemResponse.class);
    when(response.getItem()).thenReturn(document);
    when(response.getETag()).thenReturn("etag-1");
    return response;
  }

  private CosmosException cosmosError(int status) {
    CosmosException error = mock(CosmosException.class);
    when(error.getStatusCode()).thenReturn(status);
    return error;
  }

}
