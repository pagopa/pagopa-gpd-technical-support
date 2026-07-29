package it.gov.pagopa.gpd.technicalsupport.service.reconciliation.key;

import static org.assertj.core.api.Assertions.assertThat;

import it.gov.pagopa.gpd.technicalsupport.model.gpd.ServiceType;
import java.time.Month;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReconciliationKeyBuilderTest {

  private final ReconciliationKeyBuilder keyBuilder = new ReconciliationKeyBuilder();

  @Test
  void serviceTypesKey_shouldNormalizeOrder() {
    String key = keyBuilder.serviceTypesKey(List.of(ServiceType.WISP, ServiceType.GPD));

    assertThat(key).isEqualTo("GPD|WISP");
  }

  @Test
  void logicalRunKey_shouldUseDayAndNormalizedServiceTypes() {
    String key =
        keyBuilder.logicalRunKey(
            LocalDate.of(2026, Month.MAY, 20), List.of(ServiceType.WISP, ServiceType.GPD));

    assertThat(key).isEqualTo("2026-05-20__GPD|WISP");
  }

  @Test
  void executionId_shouldAppendExecutionTimestamp() {
    String executionId =
        keyBuilder.executionId(
            "2026-05-20__GPD|WISP",
            OffsetDateTime.of(2026, Month.MAY.getValue(), 26, 10, 0, 0, 0, ZoneOffset.UTC));

    assertThat(executionId).isEqualTo("2026-05-20__GPD|WISP__20260526T100000Z");
  }
}