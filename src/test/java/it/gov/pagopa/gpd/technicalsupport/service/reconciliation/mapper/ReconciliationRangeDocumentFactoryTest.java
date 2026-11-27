package it.gov.pagopa.gpd.technicalsupport.service.reconciliation.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import it.gov.pagopa.gpd.technicalsupport.config.reconciliation.ReconciliationProperties;
import it.gov.pagopa.gpd.technicalsupport.exception.AppException;
import it.gov.pagopa.gpd.technicalsupport.model.gpd.ServiceType;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.ReconciliationRangeRequest;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.cosmos.ReconciliationRangeDocument;
import it.gov.pagopa.gpd.technicalsupport.service.reconciliation.ReconciliationRangeIdentity;
import it.gov.pagopa.gpd.technicalsupport.service.reconciliation.ReconciliationRangeValidator;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ReconciliationRangeDocumentFactoryTest {
  private ReconciliationRangeDocumentFactory factory;

  @BeforeEach
  void setUp() {
    Clock clock = Clock.fixed(Instant.parse("2026-11-25T09:00:00Z"), ZoneOffset.UTC);
    factory = new ReconciliationRangeDocumentFactory(new ReconciliationRangeIdentity(),
        new ReconciliationRangeValidator(new ReconciliationProperties(), clock), clock);
  }

  private ReconciliationRangeRequest request() {
    return new ReconciliationRangeRequest(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30),
        List.of(ServiceType.WISP, ServiceType.GPD, ServiceType.GPD));
  }

  @Test
  void createsInclusiveMonthWithCanonicalServicesAndInitialProgress() {
    var document = factory.newCreated("dev", "request-1", request());
    assertThat(document.id()).isEqualTo("summary");
    assertThat(document.documentType()).isEqualTo("RANGE");
    assertThat(document.schemaVersion()).isEqualTo(1);
    assertThat(document.serviceTypes()).containsExactly("GPD", "WISP");
    assertThat(document.serviceTypesKey()).isEqualTo("GPD|WISP");
    assertThat(document.totalDays()).isEqualTo(30);
    assertThat(document.completedDays()).isZero();
    assertThat(document.remainingDays()).isEqualTo(30);
    assertThat(document.currentDay()).isEqualTo("2026-09-01");
    assertThat(document.status()).isEqualTo("CREATED");
    assertThat(document.createdAt()).isEqualTo("2026-11-25T09:00:00Z");
    assertThat(document.updatedAt()).isEqualTo(document.createdAt());
  }

  @Test
  void sameDateRepresentsOneDay() {
    var request = new ReconciliationRangeRequest(LocalDate.of(2026, 9, 1),
        LocalDate.of(2026, 9, 1), List.of(ServiceType.GPD));
    assertThat(factory.newCreated("dev", "request-1", request).totalDays()).isEqualTo(1);
  }

  @Test
  void rejectsMissingFieldsBeforeBuildingDocument() {
    var day = LocalDate.of(2026, 9, 1);
    var invalid = Arrays.asList(null,
        new ReconciliationRangeRequest(null, day, List.of(ServiceType.GPD)),
        new ReconciliationRangeRequest(day, null, List.of(ServiceType.GPD)),
        new ReconciliationRangeRequest(day, day, null),
        new ReconciliationRangeRequest(day, day, List.of()),
        new ReconciliationRangeRequest(day, day, Arrays.asList(ServiceType.GPD, null)));
    for (var request : invalid) {
      assertThatThrownBy(() -> factory.newCreated("dev", "request-1", request))
          .isInstanceOf(AppException.class);
    }
  }

  @Test
  void validatesDatesBeforeBuildingDocument() {
    var today = LocalDate.of(2026, 11, 25);
    var request = new ReconciliationRangeRequest(today, today, List.of(ServiceType.GPD));
    assertThatThrownBy(() -> factory.newCreated("dev", "request-1", request))
        .isInstanceOf(AppException.class).hasMessageContaining("too recent");
  }

  @Test
  void validatesIdempotencyKeyBeforeBuildingDocument() {
    var request = request();
    assertThatThrownBy(() -> factory.newCreated("dev", "", request))
        .isInstanceOf(AppException.class);
  }

  @Test
  void jsonRetainsPythonSchemaAndIgnoresCosmosSystemFieldsOnRead() throws Exception {
    ObjectMapper mapper = new ObjectMapper();
    var original = factory.newCreated("dev", "request-1", request());
    ObjectNode json = mapper.valueToTree(original);
    assertThat(json.size()).isEqualTo(22);
    for (String field : List.of("ownerId", "leaseExpiresAt", "heartbeatAt",
        "currentDispatch", "completedAt", "lastError")) {
      assertThat(json.has(field)).isTrue();
      assertThat(json.get(field).isNull()).isTrue();
    }
    assertThat(json.get("from").asText()).isEqualTo("2026-09-01");
    assertThat(json.get("serviceTypes").get(0).asText()).isEqualTo("GPD");
    json.put("_etag", "server-etag");
    json.put("_ts", 1795597200);
    assertThat(mapper.treeToValue(json, ReconciliationRangeDocument.class)).isEqualTo(original);
  }
}
