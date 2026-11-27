package it.gov.pagopa.gpd.technicalsupport.service.reconciliation;

import it.gov.pagopa.gpd.technicalsupport.config.reconciliation.ReconciliationProperties;
import it.gov.pagopa.gpd.technicalsupport.exception.AppError;
import it.gov.pagopa.gpd.technicalsupport.exception.AppException;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.ReconciliationRangeRequest;
import java.time.Clock;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReconciliationRangeValidator {

  private final ReconciliationProperties properties;
  private final Clock clock;

  // Required fields and list elements are checked by Jakarta Validation at the API boundary.
  public void validate(ReconciliationRangeRequest request) {
    if (request.to().isBefore(request.from())) {
      throw new AppException(
          AppError.BAD_REQUEST,
          "'to' must be greater than or equal to 'from'");
    }

    // Keep dates compatible with the Python worker's datetime.date representation.
    if (request.from().getYear() < 1 || request.to().getYear() > 9999) {
      throw new AppException(
          AppError.BAD_REQUEST,
          "Dates must use years between 0001 and 9999");
    }

    LocalDate maxProcessableDate =
        LocalDate.now(clock).minusDays(properties.getMinProcessingDelayDays());

    if (request.to().isAfter(maxProcessableDate)) {
      throw new AppException(
          AppError.BAD_REQUEST,
          "The requested interval is too recent. Maximum processable date is %s"
              .formatted(maxProcessableDate));
    }
  }
}