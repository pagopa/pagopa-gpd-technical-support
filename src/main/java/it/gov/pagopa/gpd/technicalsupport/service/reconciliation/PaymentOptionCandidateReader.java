package it.gov.pagopa.gpd.technicalsupport.service.reconciliation;

import it.gov.pagopa.gpd.technicalsupport.model.gpd.ServiceType;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.apd.ReconciliationCandidate;
import java.time.LocalDate;
import java.util.List;

public interface PaymentOptionCandidateReader {

  List<ReconciliationCandidate> findCandidates(LocalDate day, List<ServiceType> serviceTypes);
}