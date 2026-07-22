package it.gov.pagopa.gpd.technicalsupport.config.reconciliation.biz;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "reconciliation.biz.cosmos")
public class ReconciliationBizCosmosProperties {

  private String endpoint;
  private String key;
  private String databaseName;
  private String containerName;
  private String readRegion;
  private boolean useManagedIdentity = false;
}