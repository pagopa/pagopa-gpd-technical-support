package it.gov.pagopa.gpd.technicalsupport.model.reconciliation.apd;

import it.gov.pagopa.gpd.technicalsupport.model.gpd.DebtPositionStatus;
import it.gov.pagopa.gpd.technicalsupport.model.gpd.PaymentOptionStatus;
import it.gov.pagopa.gpd.technicalsupport.model.gpd.ServiceType;
import java.time.LocalDate;

public record ReconciliationCandidate(
    LocalDate day,
    ServiceType serviceType,
    String paymentPositionId,
    String paymentOptionId,
    String ec,
    String nav,
    String iuv,
    DebtPositionStatus ppStatus,
    PaymentOptionStatus poStatus,
    String paymentPlanId) {}