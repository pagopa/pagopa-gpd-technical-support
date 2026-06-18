package it.gov.pagopa.gpd.technicalsupport.service.reconciliation.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import it.gov.pagopa.gpd.technicalsupport.model.gpd.ServiceType;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.ReconciliationRunStatus;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.cosmos.ReconciliationRunDocument;
import it.gov.pagopa.gpd.technicalsupport.service.reconciliation.ReconciliationCounters;
import it.gov.pagopa.gpd.technicalsupport.service.reconciliation.key.ReconciliationKeyBuilder;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReconciliationRunDocumentMapperTest {

  private final ReconciliationRunDocumentMapper mapper =
      new ReconciliationRunDocumentMapper(new ReconciliationKeyBuilder());

  @Test
  void newCreatedRun_shouldCreateDocumentWithNormalizedKeys() {
    OffsetDateTime now = OffsetDateTime.of(2026, 5, 26, 10, 0, 0, 0, ZoneOffset.UTC);

    ReconciliationRunDocument document =
        mapper.newCreatedRun(
            LocalDate.of(2026, 5, 20),
            List.of(ServiceType.WISP, ServiceType.GPD),
            "2026-05-20__GPD|WISP__20260526T100000Z",
            now);

    assertThat(document.id()).isEqualTo("2026-05-20__GPD|WISP");
    assertThat(document.logicalRunKey()).isEqualTo("2026-05-20__GPD|WISP");
    assertThat(document.serviceTypesKey()).isEqualTo("GPD|WISP");
    assertThat(document.status()).isEqualTo(ReconciliationRunStatus.CREATED);
    assertThat(document.createdAt()).isEqualTo(now.toString());
    assertThat(document.updatedAt()).isEqualTo(now.toString());
  }

  @Test
  void done_shouldApplyCountersAndSetCompletedAt() {
    OffsetDateTime createdAt = OffsetDateTime.of(2026, 5, 26, 10, 0, 0, 0, ZoneOffset.UTC);
    OffsetDateTime completedAt = OffsetDateTime.of(2026, 5, 26, 10, 5, 0, 0, ZoneOffset.UTC);

    ReconciliationRunDocument document =
        mapper.newCreatedRun(
            LocalDate.of(2026, 5, 20),
            List.of(ServiceType.GPD),
            "2026-05-20__GPD__20260526T100000Z",
            createdAt);

    ReconciliationCounters counters =
        new ReconciliationCounters(100, 10, 3, 2, 1, 0, 2, 0);

    ReconciliationRunDocument done = mapper.done(document, counters, completedAt);

    assertThat(done.status()).isEqualTo(ReconciliationRunStatus.DONE);
    assertThat(done.scanned()).isEqualTo(100);
    assertThat(done.positiveEventsFound()).isEqualTo(10);
    assertThat(done.reconciliationCases()).isEqualTo(3);
    assertThat(done.recovered()).isEqualTo(2);
    assertThat(done.manualRequired()).isEqualTo(1);
    assertThat(done.technicalFailures()).isZero();
    assertThat(done.notRecovered()).isEqualTo(1);
    assertThat(done.completedAt()).isEqualTo(completedAt.toString());
  }
}