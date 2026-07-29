package it.gov.pagopa.gpd.technicalsupport.model.reconciliation;

import it.gov.pagopa.gpd.technicalsupport.model.gpd.ServiceType;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;

public record PositionStatusReconciliationRequest(
    @NotNull LocalDate from,
    @NotNull LocalDate to,
    @NotEmpty List<ServiceType> serviceTypes,
    boolean force) {}