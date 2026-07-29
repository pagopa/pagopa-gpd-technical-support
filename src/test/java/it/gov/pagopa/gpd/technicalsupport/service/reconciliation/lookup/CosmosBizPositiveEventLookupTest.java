package it.gov.pagopa.gpd.technicalsupport.service.reconciliation.lookup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.models.SqlQuerySpec;
import com.azure.cosmos.util.CosmosPagedIterable;
import it.gov.pagopa.gpd.technicalsupport.model.gpd.DebtPositionStatus;
import it.gov.pagopa.gpd.technicalsupport.model.gpd.PaymentOptionStatus;
import it.gov.pagopa.gpd.technicalsupport.model.gpd.ServiceType;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.apd.ReconciliationCandidate;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.biz.BizPositiveEvent;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.biz.BizPositiveEventLookupResult;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.biz.BizPositiveEventLookupStatus;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.biz.cosmos.BizPositiveEventDocument;
import it.gov.pagopa.gpd.technicalsupport.service.reconciliation.mapper.BizPositiveEventMapper;
import java.time.LocalDate;
import java.time.Month;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CosmosBizPositiveEventLookupTest {

  private final CosmosContainer bizPositiveEventsContainer =
      mock(CosmosContainer.class);

  private final BizPositiveEventMapper mapper = new BizPositiveEventMapper();

  private final CosmosBizPositiveEventLookup lookup =
      new CosmosBizPositiveEventLookup(bizPositiveEventsContainer, mapper);

  @Test
  void findPositiveEvent_shouldReturnNotFoundAndNotQueryCosmosWhenCandidateIsNotLookupable() {
    ReconciliationCandidate candidate =
        candidate("payment-position-id", "payment-option-id", " ", "302131563536065220");

    BizPositiveEventLookupResult result = lookup.findPositiveEvent(candidate);

    assertThat(result.status()).isEqualTo(BizPositiveEventLookupStatus.NOT_FOUND);

    verify(bizPositiveEventsContainer, never())
        .queryItems(
            any(SqlQuerySpec.class),
            any(CosmosQueryRequestOptions.class),
            eq(BizPositiveEventDocument.class));
  }

  @Test
  void findPositiveEvents_shouldReturnEmptyMapWhenNoLookupableCandidatesAreProvided() {
    ReconciliationCandidate blankEcCandidate =
        candidate("payment-position-id-1", "payment-option-id-1", " ", "302131563536065220");

    ReconciliationCandidate blankNavCandidate =
        candidate("payment-position-id-2", "payment-option-id-2", "77777777777", " ");

    Map<String, BizPositiveEventLookupResult> results =
        lookup.findPositiveEvents(List.of(blankEcCandidate, blankNavCandidate));

    assertThat(results).isEmpty();

    verify(bizPositiveEventsContainer, never())
        .queryItems(
            any(SqlQuerySpec.class),
            any(CosmosQueryRequestOptions.class),
            eq(BizPositiveEventDocument.class));
  }

  @Test
  void findPositiveEvents_shouldQueryDistinctNavsAndReturnOnlyMatchingCandidateKeys() {
    ReconciliationCandidate firstCandidate =
        candidate(
            "payment-position-id-1",
            "payment-option-id-1",
            "77777777777",
            "302131563536065220");

    ReconciliationCandidate secondCandidate =
        candidate(
            "payment-position-id-2",
            "payment-option-id-2",
            "88888888888",
            "302131563536065221");

    ReconciliationCandidate duplicateNavCandidate =
        candidate(
            "payment-position-id-3",
            "payment-option-id-3",
            "99999999999",
            "302131563536065220");

    BizPositiveEventDocument firstDocument =
        document("event-1", "receipt-1", "77777777777", "302131563536065220", 300L);

    BizPositiveEventDocument secondDocument =
        document("event-2", "receipt-2", "88888888888", "302131563536065221", 200L);

    BizPositiveEventDocument unrelatedDocument =
        document("event-3", "receipt-3", "00000000000", "302131563536065222", 100L);

    mockCosmosQuery(firstDocument, secondDocument, unrelatedDocument);

    Map<String, BizPositiveEventLookupResult> results =
        lookup.findPositiveEvents(
            List.of(firstCandidate, secondCandidate, duplicateNavCandidate));

    assertThat(results)
        .containsOnlyKeys(
            BizPositiveEventLookup.key(firstCandidate),
            BizPositiveEventLookup.key(secondCandidate));

    assertThat(results.get(BizPositiveEventLookup.key(firstCandidate)).status())
        .isEqualTo(BizPositiveEventLookupStatus.FOUND);

    assertThat(results.get(BizPositiveEventLookup.key(secondCandidate)).status())
        .isEqualTo(BizPositiveEventLookupStatus.FOUND);

    ArgumentCaptor<SqlQuerySpec> queryCaptor = ArgumentCaptor.forClass(SqlQuerySpec.class);

    verify(bizPositiveEventsContainer)
        .queryItems(
            queryCaptor.capture(),
            any(CosmosQueryRequestOptions.class),
            eq(BizPositiveEventDocument.class));

    SqlQuerySpec querySpec = queryCaptor.getValue();

    assertThat(querySpec.getQueryText())
        .contains("c.eventStatus = @eventStatus")
        .contains("c.debtorPosition.noticeNumber IN (@nav0, @nav1)")
        .contains("ORDER BY c.timestamp DESC");

    assertThat(querySpec.getParameters()).hasSize(3);
  }

  @Test
  void findPositiveEvents_shouldKeepFirstDocumentWhenMoreDocumentsHaveSameEcAndNav() {
    ReconciliationCandidate candidate =
        candidate(
            "payment-position-id",
            "payment-option-id",
            "77777777777",
            "302131563536065220");

    BizPositiveEventDocument newestDocument =
        document("newest-event", "newest-receipt", "77777777777", "302131563536065220", 300L);

    BizPositiveEventDocument olderDocument =
        document("older-event", "older-receipt", "77777777777", "302131563536065220", 100L);

    mockCosmosQuery(newestDocument, olderDocument);

    Map<String, BizPositiveEventLookupResult> results =
        lookup.findPositiveEvents(List.of(candidate));

    BizPositiveEvent expectedEvent = mapper.toDomain(newestDocument);

    assertThat(results).containsOnlyKeys(BizPositiveEventLookup.key(candidate));
    assertThat(results.get(BizPositiveEventLookup.key(candidate)).status())
        .isEqualTo(BizPositiveEventLookupStatus.FOUND);
    assertThat(results.get(BizPositiveEventLookup.key(candidate)).event())
        .isEqualTo(expectedEvent);
  }

  @Test
  void findPositiveEvents_shouldReturnFailedResultsForLookupableCandidatesWhenCosmosThrows() {
    ReconciliationCandidate lookupableCandidate =
        candidate(
            "payment-position-id-1",
            "payment-option-id-1",
            "77777777777",
            "302131563536065220");

    ReconciliationCandidate notLookupableCandidate =
        candidate(
            "payment-position-id-2",
            "payment-option-id-2",
            " ",
            "302131563536065221");

    when(
            bizPositiveEventsContainer.queryItems(
                any(SqlQuerySpec.class),
                any(CosmosQueryRequestOptions.class),
                eq(BizPositiveEventDocument.class)))
        .thenThrow(new RuntimeException("Cosmos unavailable"));

    Map<String, BizPositiveEventLookupResult> results =
        lookup.findPositiveEvents(List.of(lookupableCandidate, notLookupableCandidate));

    assertThat(results).containsOnlyKeys(BizPositiveEventLookup.key(lookupableCandidate));
    assertThat(results.get(BizPositiveEventLookup.key(lookupableCandidate)).status())
        .isEqualTo(BizPositiveEventLookupStatus.FAILED);
  }

  @SafeVarargs
  private final void mockCosmosQuery(BizPositiveEventDocument... documents) {
    @SuppressWarnings("unchecked")
    CosmosPagedIterable<BizPositiveEventDocument> pagedIterable =
        mock(CosmosPagedIterable.class);

    when(pagedIterable.stream()).thenReturn(Stream.of(documents));

    when(
            bizPositiveEventsContainer.queryItems(
                any(SqlQuerySpec.class),
                any(CosmosQueryRequestOptions.class),
                eq(BizPositiveEventDocument.class)))
        .thenReturn(pagedIterable);
  }

  private ReconciliationCandidate candidate(
      String paymentPositionId,
      String paymentOptionId,
      String ec,
      String nav) {

    return new ReconciliationCandidate(
        LocalDate.of(2026, Month.JUNE, 10),
        ServiceType.GPD,
        paymentPositionId,
        paymentOptionId,
        ec,
        nav,
        "02131563536065220",
        DebtPositionStatus.VALID,
        PaymentOptionStatus.PO_UNPAID,
        "SINGLE_OPTION");
  }

  private BizPositiveEventDocument document(
      String id,
      String receiptId,
      String ec,
      String nav,
      Long timestamp) {

    return new BizPositiveEventDocument(
        id,
        receiptId,
        new BizPositiveEventDocument.DebtorPosition(
            nav,
            "02131563536065220",
            "iur"),
        new BizPositiveEventDocument.Creditor(ec),
        new BizPositiveEventDocument.Psp(
            "ABI03034",
            "97249640588",
            "97249640588_01",
            "Banca Agricola Commerciale SpA",
            "09999999999",
            "91010030400",
            "NA"),
        new BizPositiveEventDocument.PaymentInfo(
            "2026-06-10T14:50:20.524566",
            "other",
            "0.0",
            "payment-token",
            "iur"),
        timestamp,
        new BizPositiveEventDocument.Properties("NDP004UAT"),
        "DONE");
  }
}