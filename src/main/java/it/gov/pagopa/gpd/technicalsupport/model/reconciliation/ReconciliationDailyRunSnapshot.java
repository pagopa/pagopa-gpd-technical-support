package it.gov.pagopa.gpd.technicalsupport.model.reconciliation;

import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.cosmos.ReconciliationRunDocument;

public record ReconciliationDailyRunSnapshot(ReconciliationRunDocument document, String etag) {
  public ReconciliationDailyRunSnapshot {
    if (document == null || etag == null || etag.isBlank()) {
      throw new IllegalArgumentException("Daily run snapshot requires a document and ETag");
    }
  }
}
