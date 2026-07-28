package it.gov.pagopa.gpd.technicalsupport.service.reconciliation;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

import it.gov.pagopa.gpd.technicalsupport.model.gpd.ServiceType;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.ReconciliationRunCreationResult;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.ReconciliationRunStatus;
import it.gov.pagopa.gpd.technicalsupport.service.reconciliation.store.ReconciliationRunStore;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class PositionStatusReconciliationRunnerTest {

  private final ReconciliationRunStore runStore = mock(ReconciliationRunStore.class);

  private final PositionStatusReconciliationProcessor processor =
      mock(PositionStatusReconciliationProcessor.class);

  private final PositionStatusReconciliationRunner runner =
      new PositionStatusReconciliationRunner(runStore, processor);

  @Test
  void run_shouldMoveRunFromRunningToDone() {
    ReconciliationRunCreationResult run =
        new ReconciliationRunCreationResult(
            LocalDate.of(2026, 5, 20),
            List.of(ServiceType.GPD, ServiceType.WISP),
            "2026-05-20__GPD|WISP",
            "2026-05-20__GPD|WISP__20260526T100000Z",
            ReconciliationRunStatus.CREATED,
            true);

    ReconciliationCounters counters =
        new ReconciliationCounters(10, 1, 1, 1, 0, 0, 1, 0);

    when(processor.process(run)).thenReturn(counters);

    runner.run(run);

    InOrder inOrder = inOrder(runStore, processor);
    inOrder.verify(runStore).markRunning(run.logicalRunKey(), run.executionId());
    inOrder.verify(processor).process(run);
    inOrder.verify(runStore).markDone(run.logicalRunKey(), run.executionId(), counters);
  }
}