package it.gov.pagopa.gpd.technicalsupport.service.reconciliation.reader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosException;
import com.azure.cosmos.models.CosmosItemResponse;
import com.azure.cosmos.models.PartitionKey;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import it.gov.pagopa.gpd.technicalsupport.exception.ReconciliationDailyRunChangedException;
import it.gov.pagopa.gpd.technicalsupport.exception.ReconciliationDailyRunInvalidException;
import it.gov.pagopa.gpd.technicalsupport.model.gpd.ServiceType;
import it.gov.pagopa.gpd.technicalsupport.service.reconciliation.key.ReconciliationKeyBuilder;
import it.gov.pagopa.gpd.technicalsupport.service.reconciliation.mapper.ReconciliationDailyRunDocumentMapper;
import java.time.LocalDate;
import java.time.Month;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

class CosmosReconciliationDailyRunReaderTest {
  private static final LocalDate DAY = LocalDate.of(2026, Month.SEPTEMBER, 1);
  private static final List<ServiceType> SERVICES = List.of(ServiceType.GPD);
  private static final String KEY = "2026-09-01__GPD";
  private static final String EXECUTION = "2026-09-01__GPD__20261127T090000Z";
  private CosmosContainer container;
  private CosmosReconciliationDailyRunReader reader;

  @BeforeEach
  void setUp() {
    container = mock(CosmosContainer.class);
    reader = new CosmosReconciliationDailyRunReader(container,
        new ReconciliationKeyBuilder(), new ReconciliationDailyRunDocumentMapper());
  }

  private ObjectNode document() {
    var json = JsonNodeFactory.instance.objectNode();
    json.put("id", KEY).put("logicalRunKey", KEY).put("day", DAY.toString())
        .put("serviceTypesKey", "GPD").put("executionId", EXECUTION)
        .put("status", "DONE").put("completedAt", "2026-11-27T09:00:00Z");
    json.putArray("serviceTypes").add("GPD");
    for (String field : List.of("scanned", "positiveEventsFound", "reconciliationCases", "recovered",
        "notRecovered", "manualRequired", "technicalFailures", "payExecuted", "payFailed")) {
      json.put(field, 0);
    }
    return json;
  }

  @SuppressWarnings("unchecked")
  private void respond(ObjectNode body, String etag) {
    CosmosItemResponse<ObjectNode> response = mock(CosmosItemResponse.class);
    when(response.getItem()).thenReturn(body);
    when(response.getETag()).thenReturn(etag);
    when(container.readItem(eq(KEY), any(PartitionKey.class), eq(ObjectNode.class))).thenReturn(response);
  }

  private CosmosException cosmosError(int status) {
    var error = mock(CosmosException.class);
    when(error.getStatusCode()).thenReturn(status);
    return error;
  }

  private void failRead(CosmosException failure) {
    when(container.readItem(eq(KEY), any(PartitionKey.class), eq(ObjectNode.class))).thenThrow(failure);
  }

  @Test
  void usesOnePointReadAndReturnsTheResponseEtag() {
    var raw = document();
    respond(raw, "daily-etag");
    var result = reader.read(DAY, SERVICES).orElseThrow();
    assertThat(result.etag()).isEqualTo("daily-etag");
    assertThat(result.document().executionId()).isEqualTo(EXECUTION);
    var partition = ArgumentCaptor.forClass(PartitionKey.class);
    verify(container).readItem(eq(KEY), partition.capture(), eq(ObjectNode.class));
    assertThat(partition.getValue()).isEqualTo(new PartitionKey(DAY.toString()));
    verifyNoMoreInteractions(container);
  }

  @Test
  void unorderedAndDuplicateServicesUseTheCanonicalLogicalKey() {
    var raw = document();
    String key = "2026-09-01__GPD|WISP";
    raw.put("id", key).put("logicalRunKey", key).put("serviceTypesKey", "GPD|WISP");
    raw.putArray("serviceTypes").add("GPD").add("WISP");
    @SuppressWarnings("unchecked")
    CosmosItemResponse<ObjectNode> response = mock(CosmosItemResponse.class);
    when(response.getItem()).thenReturn(raw);
    when(response.getETag()).thenReturn("etag");
    when(container.readItem(eq(key), any(PartitionKey.class), eq(ObjectNode.class))).thenReturn(response);
    var result = reader.read(DAY, List.of(ServiceType.WISP, ServiceType.GPD, ServiceType.GPD));
    assertThat(result.orElseThrow().document().serviceTypesKey()).isEqualTo("GPD|WISP");
    verify(container).readItem(eq(key), any(PartitionKey.class), eq(ObjectNode.class));
    verifyNoMoreInteractions(container);
  }

  @Test
  void onlyInitialNotFoundReturnsEmpty() {
    var missing = cosmosError(404);
    failRead(missing);
    assertThat(reader.read(DAY, SERVICES)).isEmpty();
    verify(container).readItem(eq(KEY), any(PartitionKey.class), eq(ObjectNode.class));
    verifyNoMoreInteractions(container);
  }

  @Test
  void missingPreviouslyObservedRunIsAnAnomaly() {
    var missing = cosmosError(404);
    failRead(missing);
    assertThatThrownBy(() -> reader.read(DAY, SERVICES, EXECUTION))
        .isInstanceOf(ReconciliationDailyRunChangedException.class).hasCause(missing);
  }

  @ParameterizedTest
  @ValueSource(ints = {401, 403, 408, 410, 429, 500, 503})
  void cosmosErrorsAreNeverInterpretedAsAbsenceAndAreNotRetried(int status) {
    var failure = cosmosError(status);
    failRead(failure);
    assertThatThrownBy(() -> reader.read(DAY, SERVICES)).isSameAs(failure);
    verify(container).readItem(eq(KEY), any(PartitionKey.class), eq(ObjectNode.class));
    verifyNoMoreInteractions(container);
  }

  @Test
  void expectedExecutionIdIsVerified() {
    respond(document(), "etag");
    assertThat(reader.read(DAY, SERVICES, EXECUTION)).isPresent();
    assertThatThrownBy(() -> reader.read(DAY, SERVICES, "another-execution"))
        .isInstanceOf(ReconciliationDailyRunChangedException.class);
  }

  @Test
  void invalidDocumentsAreRejectedWithoutAnyWrite() {
    var malformed = document();
    malformed.remove("scanned");
    respond(malformed, "etag");
    assertThatThrownBy(() -> reader.read(DAY, SERVICES))
        .isInstanceOf(ReconciliationDailyRunInvalidException.class);
    verify(container).readItem(eq(KEY), any(PartitionKey.class), eq(ObjectNode.class));
    verifyNoMoreInteractions(container);
  }

  @Test
  void nullDocumentAndMissingEtagAreRejected() {
    respond(null, "etag");
    assertThatThrownBy(() -> reader.read(DAY, SERVICES))
        .isInstanceOf(ReconciliationDailyRunInvalidException.class);
    respond(document(), null);
    assertThatThrownBy(() -> reader.read(DAY, SERVICES))
        .isInstanceOf(ReconciliationDailyRunInvalidException.class);
    respond(document(), " ");
    assertThatThrownBy(() -> reader.read(DAY, SERVICES))
        .isInstanceOf(ReconciliationDailyRunInvalidException.class);
  }

  @Test
  void invalidArgumentsFailBeforeCosmosRead() {
    var nullElement = Arrays.asList(ServiceType.GPD, null);
    var empty = List.<ServiceType>of();
    var yearZero = LocalDate.of(0, Month.JANUARY, 1);
    var yearTooLarge = LocalDate.of(10000, Month.JANUARY, 1);
    assertThatThrownBy(() -> reader.read(null, SERVICES)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> reader.read(yearZero, SERVICES)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> reader.read(yearTooLarge, SERVICES)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> reader.read(DAY, null)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> reader.read(DAY, empty)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> reader.read(DAY, nullElement)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> reader.read(DAY, SERVICES, " ")).isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(container);
  }
}
