package it.gov.pagopa.gpd.technicalsupport.service.reconciliation.store;

import it.gov.pagopa.gpd.technicalsupport.model.gpd.ServiceType;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.ReconciliationRunCreationResult;
import it.gov.pagopa.gpd.technicalsupport.service.reconciliation.ReconciliationCounters;

import java.time.LocalDate;
import java.util.List;

public interface ReconciliationRunStore {

  ReconciliationRunCreationResult createOrEvaluateRun(
      LocalDate day,
      List<ServiceType> serviceTypes,
      boolean force);

  void markRunning(String logicalRunKey, String executionId);

  void markDone(String logicalRunKey, String executionId, ReconciliationCounters counters);

  void markFailed(
      String logicalRunKey,
      String executionId,
      ReconciliationCounters counters,
      Throwable error);
}