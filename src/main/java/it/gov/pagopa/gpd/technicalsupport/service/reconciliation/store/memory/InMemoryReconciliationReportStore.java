package it.gov.pagopa.gpd.technicalsupport.service.reconciliation.store.memory;

import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.cosmos.ReconciliationReportDocument;
import it.gov.pagopa.gpd.technicalsupport.service.reconciliation.store.ReconciliationReportStore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(
    prefix = "reconciliation.cosmos",
    name = "enabled",
    havingValue = "false",
    matchIfMissing = true)
public class InMemoryReconciliationReportStore implements ReconciliationReportStore {

  private final List<ReconciliationReportDocument> reports =
      Collections.synchronizedList(new ArrayList<>());

  @Override
  public void save(ReconciliationReportDocument report) {
    reports.add(report);
  }

  public List<ReconciliationReportDocument> reports() {
    return List.copyOf(reports);
  }
}