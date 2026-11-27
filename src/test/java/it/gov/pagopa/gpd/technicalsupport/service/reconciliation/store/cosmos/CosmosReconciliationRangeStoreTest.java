package it.gov.pagopa.gpd.technicalsupport.service.reconciliation.store.cosmos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosException;
import com.azure.cosmos.models.CosmosItemRequestOptions;
import com.azure.cosmos.models.CosmosItemResponse;
import com.azure.cosmos.models.PartitionKey;
import it.gov.pagopa.gpd.technicalsupport.config.reconciliation.ReconciliationProperties;
import it.gov.pagopa.gpd.technicalsupport.exception.AppException;
import it.gov.pagopa.gpd.technicalsupport.model.gpd.ServiceType;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.ReconciliationRangeRequest;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.ReconciliationRangeSnapshot;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.cosmos.ReconciliationRangeDocument;
import it.gov.pagopa.gpd.technicalsupport.service.reconciliation.ReconciliationRangeIdentity;
import it.gov.pagopa.gpd.technicalsupport.service.reconciliation.ReconciliationRangeValidator;
import it.gov.pagopa.gpd.technicalsupport.service.reconciliation.mapper.ReconciliationRangeDocumentFactory;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

class CosmosReconciliationRangeStoreTest {
  private CosmosContainer container;
  private ReconciliationRangeDocumentFactory factory;
  private CosmosReconciliationRangeStore store;
  private ReconciliationRangeRequest request;

  @BeforeEach
  void setUp() {
    container = mock(CosmosContainer.class);
    Clock clock = Clock.fixed(Instant.parse("2026-11-25T09:00:00Z"), ZoneOffset.UTC);
    factory = new ReconciliationRangeDocumentFactory(new ReconciliationRangeIdentity(),
        new ReconciliationRangeValidator(new ReconciliationProperties(), clock), clock);
    store = new CosmosReconciliationRangeStore(container, factory);
    request = new ReconciliationRangeRequest(LocalDate.of(2026, Month.SEPTEMBER, 1), LocalDate.of(2026, Month.SEPTEMBER, 30),
        List.of(ServiceType.WISP, ServiceType.GPD, ServiceType.GPD));
  }

  private ReconciliationRangeDocument document() {
    return factory.newCreated("dev", "request-1", request);
  }

  private CosmosException error(int status) {
    var error = mock(CosmosException.class);
    when(error.getStatusCode()).thenReturn(status);
    return error;
  }

  @SuppressWarnings("unchecked")
  private CosmosItemResponse<ReconciliationRangeDocument> response(
      ReconciliationRangeDocument document, String etag) {
    CosmosItemResponse<ReconciliationRangeDocument> response = mock(CosmosItemResponse.class);
    when(response.getItem()).thenReturn(document);
    when(response.getETag()).thenReturn(etag);
    return response;
  }

  private void conflictWith(ReconciliationRangeDocument existing) {
    var conflict = error(409);
    var persisted = response(existing, "persisted-etag");
    when(container.createItem(any(ReconciliationRangeDocument.class), any(PartitionKey.class),
        any(CosmosItemRequestOptions.class))).thenThrow(conflict);
    when(container.readItem(eq("summary"), any(PartitionKey.class),
        eq(ReconciliationRangeDocument.class))).thenReturn(persisted);
  }

  @Test
  void createsAtomicallyAndReturnsPersistedDocumentAndEtag() {
    var document = document();
    var persisted = response(document, "new-etag");
    when(container.createItem(any(ReconciliationRangeDocument.class), any(PartitionKey.class),
        any(CosmosItemRequestOptions.class))).thenReturn(persisted);
    var result = store.createOrRead("dev", "request-1", request);
    assertThat(result.document()).isSameAs(document);
    assertThat(result.etag()).isEqualTo("new-etag");
    var pk = ArgumentCaptor.forClass(PartitionKey.class);
    var options = ArgumentCaptor.forClass(CosmosItemRequestOptions.class);
    verify(container).createItem(eq(document), pk.capture(), options.capture());
    assertThat(pk.getValue().toString()).contains(document.executionId());
    assertThat(options.getValue().isContentResponseOnWriteEnabled()).isTrue();
    verifyNoMoreInteractions(container);
  }

  @Test
  void conflictPreservesProgressOwnershipDispatchAndTimestamps() {
    var existing = document().toBuilder().status("RUNNING").completedDays(10).remainingDays(20)
        .currentDay("2026-09-11").ownerId("owner-A").updatedAt("2026-11-25T09:05:00Z")
        .currentDispatch(Map.of("intentId", "existing-intent")).build();
    conflictWith(existing);
    var normalized = new ReconciliationRangeRequest(request.from(), request.to(),
        List.of(ServiceType.GPD, ServiceType.WISP));
    var result = store.createOrRead("dev", "request-1", normalized);
    assertThat(result.document()).isSameAs(existing);
    assertThat(result.etag()).isEqualTo("persisted-etag");
    verify(container).createItem(any(ReconciliationRangeDocument.class), any(PartitionKey.class),
        any(CosmosItemRequestOptions.class));
    verify(container).readItem(eq("summary"), any(PartitionKey.class),
        eq(ReconciliationRangeDocument.class));
    verifyNoMoreInteractions(container);
  }

  @Test
  void conflictRejectsChangedParameters() {
    conflictWith(document());
    var changed = List.of(
        new ReconciliationRangeRequest(request.from().plusDays(1), request.to(), request.serviceTypes()),
        new ReconciliationRangeRequest(request.from(), request.to().minusDays(1), request.serviceTypes()),
        new ReconciliationRangeRequest(request.from(), request.to(), List.of(ServiceType.GPD)));
    for (var other : changed) {
      assertThatThrownBy(() -> store.createOrRead("dev", "request-1", other))
          .isInstanceOfSatisfying(AppException.class,
              exception -> assertThat(exception.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT));
    }
  }

  @Test
  void conflictRejectsIncompatibleSchema() {
    conflictWith(document().toBuilder().schemaVersion(2).build());
    assertThatThrownBy(() -> store.createOrRead("dev", "request-1", request))
        .isInstanceOfSatisfying(AppException.class,
            exception -> assertThat(exception.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT));
  }

  @ParameterizedTest
  @ValueSource(ints = {401, 403, 404, 408, 429, 503})
  void createErrorsArePropagatedWithoutReadingOrRetrying(int status) {
    var error = error(status);
    when(container.createItem(any(ReconciliationRangeDocument.class), any(PartitionKey.class),
        any(CosmosItemRequestOptions.class))).thenThrow(error);
    assertThatThrownBy(() -> store.createOrRead("dev", "request-1", request)).isSameAs(error);
    verify(container).createItem(any(ReconciliationRangeDocument.class), any(PartitionKey.class),
        any(CosmosItemRequestOptions.class));
    verifyNoMoreInteractions(container);
  }

  @Test
  void getUsesPointReadAndReturnsEtag() {
    var document = document();
    var persisted = response(document, "etag");
    when(container.readItem(eq("summary"), any(PartitionKey.class),
        eq(ReconciliationRangeDocument.class))).thenReturn(persisted);
    assertThat(store.get(document.executionId()).document()).isSameAs(document);
    var pk = ArgumentCaptor.forClass(PartitionKey.class);
    verify(container).readItem(eq("summary"), pk.capture(), eq(ReconciliationRangeDocument.class));
    assertThat(pk.getValue().toString()).contains(document.executionId());
    verifyNoMoreInteractions(container);
  }

  @Test
  void missingSummaryIsNotRecreated() {
    var notFound = error(404);
    when(container.readItem(eq("summary"), any(PartitionKey.class),
        eq(ReconciliationRangeDocument.class))).thenThrow(notFound);
    var executionId = document().executionId();
    assertThatThrownBy(() -> store.get(executionId))
        .isInstanceOfSatisfying(AppException.class,
            exception -> assertThat(exception.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    verify(container).readItem(eq("summary"), any(PartitionKey.class), eq(ReconciliationRangeDocument.class));
    verifyNoMoreInteractions(container);
  }

  @Test
  void readOutageIsPropagated() {
    var error = error(503);
    when(container.readItem(eq("summary"), any(PartitionKey.class),
        eq(ReconciliationRangeDocument.class))).thenThrow(error);
    var executionId = document().executionId();
    assertThatThrownBy(() -> store.get(executionId)).isSameAs(error);
  }

  @Test
  void outageWhileReadingConflictDoesNotCauseAnotherCreate() {
    conflictWith(document());
    var error = error(503);
    when(container.readItem(eq("summary"), any(PartitionKey.class),
        eq(ReconciliationRangeDocument.class))).thenThrow(error);
    assertThatThrownBy(() -> store.createOrRead("dev", "request-1", request)).isSameAs(error);
    verify(container).createItem(any(ReconciliationRangeDocument.class), any(PartitionKey.class),
        any(CosmosItemRequestOptions.class));
  }

  @Test
  void missingEtagIsNotAcceptedAsUsableSnapshot() {
    var persisted = response(document(), null);
    when(container.readItem(eq("summary"), any(PartitionKey.class),
        eq(ReconciliationRangeDocument.class))).thenReturn(persisted);
    var executionId = document().executionId();
    assertThatThrownBy(() -> store.get(executionId)).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void invalidInputFailsBeforeAnyCosmosCall() {
    assertThatThrownBy(() -> store.createOrRead("dev", "", request)).isInstanceOf(AppException.class);
    assertThatThrownBy(() -> store.createOrRead("dev", "request-1", null)).isInstanceOf(AppException.class);
    assertThatThrownBy(() -> store.get("invalid")).isInstanceOf(AppException.class);
    verifyNoInteractions(container);
  }

  @Test
  void retryAfterLostCreateResponseReusesPersistedRange() {
    var existing = document().toBuilder().status("RUNNING").ownerId("other-owner").build();
    var unavailable = error(503);
    var conflict = error(409);
    var persisted = response(existing, "existing-etag");
    when(container.createItem(any(ReconciliationRangeDocument.class), any(PartitionKey.class),
        any(CosmosItemRequestOptions.class))).thenThrow(unavailable).thenThrow(conflict);
    when(container.readItem(eq("summary"), any(PartitionKey.class),
        eq(ReconciliationRangeDocument.class))).thenReturn(persisted);
    assertThatThrownBy(() -> store.createOrRead("dev", "request-1", request)).isSameAs(unavailable);
    assertThat(store.createOrRead("dev", "request-1", request).document()).isSameAs(existing);
    verify(container, times(2)).createItem(any(ReconciliationRangeDocument.class), any(PartitionKey.class),
        any(CosmosItemRequestOptions.class));
    verify(container).readItem(eq("summary"), any(PartitionKey.class), eq(ReconciliationRangeDocument.class));
    verifyNoMoreInteractions(container);
  }

  @Test
  void replaceRequiresMatchingEtagAndReturnsPersistedSnapshot() {
    var original = document();
    var current = new ReconciliationRangeSnapshot(original, "original-etag");
    var updated = original.toBuilder().status("RUNNING").build();
    var persisted = response(updated, "updated-etag");
    when(container.replaceItem(eq(updated), eq("summary"), any(PartitionKey.class),
        any(CosmosItemRequestOptions.class))).thenReturn(persisted);
    var result = store.replace(current, updated);
    var options = ArgumentCaptor.forClass(CosmosItemRequestOptions.class);
    var pk = ArgumentCaptor.forClass(PartitionKey.class);
    verify(container).replaceItem(eq(updated), eq("summary"), pk.capture(), options.capture());
    assertThat(options.getValue().getIfMatchETag()).isEqualTo("original-etag");
    assertThat(options.getValue().isContentResponseOnWriteEnabled()).isTrue();
    assertThat(pk.getValue().toString()).contains(original.executionId());
    assertThat(result.document()).isSameAs(updated);
    assertThat(result.etag()).isEqualTo("updated-etag");
    verifyNoMoreInteractions(container);
  }

  @Test
  void replaceCannotChangeImmutableParameters() {
    var current = new ReconciliationRangeSnapshot(document(), "etag");
    var updated = current.document().toBuilder().to("2026-10-01").build();
    assertThatThrownBy(() -> store.replace(current, updated)).isInstanceOf(AppException.class);
    verifyNoInteractions(container);
  }

  @ParameterizedTest
  @ValueSource(ints = {412, 503})
  void replaceErrorIsPropagatedWithoutReadOrRetry(int status) {
    var current = new ReconciliationRangeSnapshot(document(), "etag");
    var error = error(status);
    when(container.replaceItem(any(ReconciliationRangeDocument.class), eq("summary"),
        any(PartitionKey.class), any(CosmosItemRequestOptions.class))).thenThrow(error);
    var updated = current.document();
    assertThatThrownBy(() -> store.replace(current, updated)).isSameAs(error);
    verify(container).replaceItem(any(ReconciliationRangeDocument.class), eq("summary"),
        any(PartitionKey.class), any(CosmosItemRequestOptions.class));
    verifyNoMoreInteractions(container);
  }
}
