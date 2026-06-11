package it.gov.pagopa.gpd.technicalsupport.config.reconciliation.cosmos;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "reconciliation.cosmos")
public class ReconciliationCosmosProperties {

  private boolean enabled = true;
  private String endpoint;
  private String key;
  private String databaseName = "gpd_db";
  private String runsContainerName = "gpd-reconciliation-runs";
  private String reportsContainerName = "gpd-reconciliation-reports";
  private boolean useManagedIdentity = false;
}