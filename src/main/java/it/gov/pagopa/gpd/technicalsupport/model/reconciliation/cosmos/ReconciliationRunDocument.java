package it.gov.pagopa.gpd.technicalsupport.model.reconciliation.cosmos;

import it.gov.pagopa.gpd.technicalsupport.model.gpd.ServiceType;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.ReconciliationRunStatus;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.Builder;

@Builder(toBuilder = true)
public record ReconciliationRunDocument(
    String id,
    String day, // pk field: day as yyyy-MM-dd format
    List<ServiceType> serviceTypes,
    String serviceTypesKey,
    String logicalRunKey,
    String executionId,
    ReconciliationRunStatus status,
    long scanned,
    long positiveEventsFound,
    long reconciliationCases,
    long recovered,
    long notRecovered,
    long manualRequired,
    long technicalFailures,
    long payExecuted,
    long payFailed,
    String errorCode,
    String errorMessage,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt,
    OffsetDateTime startedAt,
    OffsetDateTime completedAt) {}