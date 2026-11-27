package it.gov.pagopa.gpd.technicalsupport.service.reconciliation.mapper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import it.gov.pagopa.gpd.technicalsupport.exception.ReconciliationDailyRunInvalidException;
import it.gov.pagopa.gpd.technicalsupport.model.gpd.ServiceType;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.ReconciliationRunStatus;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.cosmos.ReconciliationRunDocument;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Validates raw Cosmos fields before mapping them into primitive Java counters. */
@Component
public class ReconciliationDailyRunDocumentMapper {
  public ReconciliationRunDocument map(
      ObjectNode json, String day, String servicesKey, String logicalKey) {
    if (json == null) {
      throw new ReconciliationDailyRunInvalidException("Missing daily run document");
    }
    expect(json, "id", logicalKey);
    expect(json, "logicalRunKey", logicalKey);
    expect(json, "day", day);
    expect(json, "serviceTypesKey", servicesKey);
    var services = services(json.path("serviceTypes"));
    String canonical = services.stream().map(Enum::name).collect(Collectors.joining("|"));
    if (!canonical.equals(servicesKey)) {
      throw new ReconciliationDailyRunInvalidException("Daily service types do not match the requested services");
    }
    var status = status(requiredText(json, "status"));
    String completedAt = timestamp(json, "completedAt");
    if (status == ReconciliationRunStatus.DONE && completedAt == null) {
      throw new ReconciliationDailyRunInvalidException("DONE daily run is missing completedAt");
    }
    return ReconciliationRunDocument.builder()
        .id(logicalKey).logicalRunKey(logicalKey).day(day)
        .serviceTypes(services).serviceTypesKey(servicesKey)
        .executionId(requiredText(json, "executionId")).status(status)
        .scanned(counter(json, "scanned"))
        .positiveEventsFound(counter(json, "positiveEventsFound"))
        .reconciliationCases(counter(json, "reconciliationCases"))
        .recovered(counter(json, "recovered"))
        .notRecovered(counter(json, "notRecovered"))
        .manualRequired(counter(json, "manualRequired"))
        .technicalFailures(counter(json, "technicalFailures"))
        .payExecuted(counter(json, "payExecuted"))
        .payFailed(counter(json, "payFailed"))
        .createdAt(timestamp(json, "createdAt")).updatedAt(timestamp(json, "updatedAt"))
        .startedAt(timestamp(json, "startedAt")).completedAt(completedAt)
        .errorCode(optionalText(json, "errorCode")).errorMessage(optionalText(json, "errorMessage"))
        .build();
  }

  private void expect(ObjectNode json, String field, String expected) {
    if (!expected.equals(requiredText(json, field))) {
      throw new ReconciliationDailyRunInvalidException("Unexpected daily run identity field: " + field);
    }
  }

  private String requiredText(ObjectNode json, String field) {
    var value = json.path(field);
    if (!value.isTextual() || value.textValue().isBlank()) {
      throw new ReconciliationDailyRunInvalidException("Missing or invalid daily field: " + field);
    }
    return value.textValue();
  }

  private String optionalText(ObjectNode json, String field) {
    var value = json.path(field);
    if (value.isMissingNode() || value.isNull()) {
      return null;
    }
    if (!value.isTextual()) {
      throw new ReconciliationDailyRunInvalidException("Invalid daily text field: " + field);
    }
    return value.textValue();
  }

  private String timestamp(ObjectNode json, String field) {
    String value = optionalText(json, field);
    if (value != null) {
      try {
        OffsetDateTime.parse(value);
      } catch (DateTimeParseException error) {
        throw new ReconciliationDailyRunInvalidException("Invalid daily timestamp: " + field, error);
      }
    }
    return value;
  }

  private long counter(ObjectNode json, String field) {
    var value = json.path(field);
    if (!value.isIntegralNumber() || !value.canConvertToLong()) {
      throw new ReconciliationDailyRunInvalidException("Missing or invalid daily counter: " + field);
    }
    long result = value.longValue();
    if (result < 0) {
      throw new ReconciliationDailyRunInvalidException("Negative daily counter: " + field);
    }
    return result;
  }

  private ReconciliationRunStatus status(String value) {
    ReconciliationRunStatus status;
    try {
      status = ReconciliationRunStatus.valueOf(value);
    } catch (IllegalArgumentException error) {
      throw new ReconciliationDailyRunInvalidException("Unknown persisted daily status", error);
    }
    if (status == ReconciliationRunStatus.SKIPPED) {
      throw new ReconciliationDailyRunInvalidException("SKIPPED is an API outcome, not a persisted daily status");
    }
    return status;
  }

  private List<ServiceType> services(JsonNode values) {
    if (!values.isArray() || values.isEmpty()) {
      throw new ReconciliationDailyRunInvalidException("Missing daily service types");
    }
    var services = new ArrayList<ServiceType>();
    for (var value : values) {
      if (!value.isTextual()) {
        throw new ReconciliationDailyRunInvalidException("Invalid daily service type");
      }
      try {
        services.add(ServiceType.valueOf(value.textValue()));
      } catch (IllegalArgumentException error) {
        throw new ReconciliationDailyRunInvalidException("Unknown daily service type", error);
      }
    }
    return services.stream().distinct().sorted(Comparator.comparing(Enum::name)).toList();
  }
}
