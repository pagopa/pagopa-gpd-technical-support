package it.gov.pagopa.gpd.technicalsupport.service.reconciliation;

import static it.gov.pagopa.gpd.technicalsupport.config.CosmosBeanNames.RECONCILIATION_RUNS_CONTAINER;

import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.models.CosmosItemRequestOptions;
import com.azure.cosmos.models.PartitionKey;
import it.gov.pagopa.gpd.technicalsupport.model.gpd.ServiceType;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.ReconciliationRunCreationResult;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.ReconciliationRunStatus;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.cosmos.ReconciliationRunDocument;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
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
public class CosmosReconciliationRunStore implements ReconciliationRunStore {

  private final CosmosContainer runsContainer;
  private final ReconciliationKeyBuilder keyBuilder;
  private final ReconciliationRunDocumentMapper mapper;
  private final Clock clock;

  public CosmosReconciliationRunStore(
      @Qualifier(RECONCILIATION_RUNS_CONTAINER) CosmosContainer runsContainer,
      ReconciliationKeyBuilder keyBuilder,
      ReconciliationRunDocumentMapper mapper,
      Clock clock) {
    this.runsContainer = runsContainer;
    this.keyBuilder = keyBuilder;
    this.mapper = mapper;
    this.clock = clock;
  }

  @Override
  public ReconciliationRunCreationResult createOrEvaluateRun(
      LocalDate day,
      List<ServiceType> serviceTypes,
      boolean force) {

    String logicalRunKey = keyBuilder.logicalRunKey(day, serviceTypes);
    PartitionKey partitionKey = new PartitionKey(day.toString());

    ReconciliationRunDocument existingRun = readRunIfExists(logicalRunKey, partitionKey);

    if (existingRun != null && existingRun.status() == ReconciliationRunStatus.RUNNING && !force) {
      return toCreationResult(existingRun, false);
    }

    if (existingRun != null && existingRun.status() == ReconciliationRunStatus.DONE && !force) {
    	return new ReconciliationRunCreationResult(
    		    LocalDate.parse(existingRun.day()),
    		    existingRun.serviceTypes(),
    		    existingRun.logicalRunKey(),
    		    existingRun.executionId(),
    		    ReconciliationRunStatus.SKIPPED,
    		    false);
    }

    OffsetDateTime now = nowUtc();

    String executionId = keyBuilder.executionId(logicalRunKey, now);

    ReconciliationRunDocument document =
        mapper.newCreatedRun(day, serviceTypes, executionId, now);
    
    log.info(
    	    "Upserting reconciliation run document on Cosmos. id={}, partitionKey={}, executionId={}, status={}",
    	    document.id(),
    	    day,
    	    document.executionId(),
    	    document.status());

    runsContainer.upsertItem(document, partitionKey, new CosmosItemRequestOptions());

    return toCreationResult(document, true);
  }

  @Override
  public void markRunning(String logicalRunKey, String executionId) {
    updateStatus(logicalRunKey, executionId, RunUpdate.RUNNING, ReconciliationCounters.empty(), null);
  }

  @Override
  public void markDone(
      String logicalRunKey, String executionId, ReconciliationCounters counters) {
    updateStatus(logicalRunKey, executionId, RunUpdate.DONE, counters, null);
  }

  @Override
  public void markFailed(
      String logicalRunKey,
      String executionId,
      ReconciliationCounters counters,
      Throwable error) {
    updateStatus(logicalRunKey, executionId, RunUpdate.FAILED, counters, error);
  }

  private void updateStatus(
      String logicalRunKey,
      String executionId,
      RunUpdate update,
      ReconciliationCounters counters,
      Throwable error) {

    LocalDate day = extractDayFromLogicalRunKey(logicalRunKey);
    PartitionKey partitionKey = new PartitionKey(day.toString());

    ReconciliationRunDocument existingRun = readRunIfExists(logicalRunKey, partitionKey);

    if (existingRun == null) {
      log.warn(
          "Unable to update reconciliation run because it does not exist. logicalRunKey={}, executionId={}, update={}",
          logicalRunKey,
          executionId,
          update);
      return;
    }

    if (!existingRun.executionId().equals(executionId)) {
      log.warn(
          "Skipping reconciliation run update because executionId does not match. logicalRunKey={}, expectedExecutionId={}, actualExecutionId={}, update={}",
          logicalRunKey,
          existingRun.executionId(),
          executionId,
          update);
      return;
    }

    OffsetDateTime now = nowUtc();

    ReconciliationRunDocument updatedRun =
        switch (update) {
          case RUNNING -> mapper.running(existingRun, now);
          case DONE -> mapper.done(existingRun, counters, now);
          case FAILED -> mapper.failed(existingRun, counters, error, now);
        };
        
    log.info(
		   "Updating reconciliation run document on Cosmos. id={}, partitionKey={}, executionId={}, status={}",
            updatedRun.id(),
        	day,
        	updatedRun.executionId(),
        	updatedRun.status());

    runsContainer.upsertItem(updatedRun, partitionKey, new CosmosItemRequestOptions());
  }

  private ReconciliationRunDocument readRunIfExists(
      String logicalRunKey, PartitionKey partitionKey) {
    try {
      return runsContainer
          .readItem(logicalRunKey, partitionKey, ReconciliationRunDocument.class)
          .getItem();
    } catch (Exception e) {
      if (isNotFound(e)) {
        return null;
      }
      throw e;
    }
  }

  private boolean isNotFound(Exception e) {
    return e instanceof com.azure.cosmos.CosmosException cosmosException
        && cosmosException.getStatusCode() == 404;
  }

  private OffsetDateTime nowUtc() {
    return OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC);
  }

  private LocalDate extractDayFromLogicalRunKey(String logicalRunKey) {
	  return LocalDate.parse(
			  logicalRunKey.substring(0, logicalRunKey.indexOf(ReconciliationKeyBuilder.RUN_KEY_SEPARATOR)));
  }

  private ReconciliationRunCreationResult toCreationResult(ReconciliationRunDocument document, boolean shouldStart) {
	  return new ReconciliationRunCreationResult(
			  LocalDate.parse(document.day()),
			  document.serviceTypes(),
			  document.logicalRunKey(),
			  document.executionId(),
			  document.status(),
			  shouldStart);
  }

  private enum RunUpdate {
    RUNNING,
    DONE,
    FAILED
  }
}