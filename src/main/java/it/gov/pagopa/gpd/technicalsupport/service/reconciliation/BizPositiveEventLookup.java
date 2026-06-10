package it.gov.pagopa.gpd.technicalsupport.service.reconciliation;

import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.apd.ReconciliationCandidate;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.biz.BizPositiveEventLookupResult;

public interface BizPositiveEventLookup {

  BizPositiveEventLookupResult findPositiveEvent(ReconciliationCandidate candidate);
}