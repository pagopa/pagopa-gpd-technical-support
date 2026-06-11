package it.gov.pagopa.gpd.technicalsupport.config.reconciliation.apd;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "reconciliation.apd")
public class ReconciliationApdProperties {
  private String jdbcUrl;
  private String username;
  private String password;
  private int maximumPoolSize = 5;
  private int minimumIdle = 1;
  private long connectionTimeoutMs = 30000;
}