package it.gov.pagopa.gpd.technicalsupport.service.reconciliation;

public record ReconciliationCounters(
    long scanned,
    long positiveEventsFound,
    long reconciliationCases,
    long recovered,
    long manualRequired,
    long technicalFailures,
    long payExecuted,
    long payFailed) {

  public static ReconciliationCounters empty() {
    return new ReconciliationCounters(0, 0, 0, 0, 0, 0, 0, 0);
  }

  public long notRecovered() {
    return manualRequired + technicalFailures;
  }
}