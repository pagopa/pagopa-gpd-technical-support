package it.gov.pagopa.gpd.technicalsupport.service.reconciliation.store.cosmos;

import static it.gov.pagopa.gpd.technicalsupport.config.reconciliation.cosmos.CosmosBeanNames.RECONCILIATION_RANGE_RUNS_CONTAINER;

import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosException;
import com.azure.cosmos.models.CosmosItemRequestOptions;
import com.azure.cosmos.models.CosmosItemResponse;
import com.azure.cosmos.models.PartitionKey;
import it.gov.pagopa.gpd.technicalsupport.exception.AppError;
import it.gov.pagopa.gpd.technicalsupport.exception.AppException;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.ReconciliationRangeRequest;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.ReconciliationRangeSnapshot;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.cosmos.ReconciliationRangeDocument;
import it.gov.pagopa.gpd.technicalsupport.service.reconciliation.mapper.ReconciliationRangeDocumentFactory;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "reconciliation.cosmos", name = "enabled", havingValue = "true")
public class CosmosReconciliationRangeStore {
  private final CosmosContainer container;
  private final ReconciliationRangeDocumentFactory factory;

  public CosmosReconciliationRangeStore(
      @Qualifier(RECONCILIATION_RANGE_RUNS_CONTAINER) CosmosContainer container,
      ReconciliationRangeDocumentFactory factory) {
    this.container = container;
    this.factory = factory;
  }

  public ReconciliationRangeSnapshot createOrRead(
      String environment, String idempotencyKey, ReconciliationRangeRequest request) {
    var requested = factory.newCreated(environment, idempotencyKey, request);
    CosmosItemResponse<ReconciliationRangeDocument> response;
    try {
      response = container.createItem(requested, new PartitionKey(requested.executionId()),
          new CosmosItemRequestOptions().setContentResponseOnWriteEnabled(true));
    } catch (CosmosException error) {
      if (error.getStatusCode() != 409) {
        throw error;
      }
      var existing = get(requested.executionId());
      assertSameRequest(existing.document(), requested);
      return existing;
    }
    return snapshot(response);
  }

  public ReconciliationRangeSnapshot get(String executionId) {
    String canonicalId = canonicalId(executionId);
    try {
      return snapshot(container.readItem(
          "summary", new PartitionKey(canonicalId), ReconciliationRangeDocument.class));
    } catch (CosmosException error) {
      if (error.getStatusCode() == 404) {
        throw new AppException(HttpStatus.NOT_FOUND, "Not Found", "Range execution not found");
      }
      throw error;
    }
  }

  private String canonicalId(String value) {
    try {
      return UUID.fromString(value).toString();
    } catch (IllegalArgumentException | NullPointerException error) {
      throw new AppException(AppError.BAD_REQUEST, "executionId must be a UUID");
    }
  }

  private ReconciliationRangeSnapshot snapshot(
      CosmosItemResponse<ReconciliationRangeDocument> response) {
    return new ReconciliationRangeSnapshot(response.getItem(), response.getETag());
  }

  private void assertSameRequest(
      ReconciliationRangeDocument existing, ReconciliationRangeDocument requested) {
    boolean same = Objects.equals(existing.id(), requested.id())
        && Objects.equals(existing.documentType(), requested.documentType())
        && existing.schemaVersion() == requested.schemaVersion()
        && Objects.equals(existing.executionId(), requested.executionId())
        && Objects.equals(existing.environment(), requested.environment())
        && Objects.equals(existing.from(), requested.from())
        && Objects.equals(existing.to(), requested.to())
        && Objects.equals(existing.serviceTypes(), requested.serviceTypes())
        && Objects.equals(existing.serviceTypesKey(), requested.serviceTypesKey());
    if (!same) {
      throw new AppException(HttpStatus.CONFLICT, "Conflict",
          "Existing range has incompatible schema or parameters for this Idempotency-Key");
    }
  }
}
