package it.gov.pagopa.gpd.technicalsupport.service.reconciliation;

import it.gov.pagopa.gpd.technicalsupport.model.gpd.DebtPositionStatus;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.ReconciliationOutcome;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.ReconciliationRunCreationResult;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.apd.ReconciliationCandidate;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.biz.BizPositiveEventLookupResult;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.biz.BizPositiveEventLookupStatus;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.cosmos.ReconciliationReportDocument;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.gpd.GpdPayRecoveryResult;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class PositionStatusReconciliationProcessor {

  private final PaymentOptionCandidateReader candidateReader;
  private final BizPositiveEventLookup bizPositiveEventLookup;
  private final GpdPayClient gpdPayClient;
  private final ReconciliationReportStore reportStore;
  private final ReconciliationReportDocumentMapper reportMapper;
  private final Clock clock;

  public ReconciliationCounters process(ReconciliationRunCreationResult run) {
    List<ReconciliationCandidate> candidates =
        candidateReader.findCandidates(run.day(), run.serviceTypes());

    log.info(
        "APD reconciliation candidates loaded. logicalRunKey={}, executionId={}, candidates={}",
        run.logicalRunKey(),
        run.executionId(),
        candidates.size());

    long positiveEventsFound = 0;
    long recovered = 0;
    long manualRequired = 0;
    long technicalFailures = 0;
    long payExecuted = 0;
    long payFailed = 0;

    for (ReconciliationCandidate candidate : candidates) {
      BizPositiveEventLookupResult lookupResult =
          bizPositiveEventLookup.findPositiveEvent(candidate);

      if (lookupResult.status() == BizPositiveEventLookupStatus.NOT_FOUND) {
        continue;
      }

      OffsetDateTime now = nowUtc();

      if (lookupResult.status() == BizPositiveEventLookupStatus.FAILED) {
        technicalFailures++;

        ReconciliationReportDocument report =
            reportMapper.technicalFailureReport(
                run.executionId(),
                run.logicalRunKey(),
                candidate,
                lookupResult.errorCode(),
                lookupResult.errorMessage(),
                now);

        reportStore.save(report);
        continue;
      }

      positiveEventsFound++;

      if (candidate.ppStatus() == DebtPositionStatus.EXPIRED) {
        manualRequired++;

        ReconciliationReportDocument report =
            reportMapper.manualRequiredReport(
                run.executionId(),
                run.logicalRunKey(),
                candidate,
                lookupResult.event(),
                ReconciliationOutcome.POSITIVE_EVENT_FOUND_EXPIRED_MANUAL_REQUIRED,
                now);

        reportStore.save(report);
        continue;
      }

      if (candidate.ppStatus() == DebtPositionStatus.INVALID) {
        manualRequired++;

        ReconciliationReportDocument report =
            reportMapper.manualRequiredReport(
                run.executionId(),
                run.logicalRunKey(),
                candidate,
                lookupResult.event(),
                ReconciliationOutcome.POSITIVE_EVENT_FOUND_INVALID_MANUAL_REQUIRED,
                now);

        reportStore.save(report);
        continue;
      }

      GpdPayRecoveryResult payResult =
          gpdPayClient.executePayRecovery(candidate, lookupResult.event());

      if (!payResult.succeeded()) {
        technicalFailures++;
        payFailed++;

        ReconciliationReportDocument report =
            reportMapper.payFailedReport(
                run.executionId(),
                run.logicalRunKey(),
                candidate,
                lookupResult.event(),
                payResult.errorCode(),
                payResult.errorMessage(),
                nowUtc());

        reportStore.save(report);
        continue;
      }

      payExecuted++;
      recovered++;

      ReconciliationReportDocument report =
          reportMapper.recoveredReport(
              run.executionId(),
              run.logicalRunKey(),
              candidate,
              lookupResult.event(),
              nowUtc());

      reportStore.save(report);
    }

    return new ReconciliationCounters(
        candidates.size(),
        positiveEventsFound,
        recovered + manualRequired + technicalFailures,
        recovered,
        manualRequired,
        technicalFailures,
        payExecuted,
        payFailed);
  }

  private OffsetDateTime nowUtc() {
    return OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC);
  }
}