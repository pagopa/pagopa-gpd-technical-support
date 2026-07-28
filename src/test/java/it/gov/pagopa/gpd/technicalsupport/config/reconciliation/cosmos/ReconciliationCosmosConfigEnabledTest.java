package it.gov.pagopa.gpd.technicalsupport.config.reconciliation.cosmos;

import static it.gov.pagopa.gpd.technicalsupport.config.reconciliation.cosmos.CosmosBeanNames.RECONCILIATION_REPORTS_CONTAINER;
import static it.gov.pagopa.gpd.technicalsupport.config.reconciliation.cosmos.CosmosBeanNames.RECONCILIATION_RUNS_CONTAINER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosDatabase;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class ReconciliationCosmosConfigEnabledTest {

  @Test
  void containerBeans_shouldUseConfiguredContainerNames() {
    AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();

    ReconciliationCosmosProperties properties = new ReconciliationCosmosProperties();
    properties.setEnabled(true);
    properties.setDatabaseName("gpd_db");
    properties.setRunsContainerName("gpd-reconciliation-runs");
    properties.setReportsContainerName("gpd-reconciliation-reports");

    CosmosClient cosmosClient = mock(CosmosClient.class);
    CosmosDatabase cosmosDatabase = mock(CosmosDatabase.class);
    CosmosContainer runsContainer = mock(CosmosContainer.class);
    CosmosContainer reportsContainer = mock(CosmosContainer.class);

    when(cosmosClient.getDatabase("gpd_db")).thenReturn(cosmosDatabase);
    when(cosmosDatabase.getContainer("gpd-reconciliation-runs")).thenReturn(runsContainer);
    when(cosmosDatabase.getContainer("gpd-reconciliation-reports")).thenReturn(reportsContainer);

    context.registerBean(ReconciliationCosmosProperties.class, () -> properties);
    context.registerBean(CosmosClient.class, () -> cosmosClient);
    context.register(ReconciliationCosmosContainerTestConfig.class);
    context.refresh();

    assertThat(context.getBean(CosmosDatabase.class)).isSameAs(cosmosDatabase);
    assertThat(context.getBean(RECONCILIATION_RUNS_CONTAINER, CosmosContainer.class))
        .isSameAs(runsContainer);
    assertThat(context.getBean(RECONCILIATION_REPORTS_CONTAINER, CosmosContainer.class))
        .isSameAs(reportsContainer);

    context.close();
  }

  @Configuration
  static class ReconciliationCosmosContainerTestConfig {

    @Bean
    CosmosDatabase reconciliationCosmosDatabase(
        CosmosClient reconciliationCosmosClient,
        ReconciliationCosmosProperties properties) {
      return reconciliationCosmosClient.getDatabase(properties.getDatabaseName());
    }

    @Bean(RECONCILIATION_RUNS_CONTAINER)
    CosmosContainer reconciliationRunsContainer(
        CosmosDatabase reconciliationCosmosDatabase,
        ReconciliationCosmosProperties properties) {
      return reconciliationCosmosDatabase.getContainer(properties.getRunsContainerName());
    }

    @Bean(RECONCILIATION_REPORTS_CONTAINER)
    CosmosContainer reconciliationReportsContainer(
        CosmosDatabase reconciliationCosmosDatabase,
        ReconciliationCosmosProperties properties) {
      return reconciliationCosmosDatabase.getContainer(properties.getReportsContainerName());
    }
  }
}