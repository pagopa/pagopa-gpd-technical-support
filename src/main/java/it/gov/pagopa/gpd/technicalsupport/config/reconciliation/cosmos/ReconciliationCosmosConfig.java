package it.gov.pagopa.gpd.technicalsupport.config.reconciliation.cosmos;

import static it.gov.pagopa.gpd.technicalsupport.config.reconciliation.cosmos.CosmosBeanNames.RECONCILIATION_REPORTS_CONTAINER;
import static it.gov.pagopa.gpd.technicalsupport.config.reconciliation.cosmos.CosmosBeanNames.RECONCILIATION_RUNS_CONTAINER;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.azure.core.credential.AzureKeyCredential;
import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosDatabase;
import com.azure.identity.DefaultAzureCredentialBuilder;

import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(
    prefix = "reconciliation.cosmos",
    name = "enabled",
    havingValue = "true")
public class ReconciliationCosmosConfig {

  public static final String RECONCILIATION_COSMOS_CLIENT = "reconciliationCosmosClient";
  public static final String RECONCILIATION_COSMOS_DATABASE = "reconciliationCosmosDatabase";

  private final ReconciliationCosmosProperties properties;

  @Bean(RECONCILIATION_COSMOS_CLIENT)
  CosmosClient reconciliationCosmosClient() {
    CosmosClientBuilder builder =
        new CosmosClientBuilder()
            .endpoint(properties.getEndpoint())
            .gatewayMode();

    if (properties.isUseManagedIdentity()) {
      builder.credential(new DefaultAzureCredentialBuilder().build());
    } else {
      builder.credential(new AzureKeyCredential(properties.getKey()));
    }

    return builder.buildClient();
  }

  @Bean(RECONCILIATION_COSMOS_DATABASE)
  CosmosDatabase reconciliationCosmosDatabase(
      @Qualifier(RECONCILIATION_COSMOS_CLIENT) CosmosClient reconciliationCosmosClient) {
    return reconciliationCosmosClient.getDatabase(properties.getDatabaseName());
  }

  @Bean(RECONCILIATION_RUNS_CONTAINER)
  CosmosContainer reconciliationRunsContainer(
      @Qualifier(RECONCILIATION_COSMOS_DATABASE) CosmosDatabase reconciliationCosmosDatabase) {
    return reconciliationCosmosDatabase.getContainer(properties.getRunsContainerName());
  }

  @Bean(RECONCILIATION_REPORTS_CONTAINER)
  CosmosContainer reconciliationReportsContainer(
      @Qualifier(RECONCILIATION_COSMOS_DATABASE) CosmosDatabase reconciliationCosmosDatabase) {
    return reconciliationCosmosDatabase.getContainer(properties.getReportsContainerName());
  }
}