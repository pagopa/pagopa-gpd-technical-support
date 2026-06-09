package it.gov.pagopa.gpd.technicalsupport.model.reconciliation.cosmos;

import it.gov.pagopa.gpd.technicalsupport.model.gpd.DebtPositionStatus;
import it.gov.pagopa.gpd.technicalsupport.model.gpd.PaymentOptionStatus;
import it.gov.pagopa.gpd.technicalsupport.model.gpd.ServiceType;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.ReconciliationOutcome;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.ReconciliationStatus;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import lombok.Builder;

@Builder(toBuilder = true)
public record ReconciliationReportDocument(
    String id,
    String pk, // pk field: day__serviceType__bucket
    String executionId,
    String logicalRunKey,
    LocalDate day,
    ServiceType serviceType,
    int bucket,
    String paymentPositionId,
    String paymentOptionId,
    String ec,
    String nav,
    String ecNavKey,
    String iuv,
    String bizId,
    DebtPositionStatus ppStatus,
    PaymentOptionStatus poStatus,
    ReconciliationStatus reconciliationStatus,
    ReconciliationOutcome outcome,
    boolean payInvoked,
    boolean paySucceeded,
    String errorCode,
    String errorMessage,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}