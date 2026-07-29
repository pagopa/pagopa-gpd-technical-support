package it.gov.pagopa.gpd.technicalsupport.config.reconciliation;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ReconciliationClockConfig {

  /**
   * Exposes a system UTC Clock used by reconciliation components.
   *
   * <p>Injecting Clock makes date-based
   * validations deterministic and easily testable.
   */
  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }
}