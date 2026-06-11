package it.gov.pagopa.gpd.technicalsupport.config.reconciliation.biz;

import static it.gov.pagopa.gpd.technicalsupport.config.reconciliation.cosmos.CosmosBeanNames.RECONCILIATION_BIZ_POSITIVE_EVENTS_CONTAINER;

import com.azure.core.credential.AzureKeyCredential;
import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosDatabase;
import com.azure.identity.DefaultAzureCredentialBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class ReconciliationBizCosmosConfig {

  public static final String BIZ_COSMOS_CLIENT = "bizCosmosClient";
  public static final String BIZ_COSMOS_DATABASE = "bizCosmosDatabase";

  private final ReconciliationBizCosmosProperties properties;

  @Bean(BIZ_COSMOS_CLIENT)
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

  @Bean(BIZ_COSMOS_DATABASE)
  CosmosDatabase bizCosmosDatabase(
      @Qualifier(BIZ_COSMOS_CLIENT) CosmosClient bizCosmosClient) {
    return bizCosmosClient.getDatabase(properties.getDatabaseName());
  }

  @Bean(RECONCILIATION_BIZ_POSITIVE_EVENTS_CONTAINER)
  CosmosContainer bizPositiveEventsContainer(
      @Qualifier(BIZ_COSMOS_DATABASE) CosmosDatabase bizCosmosDatabase) {
    return bizCosmosDatabase.getContainer(properties.getContainerName());
  }
}