package it.gov.pagopa.gpd.technicalsupport.config.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ReconciliationPropertiesTest {

  @Test
  void defaultValues_shouldBeConsistent() {
    ReconciliationProperties properties = new ReconciliationProperties();

    assertThat(properties.getMaxProcessingWindowDays()).isEqualTo(7);
    assertThat(properties.getMinProcessingDelayDays()).isEqualTo(1);
    assertThat(properties.getReportPartitionBuckets()).isEqualTo(16);
  }
}