package it.gov.pagopa.gpd.technicalsupport.service.reconciliation;

import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.apd.ReconciliationCandidate;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.biz.BizPositiveEvent;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.gpd.GpdPayRecoveryResult;

public interface GpdPayClient {

  GpdPayRecoveryResult executePayRecovery(
      ReconciliationCandidate candidate,
      BizPositiveEvent event);
}