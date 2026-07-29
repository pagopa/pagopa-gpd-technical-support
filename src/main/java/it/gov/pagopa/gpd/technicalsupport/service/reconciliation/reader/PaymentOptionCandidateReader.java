package it.gov.pagopa.gpd.technicalsupport.service.reconciliation.reader;

import it.gov.pagopa.gpd.technicalsupport.model.gpd.ServiceType;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.apd.ReconciliationCandidate;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Consumer;

public interface PaymentOptionCandidateReader {

  void forEachCandidateChunk(
      LocalDate day,
      List<ServiceType> serviceTypes,
      int chunkSize,
      Consumer<List<ReconciliationCandidate>> chunkConsumer);
}