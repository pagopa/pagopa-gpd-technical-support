package it.gov.pagopa.gpd.technicalsupport.service.reconciliation;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import it.gov.pagopa.gpd.technicalsupport.config.reconciliation.ReconciliationProperties;
import it.gov.pagopa.gpd.technicalsupport.exception.AppException;
import it.gov.pagopa.gpd.technicalsupport.model.gpd.ServiceType;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.PositionStatusReconciliationRequest;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PositionStatusReconciliationValidatorTest {

  private PositionStatusReconciliationValidator validator;

  @BeforeEach
  void setUp() {
    ReconciliationProperties properties = new ReconciliationProperties();
    properties.setMaxProcessingWindowDays(7);
    properties.setMinProcessingDelayDays(1);

    Clock clock = Clock.fixed(Instant.parse("2026-05-29T10:00:00Z"), ZoneOffset.UTC);

    validator = new PositionStatusReconciliationValidator(properties, clock);
  }

  @Test
  void validate_shouldRejectWhenToIsBeforeFrom() {
    PositionStatusReconciliationRequest request =
        new PositionStatusReconciliationRequest(
            LocalDate.of(2026, 5, 20),
            LocalDate.of(2026, 5, 19),
            List.of(ServiceType.GPD),
            false);

    assertThatThrownBy(() -> validator.validate(request))
        .isInstanceOf(AppException.class)
        .hasMessageContaining("'to' must be greater than or equal to 'from'");
  }

  @Test
  void validate_shouldRejectWhenIntervalExceedsConfiguredLimit() {
    PositionStatusReconciliationRequest request =
        new PositionStatusReconciliationRequest(
            LocalDate.of(2026, 5, 1),
            LocalDate.of(2026, 5, 20),
            List.of(ServiceType.GPD),
            false);

    assertThatThrownBy(() -> validator.validate(request))
        .isInstanceOf(AppException.class)
        .hasMessageContaining("exceeds the configured maximum");
  }

  @Test
  void validate_shouldRejectTooRecentInterval() {
    PositionStatusReconciliationRequest request =
        new PositionStatusReconciliationRequest(
            LocalDate.of(2026, 5, 29),
            LocalDate.of(2026, 5, 29),
            List.of(ServiceType.GPD),
            false);

    assertThatThrownBy(() -> validator.validate(request))
        .isInstanceOf(AppException.class)
        .hasMessageContaining("too recent");
  }
}