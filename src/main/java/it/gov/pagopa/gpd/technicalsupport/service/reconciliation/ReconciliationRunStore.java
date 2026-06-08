package it.gov.pagopa.gpd.technicalsupport.service.reconciliation;

import it.gov.pagopa.gpd.technicalsupport.model.gpd.ServiceType;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.ReconciliationRunCreationResult;
import java.time.LocalDate;
import java.util.List;

public interface ReconciliationRunStore {

  ReconciliationRunCreationResult createOrEvaluateRun(
      LocalDate day,
      List<ServiceType> serviceTypes,
      boolean force);
}