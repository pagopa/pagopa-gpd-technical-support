package it.gov.pagopa.gpd.technicalsupport.client.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import it.gov.pagopa.gpd.technicalsupport.exception.ReconciliationDailySubmissionUnknownException;
import it.gov.pagopa.gpd.technicalsupport.model.gpd.ServiceType;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.ReconciliationRunStatus;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.Month;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class ReconciliationDailyApiResponseMapperTest {
  private static final LocalDate DAY = LocalDate.of(2026, Month.SEPTEMBER, 1);
  private static final List<ServiceType> SERVICES = List.of(ServiceType.GPD, ServiceType.WISP);
  private final ReconciliationDailyApiResponseMapper mapper = new ReconciliationDailyApiResponseMapper();

  private ObjectNode body() {
    var result = JsonNodeFactory.instance.objectNode().put("accepted", true);
    var run = result.putArray("runs").addObject();
    run.put("day", DAY.toString()).put("logicalRunKey", "2026-09-01__GPD|WISP")
        .put("executionId", "daily-execution").put("status", "CREATED");
    run.putArray("serviceTypes").add("WISP").add("GPD").add("GPD");
    return result;
  }

  private byte[] bytes(JsonNode json) {
    return json.toString().getBytes(StandardCharsets.UTF_8);
  }

  private void assertUnknown(ObjectNode json) {
    byte[] response = bytes(json);
    assertThatThrownBy(() -> mapper.map(response, DAY, SERVICES))
        .isInstanceOf(ReconciliationDailySubmissionUnknownException.class);
  }

  @ParameterizedTest
  @EnumSource(ReconciliationRunStatus.class)
  void knownApiStatusesAreAcceptedIncludingSkipped(ReconciliationRunStatus status) {
    var json = body();
    ((ObjectNode) json.path("runs").get(0)).put("status", status.name());
    var result = mapper.map(bytes(json), DAY, SERVICES);
    assertThat(result.status()).isEqualTo(status);
    assertThat(result.serviceTypes()).containsExactly(ServiceType.GPD, ServiceType.WISP);
    assertThat(result.executionId()).isEqualTo("daily-execution");
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "{", "null", "[]", "true"})
  void invalidJsonOrRootIsUnknown(String value) {
    byte[] response = value.getBytes(StandardCharsets.UTF_8);
    assertThatThrownBy(() -> mapper.map(response, DAY, SERVICES))
        .isInstanceOf(ReconciliationDailySubmissionUnknownException.class);
  }

  static Stream<JsonNode> invalidAcceptance() {
    var nodes = JsonNodeFactory.instance;
    return Stream.of(nodes.booleanNode(false), nodes.textNode("true"), nodes.numberNode(1), NullNode.instance);
  }

  @ParameterizedTest
  @MethodSource("invalidAcceptance")
  void acceptanceMustBeBooleanTrue(JsonNode value) {
    var json = body();
    json.set("accepted", value);
    assertUnknown(json);
  }

  @Test
  void missingAcceptanceOrInvalidRunCountIsUnknown() {
    var missing = body();
    missing.remove("accepted");
    assertUnknown(missing);
    var empty = body();
    empty.putArray("runs");
    assertUnknown(empty);
    var multiple = body();
    var runs = multiple.putArray("runs");
    runs.addObject();
    runs.addObject();
    assertUnknown(multiple);
    var invalid = body();
    invalid.putArray("runs").add("not-an-object");
    assertUnknown(invalid);
    var noRuns = body();
    noRuns.remove("runs");
    assertUnknown(noRuns);
  }

  @ParameterizedTest
  @ValueSource(strings = {"day", "logicalRunKey"})
  void mismatchedRunIdentityIsUnknown(String field) {
    var json = body();
    ((ObjectNode) json.path("runs").get(0)).put(field, "unexpected");
    assertUnknown(json);
  }

  @ParameterizedTest
  @ValueSource(strings = {"day", "logicalRunKey", "executionId", "status"})
  void missingRequiredRunFieldsAreUnknown(String field) {
    var json = body();
    ((ObjectNode) json.path("runs").get(0)).remove(field);
    assertUnknown(json);
  }

  @ParameterizedTest
  @ValueSource(strings = {"UNKNOWN", "created", " "})
  void unsupportedStatusIsUnknown(String status) {
    var json = body();
    ((ObjectNode) json.path("runs").get(0)).put("status", status);
    assertUnknown(json);
  }

  static Stream<JsonNode> invalidServices() {
    var nodes = JsonNodeFactory.instance;
    return Stream.of(NullNode.instance, nodes.textNode("GPD"), nodes.arrayNode(),
        nodes.arrayNode().add("GPD"), nodes.arrayNode().addNull(), nodes.arrayNode().add("UNKNOWN"));
  }

  @ParameterizedTest
  @MethodSource("invalidServices")
  void invalidReturnedServicesAreUnknown(JsonNode services) {
    var json = body();
    ((ObjectNode) json.path("runs").get(0)).set("serviceTypes", services);
    assertUnknown(json);
  }

  @Test
  void blankOrNumericExecutionIdIsUnknown() {
    var blank = body();
    ((ObjectNode) blank.path("runs").get(0)).put("executionId", " ");
    assertUnknown(blank);
    var numeric = body();
    ((ObjectNode) numeric.path("runs").get(0)).put("executionId", 12);
    assertUnknown(numeric);
  }
}
