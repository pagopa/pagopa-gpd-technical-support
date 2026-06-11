package it.gov.pagopa.gpd.technicalsupport.service.reconciliation.lookup;

import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.models.SqlParameter;
import com.azure.cosmos.models.SqlQuerySpec;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.apd.ReconciliationCandidate;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.biz.BizPositiveEvent;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.biz.BizPositiveEventLookupResult;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.biz.cosmos.BizPositiveEventDocument;
import it.gov.pagopa.gpd.technicalsupport.service.reconciliation.mapper.BizPositiveEventMapper;

import static it.gov.pagopa.gpd.technicalsupport.config.reconciliation.cosmos.CosmosBeanNames.RECONCILIATION_BIZ_POSITIVE_EVENTS_CONTAINER;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class CosmosBizPositiveEventLookup implements BizPositiveEventLookup {

  private static final String FIND_POSITIVE_EVENT_SQL = """
      SELECT TOP 1 *
      FROM c
      WHERE c.creditor.idPA = @ec
        AND c.debtorPosition.noticeNumber = @nav
        AND c.eventStatus = @eventStatus
      ORDER BY c.timestamp DESC
      """;

  private final CosmosContainer bizPositiveEventsContainer;
  private final BizPositiveEventMapper mapper;

  public CosmosBizPositiveEventLookup(
      @Qualifier(RECONCILIATION_BIZ_POSITIVE_EVENTS_CONTAINER) CosmosContainer bizPositiveEventsContainer,
      BizPositiveEventMapper mapper) {
    this.bizPositiveEventsContainer = bizPositiveEventsContainer;
    this.mapper = mapper;
  }

  @Override
  public BizPositiveEventLookupResult findPositiveEvent(ReconciliationCandidate candidate) {
    try {
      if (candidate.ec() == null || candidate.ec().isBlank()) {
        return BizPositiveEventLookupResult.notFound();
      }

      if (candidate.nav() == null || candidate.nav().isBlank()) {
        return BizPositiveEventLookupResult.notFound();
      }

      SqlQuerySpec querySpec =
          new SqlQuerySpec(
              FIND_POSITIVE_EVENT_SQL,
              List.of(
                  new SqlParameter("@ec", candidate.ec()),
                  new SqlParameter("@nav", candidate.nav()),
                  new SqlParameter("@eventStatus", "DONE")));

      CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();

      List<BizPositiveEventDocument> documents =
          bizPositiveEventsContainer
              .queryItems(querySpec, options, BizPositiveEventDocument.class)
              .stream()
              .toList();

      if (documents.isEmpty()) {
        log.debug(
            "No Biz+ positive event found. ec={}, nav={}, paymentOptionId={}",
            candidate.ec(),
            candidate.nav(),
            candidate.paymentOptionId());

        return BizPositiveEventLookupResult.notFound();
      }

      BizPositiveEvent event = mapper.toDomain(documents.get(0));

      log.info(
          "Biz+ positive event found. ec={}, nav={}, eventId={}, paymentOptionId={}",
          candidate.ec(),
          candidate.nav(),
          event.eventId(),
          candidate.paymentOptionId());

      return BizPositiveEventLookupResult.found(event);

    } catch (Exception e) {
      log.error(
          "Failed to lookup Biz+ positive event. ec={}, nav={}, paymentOptionId={}",
          candidate.ec(),
          candidate.nav(),
          candidate.paymentOptionId(),
          e);

      return BizPositiveEventLookupResult.failed(e);
    }
  }
}