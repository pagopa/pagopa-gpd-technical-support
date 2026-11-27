package it.gov.pagopa.gpd.technicalsupport.service.reconciliation;

import com.azure.cosmos.CosmosException;
import it.gov.pagopa.gpd.technicalsupport.exception.ReconciliationRangeLeaseBusyException;
import it.gov.pagopa.gpd.technicalsupport.exception.ReconciliationRangeLeaseLostException;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.ReconciliationRangeSnapshot;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.cosmos.ReconciliationRangeDocument;
import it.gov.pagopa.gpd.technicalsupport.service.reconciliation.store.cosmos.CosmosReconciliationRangeStore;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "reconciliation.cosmos", name = "enabled", havingValue = "true")
public class ReconciliationRangeLease {
  public static final long DEFAULT_LEASE_SECONDS = 120;
  private static final String STATUS_RUNNING = "RUNNING";

  private final CosmosReconciliationRangeStore store;
  private final Clock clock;

  public ReconciliationRangeSnapshot acquire(String executionId, UUID ownerId, long seconds) {
    validateArguments(ownerId, seconds);
    var current = store.get(executionId);
    var document = current.document();
    Instant now = clock.instant();
    assertAcquirable(document, now);
    return write(current, document.toBuilder()
        .status(STATUS_RUNNING).ownerId(ownerId.toString())
        .leaseExpiresAt(now.plusSeconds(seconds).toString())
        .heartbeatAt(now.toString()).updatedAt(now.toString()).build());
  }

  public ReconciliationRangeSnapshot renew(
      ReconciliationRangeSnapshot current, UUID ownerId, long seconds) {
    validateArguments(ownerId, seconds);
    Instant now = clock.instant();
    assertOwnedAt(current.document(), ownerId, now);
    return write(current, current.document().toBuilder()
        .leaseExpiresAt(now.plusSeconds(seconds).toString())
        .heartbeatAt(now.toString()).updatedAt(now.toString()).build());
  }

  public ReconciliationRangeSnapshot release(ReconciliationRangeSnapshot current, UUID ownerId) {
    requireOwnerId(ownerId);
    Instant now = clock.instant();
    assertOwnedAt(current.document(), ownerId, now);
    return write(current, current.document().toBuilder()
        .ownerId(null).leaseExpiresAt(null).updatedAt(now.toString()).build());
  }

  public void assertOwned(ReconciliationRangeSnapshot current, UUID ownerId) {
    requireOwnerId(ownerId);
    assertOwnedAt(current.document(), ownerId, clock.instant());
  }

  private void assertAcquirable(ReconciliationRangeDocument document, Instant now) {
    assertSupported(document);
    if (!"CREATED".equals(document.status()) && !STATUS_RUNNING.equals(document.status())) {
      throw new IllegalStateException("Range is not eligible for automatic acquisition");
    }
    if ((document.ownerId() == null) != (document.leaseExpiresAt() == null)) {
      throw new IllegalStateException("Inconsistent ownership fields");
    }
    if (document.ownerId() != null) {
      validatePersistedOwner(document.ownerId());
      if (expiry(document.leaseExpiresAt()).isAfter(now)) {
        throw new ReconciliationRangeLeaseBusyException("Range already has an active owner");
      }
    }
  }

  private void assertOwnedAt(ReconciliationRangeDocument document, UUID ownerId, Instant now) {
    assertSupported(document);
    if (!STATUS_RUNNING.equals(document.status()) || !ownerId.toString().equals(document.ownerId())) {
      throw new ReconciliationRangeLeaseLostException("Process does not own this running range");
    }
    if (document.leaseExpiresAt() == null || !expiry(document.leaseExpiresAt()).isAfter(now)) {
      throw new ReconciliationRangeLeaseLostException("Lease expired; stop processing");
    }
  }

  private void assertSupported(ReconciliationRangeDocument document) {
    if (!"summary".equals(document.id()) || !"RANGE".equals(document.documentType())
        || document.schemaVersion() != 1) {
      throw new IllegalStateException("Unsupported range schema");
    }
  }

  private Instant expiry(String value) {
    try {
      return OffsetDateTime.parse(value).toInstant();
    } catch (DateTimeParseException error) {
      throw new IllegalStateException("Invalid lease expiration timestamp", error);
    }
  }

  private void validatePersistedOwner(String ownerId) {
    try {
      UUID.fromString(ownerId);
    } catch (IllegalArgumentException error) {
      throw new IllegalStateException("Invalid persisted lease owner", error);
    }
  }

  private void validateArguments(UUID ownerId, long seconds) {
    requireOwnerId(ownerId);
    if (seconds <= 0) {
      throw new IllegalArgumentException("Lease duration must be positive");
    }
  }

  private void requireOwnerId(UUID ownerId) {
    if (ownerId == null) {
      throw new IllegalArgumentException("ownerId is required");
    }
  }

  private ReconciliationRangeSnapshot write(
      ReconciliationRangeSnapshot current, ReconciliationRangeDocument updated) {
    try {
      return store.replace(current, updated);
    } catch (CosmosException error) {
      if (error.getStatusCode() == 412) {
        throw new ReconciliationRangeLeaseLostException(
            "Concurrent update; stop and reassess ownership", error);
      }
      throw error;
    }
  }
}
