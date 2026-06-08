package it.gov.pagopa.gpd.technicalsupport.service.reconciliation;

import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.ReconciliationRunCreationResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class PositionStatusReconciliationRunner {

  private final ReconciliationRunStore runStore;

  @Async
  public void run(ReconciliationRunCreationResult run) {
    ReconciliationCounters counters = ReconciliationCounters.empty();

    try {
      log.info(
          "Starting position status reconciliation run. logicalRunKey={}, executionId={}, day={}, serviceTypes={}",
          run.logicalRunKey(),
          run.executionId(),
          run.day(),
          run.serviceTypes());

      runStore.markRunning(run.logicalRunKey(), run.executionId());

      /*
       * TODO next steps:
       * 1. read candidate payment options from APD;
       * 2. lookup positive payment events on Biz+;
       * 3. call GPD-Core /pay only for automatically recoverable cases;
       * 4. write reports for RECOVERED, MANUAL_REQUIRED and TECHNICAL_FAILURE.
       */
      counters = processStub(run);

      runStore.markDone(run.logicalRunKey(), run.executionId(), counters);

      log.info(
          "Completed position status reconciliation run. logicalRunKey={}, executionId={}, counters={}",
          run.logicalRunKey(),
          run.executionId(),
          counters);

    } catch (Exception e) {
      runStore.markFailed(run.logicalRunKey(), run.executionId(), counters, e);

      log.error(
          "Failed position status reconciliation run. logicalRunKey={}, executionId={}",
          run.logicalRunKey(),
          run.executionId(),
          e);
    }
  }

  private ReconciliationCounters processStub(ReconciliationRunCreationResult run) {
    return ReconciliationCounters.empty();
  }
}