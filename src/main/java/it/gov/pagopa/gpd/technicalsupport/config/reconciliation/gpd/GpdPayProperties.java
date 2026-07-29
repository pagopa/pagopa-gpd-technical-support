package it.gov.pagopa.gpd.technicalsupport.config.reconciliation.gpd;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "reconciliation.gpd-pay")
public class GpdPayProperties {
  private String baseUrl = "http://localhost";
  private String apiKey = "test-api-key";
}