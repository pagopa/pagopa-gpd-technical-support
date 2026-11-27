package it.gov.pagopa.gpd.technicalsupport.service.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import it.gov.pagopa.gpd.technicalsupport.exception.AppException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class ReconciliationRangeIdentityTest {

  private final ReconciliationRangeIdentity identity =
      new ReconciliationRangeIdentity();

  @Test
  void retryReturnsTheSameExecutionId() {
    assertThat(identity.executionId("dev", "PIDM-2546-test-01"))
        .isEqualTo(identity.executionId("dev", "PIDM-2546-test-01"));
  }

  @Test
  void identityIsStableAcrossServiceInstances() {
    assertThat(identity.executionId("dev", "PIDM-2546-test-01"))
        .isEqualTo(
            new ReconciliationRangeIdentity()
                .executionId("dev", "PIDM-2546-test-01"));
  }

  @Test
  void differentRequestsHaveDifferentIdentifiers() {
    assertThat(identity.executionId("dev", "request-1"))
        .isNotEqualTo(identity.executionId("dev", "request-2"));
  }

  @Test
  void environmentsHaveSeparateIdentities() {
    assertThat(identity.executionId("dev", "request-1"))
        .isNotEqualTo(identity.executionId("uat", "request-1"));
  }

  @Test
  void generatedIdentifierIsAValidUuid() {
    String result = identity.executionId("dev", "request-1");

    assertThat(UUID.fromString(result)).hasToString(result);
    assertThat(identity.executionId("prod", "a".repeat(128))).isNotBlank();
  }

  @Test
  void invalidKeysAreRejectedAsBadRequests() {
    String[] invalid = {
        null, "", " ", " leading", "trailing ", "a b",
        "a\tb", "a\nb", "a".repeat(129), "richiesta-\u00e8"
    };

    for (String key : invalid) {
      assertThatThrownBy(() -> identity.executionId("dev", key))
          .isInstanceOfSatisfying(
              AppException.class,
              error -> assertThat(error.getHttpStatus())
                  .isEqualTo(HttpStatus.BAD_REQUEST));
    }
  }

  @Test
  void invalidEnvironmentIsRejected() {
    for (String environment : new String[] {null, "", "local", "DEV"}) {
      assertThatThrownBy(
          () -> identity.executionId(environment, "request-1"))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }
}