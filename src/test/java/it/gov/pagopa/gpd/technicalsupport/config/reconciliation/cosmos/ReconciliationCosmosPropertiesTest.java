package it.gov.pagopa.gpd.technicalsupport.config.reconciliation.cosmos;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ReconciliationCosmosPropertiesTest {

  @Test
  void defaultValues_shouldBeConsistent() {
    ReconciliationCosmosProperties properties = new ReconciliationCosmosProperties();

    assertThat(properties.isEnabled()).isTrue();
    assertThat(properties.getDatabaseName()).isEqualTo("gpd_db");
    assertThat(properties.getRunsContainerName()).isEqualTo("gpd-reconciliation-runs");
    assertThat(properties.getReportsContainerName()).isEqualTo("gpd-reconciliation-reports");
    assertThat(properties.isUseManagedIdentity()).isFalse();
  }
}