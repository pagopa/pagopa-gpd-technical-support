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
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class CosmosBizPositiveEventLookup implements BizPositiveEventLookup {

  private static final String DONE = "DONE";

  private static final String FIND_POSITIVE_EVENT_SQL =
      """
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
      @Qualifier(RECONCILIATION_BIZ_POSITIVE_EVENTS_CONTAINER)
          CosmosContainer bizPositiveEventsContainer,
      BizPositiveEventMapper mapper) {
    this.bizPositiveEventsContainer = bizPositiveEventsContainer;
    this.mapper = mapper;
  }

  @Override
  public BizPositiveEventLookupResult findPositiveEvent(ReconciliationCandidate candidate) {
    try {
      if (candidate.ec() == null || candidate.ec().isBlank()) {
        log.warn(
            "Biz+ lookup skipped because candidate EC is blank. paymentPositionId={}, paymentOptionId={}, nav={}, iuv={}",
            candidate.paymentPositionId(),
            candidate.paymentOptionId(),
            candidate.nav(),
            candidate.iuv());

        return BizPositiveEventLookupResult.notFound();
      }

      if (candidate.nav() == null || candidate.nav().isBlank()) {
        log.warn(
            "Biz+ lookup skipped because candidate NAV is blank. paymentPositionId={}, paymentOptionId={}, ec={}, iuv={}",
            candidate.paymentPositionId(),
            candidate.paymentOptionId(),
            candidate.ec(),
            candidate.iuv());

        return BizPositiveEventLookupResult.notFound();
      }

      SqlQuerySpec querySpec =
          new SqlQuerySpec(
              FIND_POSITIVE_EVENT_SQL,
              List.of(
                  new SqlParameter("@ec", candidate.ec()),
                  new SqlParameter("@nav", candidate.nav()),
                  new SqlParameter("@eventStatus", DONE)));

      List<BizPositiveEventDocument> documents =
          bizPositiveEventsContainer
              .queryItems(querySpec, new CosmosQueryRequestOptions(), BizPositiveEventDocument.class)
              .stream()
              .toList();

      if (documents.isEmpty()) {
        log.debug(
            "No Biz+ positive event found. paymentPositionId={}, paymentOptionId={}, ec={}, nav={}, iuv={}, expectedEventStatus={}",
            candidate.paymentPositionId(),
            candidate.paymentOptionId(),
            candidate.ec(),
            candidate.nav(),
            candidate.iuv(),
            DONE);

        return BizPositiveEventLookupResult.notFound();
      }

      BizPositiveEventDocument document = documents.get(0);
      BizPositiveEvent event = mapper.toDomain(document);

      log.info(
          "Biz+ positive event found. paymentPositionId={}, paymentOptionId={}, ec={}, nav={}, iuv={}, eventId={}, receiptId={}, eventStatus={}",
          candidate.paymentPositionId(),
          candidate.paymentOptionId(),
          candidate.ec(),
          candidate.nav(),
          candidate.iuv(),
          event.eventId(),
          event.receiptId(),
          document.eventStatus());

      return BizPositiveEventLookupResult.found(event);

    } catch (Exception e) {
      log.error(
          "Failed to lookup Biz+ positive event. paymentPositionId={}, paymentOptionId={}, ec={}, nav={}, iuv={}",
          candidate.paymentPositionId(),
          candidate.paymentOptionId(),
          candidate.ec(),
          candidate.nav(),
          candidate.iuv(),
          e);

      return BizPositiveEventLookupResult.failed(e);
    }
  }
}