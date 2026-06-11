package it.gov.pagopa.gpd.technicalsupport.service.reconciliation.store;

import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.cosmos.ReconciliationReportDocument;

public interface ReconciliationReportStore {

  void save(ReconciliationReportDocument report);
}