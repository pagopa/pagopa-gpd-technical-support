package it.gov.pagopa.gpd.technicalsupport.service.reconciliation.lookup;

import static org.assertj.core.api.Assertions.assertThat;

import it.gov.pagopa.gpd.technicalsupport.model.gpd.DebtPositionStatus;
import it.gov.pagopa.gpd.technicalsupport.model.gpd.PaymentOptionStatus;
import it.gov.pagopa.gpd.technicalsupport.model.gpd.ServiceType;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.apd.ReconciliationCandidate;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.biz.BizPositiveEvent;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.biz.BizPositiveEventLookupResult;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.biz.BizPositiveEventLookupStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BizPositiveEventLookupTest {

  @Test
  void key_shouldNormalizeEcAndNav() {
    assertThat(BizPositiveEventLookup.key(" 77777777777 ", " 302131563536065220 "))
        .isEqualTo("77777777777__302131563536065220");
  }

  @Test
  void key_shouldHandleNullValues() {
    assertThat(BizPositiveEventLookup.key(null, null)).isEqualTo("__");
  }

  @Test
  void key_shouldBuildKeyFromCandidate() {
    ReconciliationCandidate candidate = candidate(" 77777777777 ", " 302131563536065220 ");

    assertThat(BizPositiveEventLookup.key(candidate))
        .isEqualTo("77777777777__302131563536065220");
  }

  @Test
  void key_shouldBuildKeyFromBizPositiveEvent() {
    BizPositiveEvent event = event(" 77777777777 ", " 302131563536065220 ");

    assertThat(BizPositiveEventLookup.key(event))
        .isEqualTo("77777777777__302131563536065220");
  }

  @Test
  void findPositiveEvents_shouldDelegateToSingleLookupAndPreserveCandidateKey() {
    ReconciliationCandidate candidate = candidate("77777777777", "302131563536065220");

    BizPositiveEventLookup lookup =
        item -> BizPositiveEventLookupResult.found(event(item.ec(), item.nav()));

    Map<String, BizPositiveEventLookupResult> results =
        lookup.findPositiveEvents(List.of(candidate));

    assertThat(results).containsOnlyKeys("77777777777__302131563536065220");
    assertThat(results.get("77777777777__302131563536065220").status())
        .isEqualTo(BizPositiveEventLookupStatus.FOUND);
    assertThat(results.get("77777777777__302131563536065220").event().ec())
        .isEqualTo("77777777777");
    assertThat(results.get("77777777777__302131563536065220").event().nav())
        .isEqualTo("302131563536065220");
  }

  @Test
  void findPositiveEvents_shouldReturnEmptyMapWhenInputIsEmpty() {
    BizPositiveEventLookup lookup = item -> BizPositiveEventLookupResult.notFound();

    Map<String, BizPositiveEventLookupResult> results = lookup.findPositiveEvents(List.of());

    assertThat(results).isEmpty();
  }
  
  @Test
  void findPositiveEvents_shouldDelegateToSingleLookupForEachCandidate() {
    ReconciliationCandidate firstCandidate =
        candidate("77777777777", "302131563536065220");

    ReconciliationCandidate secondCandidate =
        candidate("88888888888", "302131563536065221");

    BizPositiveEventLookup lookup =
        item -> BizPositiveEventLookupResult.found(event(item.ec(), item.nav()));

    Map<String, BizPositiveEventLookupResult> results =
        lookup.findPositiveEvents(List.of(firstCandidate, secondCandidate));

    assertThat(results)
        .containsOnlyKeys(
            "77777777777__302131563536065220",
            "88888888888__302131563536065221");

    assertThat(results.get("77777777777__302131563536065220").status())
        .isEqualTo(BizPositiveEventLookupStatus.FOUND);

    assertThat(results.get("88888888888__302131563536065221").status())
        .isEqualTo(BizPositiveEventLookupStatus.FOUND);

    assertThat(results.get("77777777777__302131563536065220").event().ec())
        .isEqualTo("77777777777");

    assertThat(results.get("88888888888__302131563536065221").event().ec())
        .isEqualTo("88888888888");
  }

  private ReconciliationCandidate candidate(String ec, String nav) {
    return new ReconciliationCandidate(
        LocalDate.of(2026, 6, 10),
        ServiceType.GPD,
        "payment-position-id",
        "payment-option-id",
        ec,
        nav,
        "02131563536065220",
        DebtPositionStatus.VALID,
        PaymentOptionStatus.PO_UNPAID,
        "SINGLE_OPTION");
  }

  private BizPositiveEvent event(String ec, String nav) {
    return new BizPositiveEvent(
        "biz-event-id",
        "receipt-id",
        ec,
        nav,
        "02131563536065220",
        "iur",
        "2026-06-10T14:50:20.524566",
        "other",
        "0.0",
        1781168213044L,
        "DONE",
        "NDP004UAT",
        "ABI03034",
        "91010030400",
        "Banca Agricola Commerciale SpA",
        "97249640588",
        "97249640588_01");
  }
}