package it.gov.pagopa.gpd.technicalsupport.service.reconciliation.lookup;

import static it.gov.pagopa.gpd.technicalsupport.config.reconciliation.cosmos.CosmosBeanNames.RECONCILIATION_BIZ_POSITIVE_EVENTS_CONTAINER;

import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.models.SqlParameter;
import com.azure.cosmos.models.SqlQuerySpec;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.apd.ReconciliationCandidate;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.biz.BizPositiveEvent;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.biz.BizPositiveEventLookupResult;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.biz.cosmos.BizPositiveEventDocument;
import it.gov.pagopa.gpd.technicalsupport.service.reconciliation.mapper.BizPositiveEventMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class CosmosBizPositiveEventLookup implements BizPositiveEventLookup {

  private static final String DONE = "DONE";

  private static final String FIND_POSITIVE_EVENTS_SQL_TEMPLATE =
      """
      SELECT *
      FROM c
      WHERE c.eventStatus = @eventStatus
        AND c.debtorPosition.noticeNumber IN (%s)
      ORDER BY c.timestamp DESC
      """;

  private final CosmosContainer bizPositiveEventsContainer;
  private final BizPositiveEventMapper mapper;

  public CosmosBizPositiveEventLookup(
      @Qualifier(RECONCILIATION_BIZ_POSITIVE_EVENTS_CONTAINER)
          CosmosContainer bizPositiveEventsContainer,
      BizPositiveEventMapper mapper) {
    this.bizPositiveEventsContainer = bizPositiveEventsContainer;
    this.mapper = mapper;
  }

  @Override
  public BizPositiveEventLookupResult findPositiveEvent(ReconciliationCandidate candidate) {
    if (!isLookupable(candidate)) {
      log.warn(
          "Biz+ lookup skipped because candidate EC or NAV is blank. paymentPositionId={}, paymentOptionId={}, ec={}, nav={}, iuv={}",
          candidate.paymentPositionId(),
          candidate.paymentOptionId(),
          candidate.ec(),
          candidate.nav(),
          candidate.iuv());

      return BizPositiveEventLookupResult.notFound();
    }

    return findPositiveEvents(List.of(candidate))
        .getOrDefault(
            BizPositiveEventLookup.key(candidate),
            BizPositiveEventLookupResult.notFound());
  }

  @Override
  public Map<String, BizPositiveEventLookupResult> findPositiveEvents(
      List<ReconciliationCandidate> candidates) {

    List<ReconciliationCandidate> lookupableCandidates =
        candidates.stream()
            .filter(this::isLookupable)
            .toList();

    if (lookupableCandidates.isEmpty()) {
      log.info(
          "Biz+ batch lookup skipped because no lookupable candidates were found. candidates={}",
          candidates.size());

      return Map.of();
    }

    Set<String> candidateKeys =
        lookupableCandidates.stream()
            .map(BizPositiveEventLookup::key)
            .collect(Collectors.toCollection(LinkedHashSet::new));

    List<String> navs =
        lookupableCandidates.stream()
            .map(ReconciliationCandidate::nav)
            .map(String::trim)
            .distinct()
            .toList();

    try {
      SqlQuerySpec querySpec = buildBatchQuerySpec(navs);

      long startTime = System.currentTimeMillis();

      log.info(
          "Searching Biz+ positive events by batch. candidates={}, lookupableCandidates={}, distinctNavs={}, expectedEventStatus={}",
          candidates.size(),
          lookupableCandidates.size(),
          navs.size(),
          DONE);

      List<BizPositiveEventDocument> documents =
          bizPositiveEventsContainer
              .queryItems(querySpec, new CosmosQueryRequestOptions(), BizPositiveEventDocument.class)
              .stream()
              .toList();

      Map<String, BizPositiveEventLookupResult> results =
          mapDocumentsToLookupResults(candidateKeys, documents);

      log.info(
          "Biz+ positive events batch lookup completed. candidates={}, lookupableCandidates={}, distinctNavs={}, documentsFound={}, matchedCandidates={}, durationMs={}",
          candidates.size(),
          lookupableCandidates.size(),
          navs.size(),
          documents.size(),
          results.size(),
          System.currentTimeMillis() - startTime);

      return results;

    } catch (Exception e) {
      log.error(
          "Failed to lookup Biz+ positive events by batch. candidates={}, lookupableCandidates={}, distinctNavs={}",
          candidates.size(),
          lookupableCandidates.size(),
          navs.size(),
          e);

      return failedResultsFor(lookupableCandidates, e);
    }
  }

  private SqlQuerySpec buildBatchQuerySpec(List<String> navs) {
    List<SqlParameter> parameters = new ArrayList<>();
    parameters.add(new SqlParameter("@eventStatus", DONE));

    for (int i = 0; i < navs.size(); i++) {
      parameters.add(new SqlParameter("@nav" + i, navs.get(i)));
    }

    String inClause =
        IntStream.range(0, navs.size())
            .mapToObj(i -> "@nav" + i)
            .collect(Collectors.joining(", "));

    return new SqlQuerySpec(
        FIND_POSITIVE_EVENTS_SQL_TEMPLATE.formatted(inClause),
        parameters);
  }

  private Map<String, BizPositiveEventLookupResult> mapDocumentsToLookupResults(
      Set<String> candidateKeys,
      List<BizPositiveEventDocument> documents) {

    Map<String, BizPositiveEventLookupResult> results = new LinkedHashMap<>();

    for (BizPositiveEventDocument document : documents) {
      BizPositiveEvent event = mapper.toDomain(document);
      String key = BizPositiveEventLookup.key(event);

      if (!candidateKeys.contains(key)) {
        continue;
      }

      /*
       * The query is ordered by timestamp DESC.
       * If more than one Biz+ DONE event exists for the same EC/NAV, the first one
       * is the most recent one.
       */
      results.putIfAbsent(key, BizPositiveEventLookupResult.found(event));
    }

    return results;
  }

  private Map<String, BizPositiveEventLookupResult> failedResultsFor(
      List<ReconciliationCandidate> candidates,
      Exception e) {

    Map<String, BizPositiveEventLookupResult> results = new LinkedHashMap<>();

    for (ReconciliationCandidate candidate : candidates) {
      results.put(
          BizPositiveEventLookup.key(candidate),
          BizPositiveEventLookupResult.failed(e));
    }

    return results;
  }

  private boolean isLookupable(ReconciliationCandidate candidate) {
    return isNotBlank(candidate.ec()) && isNotBlank(candidate.nav());
  }

  private boolean isNotBlank(String value) {
    return value != null && !value.isBlank();
  }
}