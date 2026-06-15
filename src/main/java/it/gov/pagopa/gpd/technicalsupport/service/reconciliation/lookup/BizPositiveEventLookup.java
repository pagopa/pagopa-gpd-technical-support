package it.gov.pagopa.gpd.technicalsupport.service.reconciliation.lookup;

import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.apd.ReconciliationCandidate;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.biz.BizPositiveEvent;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.biz.BizPositiveEventLookupResult;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public interface BizPositiveEventLookup {

  BizPositiveEventLookupResult findPositiveEvent(ReconciliationCandidate candidate);

  default Map<String, BizPositiveEventLookupResult> findPositiveEvents(
      List<ReconciliationCandidate> candidates) {

    Map<String, BizPositiveEventLookupResult> results = new LinkedHashMap<>();

    for (ReconciliationCandidate candidate : candidates) {
      results.put(key(candidate), findPositiveEvent(candidate));
    }

    return results;
  }

  static String key(ReconciliationCandidate candidate) {
    return key(candidate.ec(), candidate.nav());
  }

  static String key(BizPositiveEvent event) {
    return key(event.ec(), event.nav());
  }

  static String key(String ec, String nav) {
    return normalize(ec) + "__" + normalize(nav);
  }

  private static String normalize(String value) {
    return value == null ? "" : value.trim();
  }
}