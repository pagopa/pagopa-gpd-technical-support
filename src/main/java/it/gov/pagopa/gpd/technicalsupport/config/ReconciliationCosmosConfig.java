package it.gov.pagopa.gpd.technicalsupport.config;

import static it.gov.pagopa.gpd.technicalsupport.config.CosmosBeanNames.RECONCILIATION_REPORTS_CONTAINER;
import static it.gov.pagopa.gpd.technicalsupport.config.CosmosBeanNames.RECONCILIATION_RUNS_CONTAINER;

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

  private final ReconciliationCosmosProperties properties;

  @Bean
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

  @Bean
  CosmosDatabase reconciliationCosmosDatabase(CosmosClient reconciliationCosmosClient) {
    return reconciliationCosmosClient.getDatabase(properties.getDatabaseName());
  }

  @Bean(RECONCILIATION_RUNS_CONTAINER)
  CosmosContainer reconciliationRunsContainer(
      CosmosDatabase reconciliationCosmosDatabase) {
    return reconciliationCosmosDatabase.getContainer(properties.getRunsContainerName());
  }

  @Bean(RECONCILIATION_REPORTS_CONTAINER)
  CosmosContainer reconciliationReportsContainer(
      CosmosDatabase reconciliationCosmosDatabase) {
    return reconciliationCosmosDatabase.getContainer(properties.getReportsContainerName());
  }
}