package it.gov.pagopa.gpd.technicalsupport.model.reconciliation;

import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.cosmos.ReconciliationRunDocument;
import java.util.stream.LongStream;

public record ReconciliationRangeDayCounters(
    long scanned, long positiveEventsFound, long reconciliationCases, long recovered,
    long notRecovered, long manualRequired, long technicalFailures, long payExecuted, long payFailed) {

  public ReconciliationRangeDayCounters {
    if (LongStream.of(scanned, positiveEventsFound, reconciliationCases, recovered,
        notRecovered, manualRequired, technicalFailures, payExecuted, payFailed)
        .anyMatch(value -> value < 0)) {
      throw new IllegalArgumentException("Daily counters must be nonnegative");
    }
    if (technicalFailures != 0 || payFailed != 0) {
      throw new IllegalArgumentException("Daily technical failures block advancement");
    }
    if (notRecovered != manualRequired) {
      throw new IllegalArgumentException("Inconsistent notRecovered counter");
    }
  }

  public static ReconciliationRangeDayCounters from(ReconciliationRunDocument run) {
    return new ReconciliationRangeDayCounters(run.scanned(), run.positiveEventsFound(),
        run.reconciliationCases(), run.recovered(), run.notRecovered(), run.manualRequired(),
        run.technicalFailures(), run.payExecuted(), run.payFailed());
  }
}
