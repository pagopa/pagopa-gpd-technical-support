package it.gov.pagopa.gpd.technicalsupport.config.reconciliation.apd;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ReconciliationApdPropertiesTest {

  @Test
  void defaultValues_shouldBeConsistent() {
    ReconciliationApdProperties properties = new ReconciliationApdProperties();

    assertThat(properties.getMaximumPoolSize()).isEqualTo(5);
    assertThat(properties.getMinimumIdle()).isEqualTo(1);
    assertThat(properties.getConnectionTimeoutMs()).isEqualTo(30000);
  }
}