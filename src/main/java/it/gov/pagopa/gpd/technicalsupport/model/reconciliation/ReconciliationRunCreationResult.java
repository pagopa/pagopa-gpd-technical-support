package it.gov.pagopa.gpd.technicalsupport.model.reconciliation;

import it.gov.pagopa.gpd.technicalsupport.model.gpd.ServiceType;
import java.time.LocalDate;
import java.util.List;

public record ReconciliationRunCreationResult(
    LocalDate day,
    List<ServiceType> serviceTypes,
    String logicalRunKey,
    String executionId,
    ReconciliationRunStatus status,
    boolean shouldStart) {}