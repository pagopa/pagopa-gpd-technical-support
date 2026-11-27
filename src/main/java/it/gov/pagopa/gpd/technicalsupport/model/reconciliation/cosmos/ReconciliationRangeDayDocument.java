package it.gov.pagopa.gpd.technicalsupport.model.reconciliation.cosmos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.ReconciliationRangeDayCounters;
import lombok.Builder;

@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.ALWAYS)
public record ReconciliationRangeDayDocument(
    String id, String documentType, int schemaVersion, String executionId,
    String day, String serviceTypesKey, String status, DailyRunReference dailyRun,
    ReconciliationRangeDayCounters dailyCounters, String dispatchIntentId,
    String dailyCompletedAt, String createdAt, String updatedAt, String completedAt) {

  public record DailyRunReference(
      String container, String partitionKey, String id, String executionId, String etag) {}
}
