package it.gov.pagopa.gpd.technicalsupport.config;

import static it.gov.pagopa.gpd.technicalsupport.config.CosmosBeanNames.RECONCILIATION_BIZ_POSITIVE_EVENTS_CONTAINER;

import com.azure.core.credential.AzureKeyCredential;
import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosDatabase;
import com.azure.identity.DefaultAzureCredentialBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(
    prefix = "reconciliation.biz",
    name = "enabled",
    havingValue = "true")
public class ReconciliationBizCosmosConfig {

  private final ReconciliationBizCosmosProperties properties;

  @Bean
  CosmosClient bizCosmosClient() {
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
  CosmosDatabase bizCosmosDatabase(CosmosClient bizCosmosClient) {
    return bizCosmosClient.getDatabase(properties.getDatabaseName());
  }

  @Bean(RECONCILIATION_BIZ_POSITIVE_EVENTS_CONTAINER)
  CosmosContainer bizPositiveEventsContainer(CosmosDatabase bizCosmosDatabase) {
    return bizCosmosDatabase.getContainer(properties.getContainerName());
  }
}