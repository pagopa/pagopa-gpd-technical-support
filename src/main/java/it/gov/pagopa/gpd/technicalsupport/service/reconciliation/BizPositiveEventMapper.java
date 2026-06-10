package it.gov.pagopa.gpd.technicalsupport.service.reconciliation;

import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.biz.BizPositiveEvent;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.biz.cosmos.BizPositiveEventDocument;
import org.springframework.stereotype.Component;

@Component
public class BizPositiveEventMapper {

  public BizPositiveEvent toDomain(BizPositiveEventDocument document) {
    return new BizPositiveEvent(
        document.id(),
        document.receiptId(),
        document.creditor() == null ? null : document.creditor().idPA(),
        document.debtorPosition() == null ? null : document.debtorPosition().noticeNumber(),
        document.debtorPosition() == null ? null : document.debtorPosition().iuv(),
        resolveIur(document),
        document.paymentInfo() == null ? null : document.paymentInfo().paymentDateTime(),
        document.timestamp(),
        document.eventStatus(),
        document.properties() == null ? null : document.properties().serviceIdentifier());
  }

  private String resolveIur(BizPositiveEventDocument document) {
    if (document.debtorPosition() != null && document.debtorPosition().iur() != null) {
      return document.debtorPosition().iur();
    }

    if (document.paymentInfo() != null) {
      return document.paymentInfo().IUR();
    }

    return null;
  }
}