package it.gov.pagopa.gpd.technicalsupport.service.reconciliation.key;

import static org.assertj.core.api.Assertions.assertThat;

import it.gov.pagopa.gpd.technicalsupport.config.reconciliation.ReconciliationProperties;
import it.gov.pagopa.gpd.technicalsupport.model.gpd.ServiceType;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ReconciliationReportKeyBuilderTest {

  private ReconciliationReportKeyBuilder keyBuilder;

  @BeforeEach
  void setUp() {
    ReconciliationProperties properties = new ReconciliationProperties();
    properties.setReportPartitionBuckets(16);

    keyBuilder = new ReconciliationReportKeyBuilder(properties);
  }

  @Test
  void ecNavKey_shouldJoinEcAndNav() {
    String ecNavKey = keyBuilder.ecNavKey("12345678901", "302012345678901234");

    assertThat(ecNavKey).isEqualTo("12345678901__302012345678901234");
  }

  @Test
  void bucket_shouldBeDeterministicAndWithinExpectedRange() {
    int firstBucket = keyBuilder.bucket("12345678901", "302012345678901234");
    int secondBucket = keyBuilder.bucket("12345678901", "302012345678901234");

    assertThat(firstBucket).isEqualTo(secondBucket);
    assertThat(firstBucket).isBetween(0, 15);
  }

  @Test
  void bucket_shouldRespectConfiguredBucketCount() {
    ReconciliationProperties properties = new ReconciliationProperties();
    properties.setReportPartitionBuckets(32);

    ReconciliationReportKeyBuilder builder = new ReconciliationReportKeyBuilder(properties);

    int bucket = builder.bucket("12345678901", "302012345678901234");

    assertThat(bucket).isBetween(0, 31);
  }

  @Test
  void partitionKey_shouldUseDayServiceTypeAndBucket() {
    String pk = keyBuilder.partitionKey(LocalDate.of(2026, 5, 20), ServiceType.GPD, 7);

    assertThat(pk).isEqualTo("2026-05-20__GPD__7");
  }

  @Test
  void reportId_shouldUseExecutionIdAndPaymentOptionId() {
    String id =
        keyBuilder.reportId(
            "2026-05-20__GPD|WISP__20260526T100000Z",
            "payment-option-id");

    assertThat(id).isEqualTo("2026-05-20__GPD|WISP__20260526T100000Z__payment-option-id");
  }
}