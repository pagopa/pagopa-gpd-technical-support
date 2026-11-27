package it.gov.pagopa.gpd.technicalsupport.service.reconciliation.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import it.gov.pagopa.gpd.technicalsupport.exception.ReconciliationDailyRunInvalidException;
import it.gov.pagopa.gpd.technicalsupport.model.gpd.ServiceType;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.ReconciliationRunStatus;
import java.math.BigInteger;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class ReconciliationDailyRunDocumentMapperTest {
  private static final String DAY = "2026-09-01";
  private static final String SERVICES = "GPD|WISP";
  private static final String KEY = DAY + "__" + SERVICES;
  private final ReconciliationDailyRunDocumentMapper mapper = new ReconciliationDailyRunDocumentMapper();

  private ObjectNode document() {
    var json = JsonNodeFactory.instance.objectNode();
    json.put("id", KEY).put("logicalRunKey", KEY).put("day", DAY)
        .put("serviceTypesKey", SERVICES).put("executionId", "daily-execution")
        .put("status", "DONE").put("completedAt", "2026-11-27T09:00:00Z")
        .put("createdAt", "2026-11-27T08:59:00+00:00")
        .put("updatedAt", "2026-11-27T09:00:00Z");
    json.putArray("serviceTypes").add("WISP").add("GPD").add("GPD");
    for (String field : List.of("scanned", "positiveEventsFound", "reconciliationCases", "recovered",
        "notRecovered", "manualRequired", "technicalFailures", "payExecuted", "payFailed")) {
      json.put(field, 0);
    }
    return json;
  }

  @ParameterizedTest
  @EnumSource(value = ReconciliationRunStatus.class, names = {"CREATED", "RUNNING", "DONE", "FAILED"})
  void persistedStatusesArePreservedWithoutMutatingRawDocument(ReconciliationRunStatus status) {
    var json = document().put("status", status.name());
    var before = json.deepCopy();
    var result = mapper.map(json, DAY, SERVICES, KEY);
    assertThat(result.status()).isEqualTo(status);
    assertThat(result.serviceTypes()).containsExactly(ServiceType.GPD, ServiceType.WISP);
    assertThat(result.executionId()).isEqualTo("daily-execution");
    assertThat(json).isEqualTo(before);
  }

  @ParameterizedTest
  @ValueSource(strings = {"scanned", "positiveEventsFound", "reconciliationCases", "recovered",
      "notRecovered", "manualRequired", "technicalFailures", "payExecuted", "payFailed"})
  void missingCounterIsNotSilentlyConvertedToZero(String field) {
    var json = document();
    json.remove(field);
    assertThatThrownBy(() -> mapper.map(json, DAY, SERVICES, KEY))
        .isInstanceOf(ReconciliationDailyRunInvalidException.class).hasMessageContaining(field);
  }

  @ParameterizedTest
  @ValueSource(strings = {"scanned", "positiveEventsFound", "reconciliationCases", "recovered",
      "notRecovered", "manualRequired", "technicalFailures", "payExecuted", "payFailed"})
  void negativeCountersAreRejected(String field) {
    var json = document().put(field, -1);
    assertThatThrownBy(() -> mapper.map(json, DAY, SERVICES, KEY))
        .isInstanceOf(ReconciliationDailyRunInvalidException.class).hasMessageContaining(field);
  }

  static Stream<JsonNode> invalidCounterValues() {
    var nodes = JsonNodeFactory.instance;
    return Stream.of(NullNode.instance, nodes.textNode("1"), nodes.booleanNode(true),
        nodes.numberNode(1.0), nodes.numberNode(1.5),
        nodes.numberNode(new BigInteger("9223372036854775808")));
  }

  @ParameterizedTest
  @MethodSource("invalidCounterValues")
  void nonIntegralOrOverflowingCountersAreRejected(JsonNode value) {
    var json = document();
    json.set("scanned", value);
    assertThatThrownBy(() -> mapper.map(json, DAY, SERVICES, KEY))
        .isInstanceOf(ReconciliationDailyRunInvalidException.class).hasMessageContaining("scanned");
  }

  @Test
  void fullLongRangeIsPreserved() {
    var json = document().put("scanned", Long.MAX_VALUE);
    assertThat(mapper.map(json, DAY, SERVICES, KEY).scanned()).isEqualTo(Long.MAX_VALUE);
  }

  @Test
  void mapsEachCounterToItsCorrespondingField() {
    var json = document().put("scanned", 100).put("positiveEventsFound", 90)
        .put("reconciliationCases", 80).put("recovered", 70)
        .put("notRecovered", 10).put("manualRequired", 9)
        .put("technicalFailures", 1).put("payExecuted", 69).put("payFailed", 2);
    var result = mapper.map(json, DAY, SERVICES, KEY);
    assertThat(result.scanned()).isEqualTo(100);
    assertThat(result.positiveEventsFound()).isEqualTo(90);
    assertThat(result.reconciliationCases()).isEqualTo(80);
    assertThat(result.recovered()).isEqualTo(70);
    assertThat(result.notRecovered()).isEqualTo(10);
    assertThat(result.manualRequired()).isEqualTo(9);
    assertThat(result.technicalFailures()).isEqualTo(1);
    assertThat(result.payExecuted()).isEqualTo(69);
    assertThat(result.payFailed()).isEqualTo(2);
  }

  @Test
  void technicalFailureCountersRemainVisibleForCheckpointEvaluation() {
    var json = document().put("technicalFailures", 1).put("payFailed", 1).put("notRecovered", 1);
    var result = mapper.map(json, DAY, SERVICES, KEY);
    assertThat(result.technicalFailures()).isEqualTo(1);
    assertThat(result.payFailed()).isEqualTo(1);
    assertThat(result.status()).isEqualTo(ReconciliationRunStatus.DONE);
  }

  @ParameterizedTest
  @ValueSource(strings = {"id", "logicalRunKey", "day", "serviceTypesKey", "executionId", "status"})
  void missingRequiredIdentityOrStatusIsRejected(String field) {
    var json = document();
    json.remove(field);
    assertThatThrownBy(() -> mapper.map(json, DAY, SERVICES, KEY))
        .isInstanceOf(ReconciliationDailyRunInvalidException.class).hasMessageContaining(field);
  }

  @ParameterizedTest
  @ValueSource(strings = {"id", "logicalRunKey", "day", "serviceTypesKey"})
  void unexpectedIdentityIsRejected(String field) {
    var json = document().put(field, "unexpected");
    assertThatThrownBy(() -> mapper.map(json, DAY, SERVICES, KEY))
        .isInstanceOf(ReconciliationDailyRunInvalidException.class).hasMessageContaining(field);
  }

  @ParameterizedTest
  @ValueSource(strings = {"SKIPPED", "UNKNOWN", "done", ""})
  void unsupportedStatusIsRejected(String status) {
    var json = document().put("status", status);
    assertThatThrownBy(() -> mapper.map(json, DAY, SERVICES, KEY))
        .isInstanceOf(ReconciliationDailyRunInvalidException.class);
  }

  @Test
  void invalidExecutionIdentityIsRejected() {
    var blank = document().put("executionId", " ");
    var numeric = document().put("executionId", 1);
    assertThatThrownBy(() -> mapper.map(blank, DAY, SERVICES, KEY))
        .isInstanceOf(ReconciliationDailyRunInvalidException.class);
    assertThatThrownBy(() -> mapper.map(numeric, DAY, SERVICES, KEY))
        .isInstanceOf(ReconciliationDailyRunInvalidException.class);
  }

  static Stream<JsonNode> invalidServices() {
    var nodes = JsonNodeFactory.instance;
    return Stream.of(NullNode.instance, nodes.textNode("GPD"), nodes.arrayNode(),
        nodes.arrayNode().add("UNKNOWN"), nodes.arrayNode().addNull(),
        nodes.arrayNode().add("GPD"));
  }

  @ParameterizedTest
  @MethodSource("invalidServices")
  void missingUnsupportedOrMismatchedServiceTypesAreRejected(JsonNode services) {
    var json = document();
    json.set("serviceTypes", services);
    assertThatThrownBy(() -> mapper.map(json, DAY, SERVICES, KEY))
        .isInstanceOf(ReconciliationDailyRunInvalidException.class);
  }

  @ParameterizedTest
  @ValueSource(strings = {"createdAt", "updatedAt", "startedAt", "completedAt"})
  void timestampsRequireAnOffset(String field) {
    var json = document().put(field, "2026-11-27T09:00:00");
    assertThatThrownBy(() -> mapper.map(json, DAY, SERVICES, KEY))
        .isInstanceOf(ReconciliationDailyRunInvalidException.class).hasMessageContaining(field);
  }

  @Test
  void doneRequiresCompletionTimeButCreatedRunMayHaveNullTimestamps() {
    var done = document();
    done.remove("completedAt");
    assertThatThrownBy(() -> mapper.map(done, DAY, SERVICES, KEY))
        .isInstanceOf(ReconciliationDailyRunInvalidException.class).hasMessageContaining("completedAt");
    var created = document().put("status", "CREATED");
    created.putNull("completedAt").putNull("startedAt");
    var result = mapper.map(created, DAY, SERVICES, KEY);
    assertThat(result.completedAt()).isNull();
    assertThat(result.startedAt()).isNull();
  }

  @Test
  void errorDetailsArePreservedAndUnknownCosmosFieldsAreIgnored() {
    var json = document().put("status", "FAILED")
        .put("errorCode", "DailyError").put("errorMessage", "Failure description")
        .put("_etag", "system-etag").put("_ts", 1795760400);
    var result = mapper.map(json, DAY, SERVICES, KEY);
    assertThat(result.errorCode()).isEqualTo("DailyError");
    assertThat(result.errorMessage()).isEqualTo("Failure description");
  }

  @Test
  void optionalMetadataMustNotBeCoercedToText() {
    var json = document().put("errorMessage", 123);
    assertThatThrownBy(() -> mapper.map(json, DAY, SERVICES, KEY))
        .isInstanceOf(ReconciliationDailyRunInvalidException.class).hasMessageContaining("errorMessage");
  }

  @Test
  void missingDocumentIsRejected() {
    assertThatThrownBy(() -> mapper.map(null, DAY, SERVICES, KEY))
        .isInstanceOf(ReconciliationDailyRunInvalidException.class);
  }
}
