package it.gov.pagopa.gpd.technicalsupport.model.reconciliation;

import static org.junit.jupiter.api.Assertions.assertFalse;

import it.gov.pagopa.gpd.technicalsupport.model.gpd.ServiceType;
import jakarta.validation.Validation;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class ReconciliationRangeRequestTest {

  static Stream<ReconciliationRangeRequest> invalidRequests() {
    LocalDate day = LocalDate.of(2026, 9, 1);

    return Stream.of(
        new ReconciliationRangeRequest(null, day, List.of(ServiceType.GPD)),
        new ReconciliationRangeRequest(day, null, List.of(ServiceType.GPD)),
        new ReconciliationRangeRequest(day, day, null),
        new ReconciliationRangeRequest(day, day, List.of()),
        new ReconciliationRangeRequest(
            day, day, Arrays.asList(ServiceType.GPD, null)));
  }

  @ParameterizedTest
  @MethodSource("invalidRequests")
  void rejectsMissingFieldsAndNullServiceElements(
      ReconciliationRangeRequest request) {

    try (var factory = Validation.buildDefaultValidatorFactory()) {
      assertFalse(factory.getValidator().validate(request).isEmpty());
    }
  }
}