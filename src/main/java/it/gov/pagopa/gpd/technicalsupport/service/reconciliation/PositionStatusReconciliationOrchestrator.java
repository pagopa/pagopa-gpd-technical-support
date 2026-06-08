package it.gov.pagopa.gpd.technicalsupport.service.reconciliation;

import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.PositionStatusReconciliationRequest;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.PositionStatusReconciliationResponse;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.ReconciliationRunCreationResult;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.ReconciliationRunResponse;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PositionStatusReconciliationOrchestrator {

  private final PositionStatusReconciliationValidator validator;
  private final ReconciliationRunStore runStore;
  private final PositionStatusReconciliationRunner runner;

  public PositionStatusReconciliationResponse start(PositionStatusReconciliationRequest request) {
    validator.validate(request);

    List<ReconciliationRunResponse> runs = new ArrayList<>();
    LocalDate day = request.from();

    while (!day.isAfter(request.to())) {
      ReconciliationRunCreationResult result =
          runStore.createOrEvaluateRun(day, request.serviceTypes(), request.force());

      runs.add(
          new ReconciliationRunResponse(
              result.day(),
              result.serviceTypes(),
              result.logicalRunKey(),
              result.executionId(),
              result.status()));

      if (result.shouldStart()) {
        runner.run(result);
      }

      day = day.plusDays(1);
    }

    return new PositionStatusReconciliationResponse(true, runs);
  }
}