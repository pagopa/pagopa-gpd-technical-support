package it.gov.pagopa.gpd.technicalsupport.service.reconciliation.reader;

import static it.gov.pagopa.gpd.technicalsupport.config.reconciliation.cosmos.CosmosBeanNames.RECONCILIATION_RUNS_CONTAINER;

import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosException;
import com.azure.cosmos.models.CosmosItemResponse;
import com.azure.cosmos.models.PartitionKey;
import com.fasterxml.jackson.databind.node.ObjectNode;
import it.gov.pagopa.gpd.technicalsupport.exception.ReconciliationDailyRunChangedException;
import it.gov.pagopa.gpd.technicalsupport.exception.ReconciliationDailyRunInvalidException;
import it.gov.pagopa.gpd.technicalsupport.model.gpd.ServiceType;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.ReconciliationDailyRunSnapshot;
import it.gov.pagopa.gpd.technicalsupport.service.reconciliation.key.ReconciliationKeyBuilder;
import it.gov.pagopa.gpd.technicalsupport.service.reconciliation.mapper.ReconciliationDailyRunDocumentMapper;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "reconciliation.cosmos", name = "enabled", havingValue = "true")
public class CosmosReconciliationDailyRunReader {
  private final CosmosContainer container;
  private final ReconciliationKeyBuilder keyBuilder;
  private final ReconciliationDailyRunDocumentMapper mapper;

  public CosmosReconciliationDailyRunReader(
      @Qualifier(RECONCILIATION_RUNS_CONTAINER) CosmosContainer container,
      ReconciliationKeyBuilder keyBuilder, ReconciliationDailyRunDocumentMapper mapper) {
    this.container = container;
    this.keyBuilder = keyBuilder;
    this.mapper = mapper;
  }

  public Optional<ReconciliationDailyRunSnapshot> read(LocalDate day, List<ServiceType> services) {
    return read(day, services, null);
  }

  public Optional<ReconciliationDailyRunSnapshot> read(
      LocalDate day, List<ServiceType> services, String expectedExecutionId) {
    validateRequest(day, services, expectedExecutionId);
    String servicesKey = keyBuilder.serviceTypesKey(services);
    String logicalKey = keyBuilder.logicalRunKey(day, services);
    CosmosItemResponse<ObjectNode> response;
    try {
      response = container.readItem(logicalKey, new PartitionKey(day.toString()), ObjectNode.class);
    } catch (CosmosException error) {
      if (error.getStatusCode() != 404) {
        throw error;
      }
      if (expectedExecutionId != null) {
        throw new ReconciliationDailyRunChangedException("Previously observed daily run is now missing", error);
      }
      return Optional.empty();
    }
    var document = mapper.map(response.getItem(), day.toString(), servicesKey, logicalKey);
    if (expectedExecutionId != null && !expectedExecutionId.equals(document.executionId())) {
      throw new ReconciliationDailyRunChangedException("Daily executionId changed; stop and investigate");
    }
    String etag = response.getETag();
    if (etag == null || etag.isBlank()) {
      throw new ReconciliationDailyRunInvalidException("Missing daily response ETag");
    }
    return Optional.of(new ReconciliationDailyRunSnapshot(document, etag));
  }

  private void validateRequest(LocalDate day, List<ServiceType> services, String expectedExecutionId) {
    if (day == null || day.getYear() < 1 || day.getYear() > 9999) {
      throw new IllegalArgumentException("Day must have a year between 0001 and 9999");
    }
    if (services == null || services.isEmpty() || services.stream().anyMatch(Objects::isNull)) {
      throw new IllegalArgumentException("At least one non-null service type is required");
    }
    if (expectedExecutionId != null && expectedExecutionId.isBlank()) {
      throw new IllegalArgumentException("Expected executionId must not be blank");
    }
  }
}
