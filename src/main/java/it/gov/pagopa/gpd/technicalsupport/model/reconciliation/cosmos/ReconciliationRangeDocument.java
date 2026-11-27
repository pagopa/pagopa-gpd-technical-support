package it.gov.pagopa.gpd.technicalsupport.model.reconciliation.cosmos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;
import lombok.Builder;

@Builder(toBuilder = true)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.ALWAYS)
public record ReconciliationRangeDocument(
    String id,
    String documentType,
    int schemaVersion,
    String executionId,
    String environment,
    String from,
    String to,
    List<String> serviceTypes,
    String serviceTypesKey,
    String status,
    String currentDay,
    Map<String, Object> currentDispatch,
    int totalDays,
    int completedDays,
    int remainingDays,
    String ownerId,
    String leaseExpiresAt,
    String heartbeatAt,
    String createdAt,
    String updatedAt,
    String completedAt,
    Map<String, Object> lastError) {}
