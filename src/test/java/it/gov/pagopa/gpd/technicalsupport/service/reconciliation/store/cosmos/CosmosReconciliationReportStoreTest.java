package it.gov.pagopa.gpd.technicalsupport.service.reconciliation.store.cosmos;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.models.CosmosItemRequestOptions;
import com.azure.cosmos.models.PartitionKey;
import it.gov.pagopa.gpd.technicalsupport.model.gpd.ServiceType;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.ReconciliationOutcome;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.ReconciliationStatus;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.cosmos.ReconciliationReportDocument;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class CosmosReconciliationReportStoreTest {

  private final CosmosContainer reportsContainer = Mockito.mock(CosmosContainer.class);

  private final CosmosReconciliationReportStore store =
      new CosmosReconciliationReportStore(reportsContainer);

  @Test
  void save_shouldUpsertReportUsingPkAsPartitionKey() {
    ReconciliationReportDocument report =
        ReconciliationReportDocument.builder()
            .id("report-id")
            .pk("2026-05-20__GPD__7")
            .executionId("execution-id")
            .logicalRunKey("logical-run-key")
            .day("2026-05-20")
            .serviceType(ServiceType.GPD)
            .bucket(7)
            .paymentOptionId("payment-option-id")
            .ec("12345678901")
            .nav("302012345678901234")
            .reconciliationStatus(ReconciliationStatus.RECOVERED)
            .outcome(ReconciliationOutcome.POSITIVE_EVENT_FOUND_PAY_EXECUTED)
            .build();

    store.save(report);

    verify(reportsContainer)
        .upsertItem(
            Mockito.eq(report),
            any(PartitionKey.class),
            any(CosmosItemRequestOptions.class));
  }
}