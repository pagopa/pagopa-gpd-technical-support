package it.gov.pagopa.gpd.technicalsupport.client.reconciliation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.gov.pagopa.gpd.technicalsupport.exception.ReconciliationDailySubmissionUnknownException;
import it.gov.pagopa.gpd.technicalsupport.model.gpd.ServiceType;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.ReconciliationRunResponse;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.ReconciliationRunStatus;
import it.gov.pagopa.gpd.technicalsupport.service.reconciliation.key.ReconciliationKeyBuilder;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class ReconciliationDailyApiResponseMapper {
  private final ObjectMapper mapper = new ObjectMapper();
  private final ReconciliationKeyBuilder keyBuilder = new ReconciliationKeyBuilder();

  ReconciliationRunResponse map(byte[] response, LocalDate day, List<ServiceType> services) {
    JsonNode body;
    try {
      body = mapper.readTree(response);
    } catch (IOException error) {
      throw new ReconciliationDailySubmissionUnknownException("Invalid API JSON; inspect Cosmos", error);
    }
    if (body == null || !body.isObject() || !body.path("accepted").isBoolean()
        || !body.path("accepted").booleanValue()) {
      throw new ReconciliationDailySubmissionUnknownException("Unverified API acceptance; inspect Cosmos");
    }
    var runs = body.path("runs");
    if (!runs.isArray() || runs.size() != 1 || !runs.get(0).isObject()) {
      throw new ReconciliationDailySubmissionUnknownException("Expected one daily API run; inspect Cosmos");
    }
    var run = runs.get(0);
    String key = keyBuilder.logicalRunKey(day, services);
    if (!day.toString().equals(text(run, "day")) || !key.equals(text(run, "logicalRunKey"))) {
      throw new ReconciliationDailySubmissionUnknownException("API run identity mismatch; inspect Cosmos");
    }
    if (!services.equals(returnedServices(run.path("serviceTypes")))) {
      throw new ReconciliationDailySubmissionUnknownException("API services mismatch; inspect Cosmos");
    }
    return new ReconciliationRunResponse(day, services, key,
        text(run, "executionId"), status(text(run, "status")));
  }

  private String text(JsonNode run, String field) {
    var value = run.path(field);
    if (!value.isTextual() || value.textValue().isBlank()) {
      throw new ReconciliationDailySubmissionUnknownException("Invalid API run field: " + field + "; inspect Cosmos");
    }
    return value.textValue();
  }

  private ReconciliationRunStatus status(String value) {
    try {
      return ReconciliationRunStatus.valueOf(value);
    } catch (IllegalArgumentException error) {
      throw new ReconciliationDailySubmissionUnknownException("Unknown API status; inspect Cosmos", error);
    }
  }

  private List<ServiceType> returnedServices(JsonNode values) {
    if (!values.isArray() || values.isEmpty()) {
      throw new ReconciliationDailySubmissionUnknownException("Invalid API services; inspect Cosmos");
    }
    var result = new ArrayList<ServiceType>();
    for (var value : values) {
      if (!value.isTextual()) {
        throw new ReconciliationDailySubmissionUnknownException("Invalid API service type; inspect Cosmos");
      }
      try {
        result.add(ServiceType.valueOf(value.textValue()));
      } catch (IllegalArgumentException error) {
        throw new ReconciliationDailySubmissionUnknownException("Unknown API service type; inspect Cosmos", error);
      }
    }
    return result.stream().distinct().sorted(Comparator.comparing(Enum::name)).toList();
  }
}
