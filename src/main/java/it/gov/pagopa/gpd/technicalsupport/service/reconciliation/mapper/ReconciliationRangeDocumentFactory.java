package it.gov.pagopa.gpd.technicalsupport.service.reconciliation.mapper;

import it.gov.pagopa.gpd.technicalsupport.exception.AppError;
import it.gov.pagopa.gpd.technicalsupport.exception.AppException;
import it.gov.pagopa.gpd.technicalsupport.model.gpd.ServiceType;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.ReconciliationRangeRequest;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.cosmos.ReconciliationRangeDocument;
import it.gov.pagopa.gpd.technicalsupport.service.reconciliation.ReconciliationRangeIdentity;
import it.gov.pagopa.gpd.technicalsupport.service.reconciliation.ReconciliationRangeValidator;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReconciliationRangeDocumentFactory {
  private final ReconciliationRangeIdentity identity;
  private final ReconciliationRangeValidator validator;
  private final Clock clock;

  public ReconciliationRangeDocument newCreated(
      String environment, String idempotencyKey, ReconciliationRangeRequest request) {
    validateRequiredFields(request);
    validator.validate(request);
    String executionId = identity.executionId(environment, idempotencyKey);
    var services = request.serviceTypes().stream()
        .map(ServiceType::name).distinct().sorted().toList();
    int total = Math.toIntExact(ChronoUnit.DAYS.between(request.from(), request.to()) + 1);
    String now = Instant.now(clock).toString();

    return ReconciliationRangeDocument.builder()
        .id("summary").documentType("RANGE").schemaVersion(1)
        .executionId(executionId).environment(environment)
        .from(request.from().toString()).to(request.to().toString())
        .serviceTypes(services).serviceTypesKey(String.join("|", services))
        .status("CREATED").currentDay(request.from().toString())
        .totalDays(total).completedDays(0).remainingDays(total)
        .createdAt(now).updatedAt(now)
        .build();
  }

  private void validateRequiredFields(ReconciliationRangeRequest request) {
    if (request == null || request.from() == null || request.to() == null
        || request.serviceTypes() == null || request.serviceTypes().isEmpty()
        || request.serviceTypes().stream().anyMatch(Objects::isNull)) {
      throw new AppException(AppError.BAD_REQUEST,
          "Dates and a non-empty list of non-null service types are required");
    }
  }
}
