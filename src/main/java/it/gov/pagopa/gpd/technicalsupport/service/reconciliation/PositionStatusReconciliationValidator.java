package it.gov.pagopa.gpd.technicalsupport.service.reconciliation;

import it.gov.pagopa.gpd.technicalsupport.config.ReconciliationProperties;
import it.gov.pagopa.gpd.technicalsupport.exception.AppError;
import it.gov.pagopa.gpd.technicalsupport.exception.AppException;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.PositionStatusReconciliationRequest;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PositionStatusReconciliationValidator {

  private final ReconciliationProperties properties;
  private final Clock clock;

  public void validate(PositionStatusReconciliationRequest request) {
    if (request.to().isBefore(request.from())) {
      throw new AppException(AppError.BAD_REQUEST, "'to' must be greater than or equal to 'from'");
    }

    long requestedDays = ChronoUnit.DAYS.between(request.from(), request.to()) + 1;
    if (requestedDays > properties.getMaxProcessingWindowDays()) {
      throw new AppException(
          AppError.BAD_REQUEST,
          "The requested interval exceeds the configured maximum of %d days",
          properties.getMaxProcessingWindowDays());
    }

    LocalDate maxProcessableDate =
        LocalDate.now(clock).minusDays(properties.getMinProcessingDelayDays());

    if (request.to().isAfter(maxProcessableDate)) {
      throw new AppException(
          AppError.BAD_REQUEST,
          "The requested interval is too recent. Maximum processable date is %s",
          maxProcessableDate);
    }
  }
}