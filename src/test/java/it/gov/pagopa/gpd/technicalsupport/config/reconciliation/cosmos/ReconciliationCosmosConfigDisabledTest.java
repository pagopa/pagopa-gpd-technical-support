package it.gov.pagopa.gpd.technicalsupport.config.reconciliation.cosmos;

import static org.assertj.core.api.Assertions.assertThat;

import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosDatabase;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ReconciliationCosmosConfigDisabledTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withBean(ReconciliationCosmosProperties.class, ReconciliationCosmosProperties::new)
          .withUserConfiguration(ReconciliationCosmosConfig.class);

  @Test
  void cosmosBeans_shouldNotBeCreatedWhenCosmosIsDisabled() {
    contextRunner
        .withPropertyValues("reconciliation.cosmos.enabled=false")
        .run(
            context ->
                assertThat(context)
                    .doesNotHaveBean(CosmosClient.class)
                    .doesNotHaveBean(CosmosDatabase.class)
                    .doesNotHaveBean(CosmosContainer.class));
  }

  @Test
  void cosmosBeans_shouldNotBeCreatedWhenCosmosPropertyIsMissing() {
    contextRunner.run(
        context ->
            assertThat(context)
                .doesNotHaveBean(CosmosClient.class)
                .doesNotHaveBean(CosmosDatabase.class)
                .doesNotHaveBean(CosmosContainer.class));
  }
}