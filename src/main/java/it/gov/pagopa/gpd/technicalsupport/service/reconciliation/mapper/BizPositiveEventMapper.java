package it.gov.pagopa.gpd.technicalsupport.service.reconciliation.mapper;

import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.biz.BizPositiveEvent;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.biz.cosmos.BizPositiveEventDocument;
import org.springframework.stereotype.Component;

@Component
public class BizPositiveEventMapper {

  public BizPositiveEvent toDomain(BizPositiveEventDocument document) {
    return new BizPositiveEvent(
        document.id(),
        document.receiptId(),
        resolveEc(document),
        resolveNav(document),
        resolveIuv(document),
        resolveIur(document),
        resolvePaymentDateTime(document),
        resolvePaymentMethod(document),
        resolveFee(document),
        document.timestamp(),
        document.eventStatus(),
        resolveServiceIdentifier(document),
        resolvePspCode(document),
        resolvePspTaxCode(document),
        resolvePspCompany(document),
        resolveIdBrokerPsp(document),
        resolveIdChannel(document));
  }

  private String resolveEc(BizPositiveEventDocument document) {
    return document.creditor() == null ? null : document.creditor().idPA();
  }

  private String resolveNav(BizPositiveEventDocument document) {
    return document.debtorPosition() == null ? null : document.debtorPosition().noticeNumber();
  }

  private String resolveIuv(BizPositiveEventDocument document) {
    return document.debtorPosition() == null ? null : document.debtorPosition().iuv();
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

  private String resolvePaymentDateTime(BizPositiveEventDocument document) {
    return document.paymentInfo() == null ? null : document.paymentInfo().paymentDateTime();
  }

  private String resolvePaymentMethod(BizPositiveEventDocument document) {
    return document.paymentInfo() == null ? null : document.paymentInfo().paymentMethod();
  }

  private String resolveFee(BizPositiveEventDocument document) {
    return document.paymentInfo() == null ? null : document.paymentInfo().fee();
  }

  private String resolveServiceIdentifier(BizPositiveEventDocument document) {
    return document.properties() == null ? null : document.properties().serviceIdentifier();
  }

  private String resolvePspCode(BizPositiveEventDocument document) {
    return document.psp() == null ? null : document.psp().idPsp();
  }

  private String resolvePspTaxCode(BizPositiveEventDocument document) {
    if (document.psp() == null) {
      return null;
    }

    if (document.psp().pspFiscalCode() != null && !document.psp().pspFiscalCode().isBlank()) {
      return document.psp().pspFiscalCode();
    }

    return document.psp().pspPartitaIVA();
  }

  private String resolvePspCompany(BizPositiveEventDocument document) {
    return document.psp() == null ? null : document.psp().psp();
  }

  private String resolveIdBrokerPsp(BizPositiveEventDocument document) {
    return document.psp() == null ? null : document.psp().idBrokerPsp();
  }

  private String resolveIdChannel(BizPositiveEventDocument document) {
    return document.psp() == null ? null : document.psp().idChannel();
  }
}