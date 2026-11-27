package it.gov.pagopa.gpd.technicalsupport.model.reconciliation;

import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.cosmos.ReconciliationRangeDocument;

public record ReconciliationRangeSnapshot(ReconciliationRangeDocument document, String etag) {
  public ReconciliationRangeSnapshot {
    if (document == null || etag == null || etag.isBlank()) {
      throw new IllegalStateException("Cosmos range response must contain a document and ETag");
    }
  }
}
