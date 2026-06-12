package it.gov.pagopa.gpd.technicalsupport.config.reconciliation;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "reconciliation")
public class ReconciliationProperties {

  private int maxProcessingWindowDays = 7;
  private int minProcessingDelayDays = 1;
  private int reportPartitionBuckets = 16;
  private int candidateChunkSize = 500;
}