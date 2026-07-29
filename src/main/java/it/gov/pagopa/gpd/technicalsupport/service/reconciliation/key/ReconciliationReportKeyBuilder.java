package it.gov.pagopa.gpd.technicalsupport.service.reconciliation.key;

import it.gov.pagopa.gpd.technicalsupport.config.reconciliation.ReconciliationProperties;
import it.gov.pagopa.gpd.technicalsupport.model.gpd.ServiceType;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.zip.CRC32;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReconciliationReportKeyBuilder {

  private final ReconciliationProperties properties;

  public String ecNavKey(String ec, String nav) {
    return ec + ReconciliationKeyBuilder.RUN_KEY_SEPARATOR + nav;
  }

  public int bucket(String ec, String nav) {
    CRC32 crc32 = new CRC32();
    crc32.update(ecNavKey(ec, nav).getBytes(StandardCharsets.UTF_8));
    return Math.toIntExact(crc32.getValue() % properties.getReportPartitionBuckets());
  }

  public String partitionKey(LocalDate day, ServiceType serviceType, int bucket) {
    return day
        + ReconciliationKeyBuilder.RUN_KEY_SEPARATOR
        + serviceType.name()
        + ReconciliationKeyBuilder.RUN_KEY_SEPARATOR
        + bucket;
  }

  public String reportId(String executionId, String paymentOptionId) {
    return executionId + ReconciliationKeyBuilder.RUN_KEY_SEPARATOR + paymentOptionId;
  }
}