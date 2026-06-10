package it.gov.pagopa.gpd.technicalsupport.service.reconciliation;

import static it.gov.pagopa.gpd.technicalsupport.config.CosmosBeanNames.RECONCILIATION_REPORTS_CONTAINER;

import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.models.CosmosItemRequestOptions;
import com.azure.cosmos.models.PartitionKey;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.cosmos.ReconciliationReportDocument;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@ConditionalOnProperty(
    prefix = "reconciliation.cosmos",
    name = "enabled",
    havingValue = "true")
public class CosmosReconciliationReportStore implements ReconciliationReportStore {

  private final CosmosContainer reportsContainer;

  public CosmosReconciliationReportStore(
      @Qualifier(RECONCILIATION_REPORTS_CONTAINER) CosmosContainer reportsContainer) {
    this.reportsContainer = reportsContainer;
  }

  @Override
  public void save(ReconciliationReportDocument report) {
    log.info(
        "Upserting reconciliation report document on Cosmos. id={}, pk={}, executionId={}, status={}, outcome={}",
        report.id(),
        report.pk(),
        report.executionId(),
        report.reconciliationStatus(),
        report.outcome());

    reportsContainer.upsertItem(
        report,
        new PartitionKey(report.pk()),
        new CosmosItemRequestOptions());
  }
}