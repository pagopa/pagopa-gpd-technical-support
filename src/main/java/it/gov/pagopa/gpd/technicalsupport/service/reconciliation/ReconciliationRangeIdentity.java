package it.gov.pagopa.gpd.technicalsupport.service.reconciliation;

import it.gov.pagopa.gpd.technicalsupport.exception.AppError;
import it.gov.pagopa.gpd.technicalsupport.exception.AppException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class ReconciliationRangeIdentity {

  private static final String NAMESPACE = "gpd-reconciliation-range:v1:";
  private static final int MAX_KEY_LENGTH = 128;

  public String executionId(String environment, String idempotencyKey) {
    if (environment == null
        || !Set.of("dev", "uat", "prod").contains(environment)) {
      throw new IllegalArgumentException(
          "Unsupported reconciliation environment");
    }

    if (idempotencyKey == null
        || idempotencyKey.isEmpty()
        || idempotencyKey.length() > MAX_KEY_LENGTH
        || idempotencyKey.chars().anyMatch(value -> value < 33 || value > 126)) {
      throw new AppException(
          AppError.BAD_REQUEST,
          "Idempotency-Key must contain 1 to 128 visible ASCII characters without spaces");
    }

    String name = NAMESPACE + environment + ":" + idempotencyKey;

    return UUID.nameUUIDFromBytes(
        name.getBytes(StandardCharsets.UTF_8)).toString();
  }
}