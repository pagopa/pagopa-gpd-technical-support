package it.gov.pagopa.gpd.technicalsupport.model.reconciliation;

import it.gov.pagopa.gpd.technicalsupport.model.gpd.ServiceType;
import java.time.LocalDate;
import java.util.List;

public record ReconciliationRunResponse(
    LocalDate day,
    List<ServiceType> serviceTypes,
    String logicalRunKey,
    String executionId,
    ReconciliationRunStatus status) {}