package it.gov.pagopa.gpd.technicalsupport.service.reconciliation.store.cosmos;

import static it.gov.pagopa.gpd.technicalsupport.config.reconciliation.cosmos.CosmosBeanNames.RECONCILIATION_RANGE_RUNS_CONTAINER;

import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosException;
import com.azure.cosmos.models.CosmosBatch;
import com.azure.cosmos.models.CosmosBatchItemRequestOptions;
import com.azure.cosmos.models.CosmosBatchResponse;
import com.azure.cosmos.models.PartitionKey;
import it.gov.pagopa.gpd.technicalsupport.exception.ReconciliationRangeCheckpointConflictException;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.ReconciliationRangeSnapshot;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.cosmos.ReconciliationRangeDayDocument;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.cosmos.ReconciliationRangeDocument;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "reconciliation.cosmos", name = "enabled", havingValue = "true")
public class CosmosReconciliationRangeCheckpointStore {
  private static final String CONFLICT_MESSAGE = "Reload persisted summary and checkpoint before continuing";
  private final CosmosContainer container;
  private final CosmosReconciliationRangeStore rangeStore;

  public CosmosReconciliationRangeCheckpointStore(
      @Qualifier(RECONCILIATION_RANGE_RUNS_CONTAINER) CosmosContainer container,
      CosmosReconciliationRangeStore rangeStore) {
    this.container = container;
    this.rangeStore = rangeStore;
  }

  public ReconciliationRangeSnapshot commit(
      ReconciliationRangeSnapshot current, ReconciliationRangeDayDocument checkpoint,
      ReconciliationRangeDocument updated) {
    String executionId = current.document().executionId();
    if (!executionId.equals(checkpoint.executionId()) || !executionId.equals(updated.executionId())
        || !"summary".equals(updated.id())) {
      throw new IllegalArgumentException("Checkpoint and summary must belong to the same range partition");
    }
    var batch = CosmosBatch.createCosmosBatch(new PartitionKey(executionId));
    batch.createItemOperation(checkpoint);
    batch.replaceItemOperation("summary", updated,
        new CosmosBatchItemRequestOptions().setIfMatchETag(current.etag()));
    CosmosBatchResponse response;
    try {
      response = container.executeCosmosBatch(batch);
    } catch (CosmosException error) {
      if (isConflict(error.getStatusCode())) {
        throw new ReconciliationRangeCheckpointConflictException(CONFLICT_MESSAGE, error);
      }
      throw error;
    }
    assertSuccessful(response);
    // A read failure here does not imply rollback: reload persisted progress before any retry.
    return rangeStore.get(executionId);
  }

  private void assertSuccessful(CosmosBatchResponse response) {
    var results = response.getResults();
    boolean conflict = isConflict(response.getStatusCode())
        || results.stream().anyMatch(result -> isConflict(result.getStatusCode()));
    if (conflict) {
      throw new ReconciliationRangeCheckpointConflictException(CONFLICT_MESSAGE);
    }
    boolean successful = response.isSuccessStatusCode() && results.size() == 2
        && results.stream().allMatch(result -> result.getStatusCode() >= 200 && result.getStatusCode() < 300);
    if (!successful) {
      throw new IllegalStateException("Cosmos checkpoint batch failed; status=" + response.getStatusCode());
    }
  }

  private boolean isConflict(int status) {
    return status == 409 || status == 412;
  }
}
