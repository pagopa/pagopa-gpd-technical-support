package it.gov.pagopa.gpd.technicalsupport.service.reconciliation.store.cosmos;

import static org.assertj.core.api.Assertions.assertThat;
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
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
            LocalDate.of(2026, 5, 20),
            List.of(ServiceType.WISP, ServiceType.GPD),
            false);

    assertThat(result.logicalRunKey()).isEqualTo("2026-05-20__GPD|WISP");
    assertThat(result.executionId()).isEqualTo("2026-05-20__GPD|WISP__20260526T100000Z");
    assertThat(result.status()).isEqualTo(ReconciliationRunStatus.CREATED);
    assertThat(result.shouldStart()).isTrue();

    verify(runsContainer)
        .upsertItem(
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
            LocalDate.of(2026, 5, 20),
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
}