package it.gov.pagopa.gpd.technicalsupport.model.reconciliation;

import java.util.List;

public record PositionStatusReconciliationResponse(
    boolean accepted,
    List<ReconciliationRunResponse> runs) {}