package it.gov.pagopa.gpd.technicalsupport.service.reconciliation;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import it.gov.pagopa.gpd.technicalsupport.config.reconciliation.ReconciliationProperties;
import it.gov.pagopa.gpd.technicalsupport.exception.AppException;
import it.gov.pagopa.gpd.technicalsupport.model.gpd.ServiceType;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.ReconciliationRangeRequest;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ReconciliationRangeValidatorTest {

  private ReconciliationProperties properties;
  private ReconciliationRangeValidator validator;

  @BeforeEach
  void setUp() {
    properties = new ReconciliationProperties();
    properties.setMaxProcessingWindowDays(7);
    properties.setMinProcessingDelayDays(1);

    validator = new ReconciliationRangeValidator(
        properties,
        Clock.fixed(
            Instant.parse("2026-11-24T23:30:00Z"),
            ZoneOffset.UTC));
  }

  private ReconciliationRangeRequest request(String from, String to) {
    return new ReconciliationRangeRequest(
        LocalDate.parse(from),
        LocalDate.parse(to),
        List.of(ServiceType.GPD));
  }

  @Test
  void acceptsMonthWithoutApplyingDailyWindowLimit() {
    properties.setMaxProcessingWindowDays(1);
    var range = request("2026-09-01", "2026-09-30");

    assertDoesNotThrow(() -> validator.validate(range));
  }

  @Test
  void acceptsSingleDayAtProcessingBoundary() {
    var range = request("2026-11-23", "2026-11-23");

    assertDoesNotThrow(() -> validator.validate(range));
  }

  @Test
  void acceptsRangeAcrossYearBoundary() {
    var range = request("2025-12-01", "2026-01-31");

    assertDoesNotThrow(() -> validator.validate(range));
  }

  @Test
  void rejectsReversedDates() {
    var range = request("2026-09-30", "2026-09-01");

    assertThatThrownBy(() -> validator.validate(range))
        .isInstanceOf(AppException.class)
        .hasMessageContaining("'to'");
  }

  @Test
  void rejectsTodayUsingInjectedClock() {
    var range = request("2026-11-23", "2026-11-24");

    assertThatThrownBy(() -> validator.validate(range))
        .isInstanceOf(AppException.class)
        .hasMessageContaining("2026-11-23");
  }

  @Test
  void respectsConfiguredProcessingDelay() {
    properties.setMinProcessingDelayDays(3);
    var acceptedRange = request("2026-11-01", "2026-11-21");
    var tooRecentRange = request("2026-11-01", "2026-11-22");

    assertDoesNotThrow(() -> validator.validate(acceptedRange));

    assertThatThrownBy(() -> validator.validate(tooRecentRange))
        .isInstanceOf(AppException.class)
        .hasMessageContaining("2026-11-21");
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 10000})
  void rejectsYearsUnsupportedByPythonWorker(int year) {
    var range = new ReconciliationRangeRequest(
        LocalDate.of(year, Month.JANUARY, 1),
        LocalDate.of(year, Month.JANUARY, 2),
        List.of(ServiceType.GPD));

    assertThatThrownBy(() -> validator.validate(range))
        .isInstanceOf(AppException.class)
        .hasMessageContaining("0001 and 9999");
  }
}