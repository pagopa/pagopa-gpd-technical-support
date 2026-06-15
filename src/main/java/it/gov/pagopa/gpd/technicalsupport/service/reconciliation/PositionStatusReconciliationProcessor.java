package it.gov.pagopa.gpd.technicalsupport.service.reconciliation;

import it.gov.pagopa.gpd.technicalsupport.config.reconciliation.ReconciliationProperties;
import it.gov.pagopa.gpd.technicalsupport.model.gpd.DebtPositionStatus;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.ReconciliationOutcome;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.ReconciliationRunCreationResult;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.apd.ReconciliationCandidate;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.biz.BizPositiveEventLookupResult;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.biz.BizPositiveEventLookupStatus;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.cosmos.ReconciliationReportDocument;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.gpd.GpdPayRecoveryResult;
import it.gov.pagopa.gpd.technicalsupport.service.reconciliation.lookup.BizPositiveEventLookup;
import it.gov.pagopa.gpd.technicalsupport.service.reconciliation.mapper.ReconciliationReportDocumentMapper;
import it.gov.pagopa.gpd.technicalsupport.service.reconciliation.pay.GpdPayClient;
import it.gov.pagopa.gpd.technicalsupport.service.reconciliation.reader.PaymentOptionCandidateReader;
import it.gov.pagopa.gpd.technicalsupport.service.reconciliation.store.ReconciliationReportStore;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

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
  private final ReconciliationProperties reconciliationProperties;
  private final Clock clock;

  public ReconciliationCounters process(ReconciliationRunCreationResult run) {
    ReconciliationCounterAccumulator accumulator = new ReconciliationCounterAccumulator();

    int chunkSize = reconciliationProperties.getCandidateChunkSize();

    candidateReader.forEachCandidateChunk(
        run.day(),
        run.serviceTypes(),
        chunkSize,
        candidates -> processChunk(run, candidates, accumulator));

    ReconciliationCounters counters = accumulator.toCounters();

    log.info(
        "Position status reconciliation processing completed. logicalRunKey={}, executionId={}, scanned={}, positiveEventsFound={}, reconciliationCases={}, recovered={}, manualRequired={}, technicalFailures={}, payExecuted={}, payFailed={}",
        run.logicalRunKey(),
        run.executionId(),
        counters.scanned(),
        counters.positiveEventsFound(),
        counters.reconciliationCases(),
        counters.recovered(),
        counters.manualRequired(),
        counters.technicalFailures(),
        counters.payExecuted(),
        counters.payFailed());

    return counters;
  }

  private void processChunk(
		  ReconciliationRunCreationResult run,
		  List<ReconciliationCandidate> candidates,
		  ReconciliationCounterAccumulator accumulator) {

	  long startTime = System.currentTimeMillis();

	  log.info(
			  "Processing APD reconciliation candidate chunk. logicalRunKey={}, executionId={}, chunkSize={}",
			  run.logicalRunKey(),
			  run.executionId(),
			  candidates.size());

	  Map<String, BizPositiveEventLookupResult> lookupResults =
			  bizPositiveEventLookup.findPositiveEvents(candidates);

	  for (ReconciliationCandidate candidate : candidates) {
		  BizPositiveEventLookupResult lookupResult =
				  lookupResults.getOrDefault(
						  BizPositiveEventLookup.key(candidate),
						  BizPositiveEventLookupResult.notFound());

		  ReconciliationCandidateProcessingResult result =
				  processCandidate(run, candidate, lookupResult);

		  accumulator.add(result);
	  }

	  log.info(
			  "Processed APD reconciliation candidate chunk. logicalRunKey={}, executionId={}, chunkSize={}, lookupResults={}, durationMs={}",
			  run.logicalRunKey(),
			  run.executionId(),
			  candidates.size(),
			  lookupResults.size(),
			  System.currentTimeMillis() - startTime);
  }

  private ReconciliationCandidateProcessingResult processCandidate(
		  ReconciliationRunCreationResult run,
		  ReconciliationCandidate candidate,
		  BizPositiveEventLookupResult lookupResult) {

	  if (lookupResult.status() == BizPositiveEventLookupStatus.NOT_FOUND) {
		  return ReconciliationCandidateProcessingResult.forNotFound();
	  }

	  if (lookupResult.status() == BizPositiveEventLookupStatus.FAILED) {
		  ReconciliationReportDocument report =
				  reportMapper.technicalFailureReport(
						  run.executionId(),
						  run.logicalRunKey(),
						  candidate,
						  lookupResult.errorCode(),
						  lookupResult.errorMessage(),
						  nowUtc());

		  reportStore.save(report);

		  return ReconciliationCandidateProcessingResult.forTechnicalFailure();
	  }

	  if (candidate.ppStatus() == DebtPositionStatus.EXPIRED) {
		  ReconciliationReportDocument report =
				  reportMapper.manualRequiredReport(
						  run.executionId(),
						  run.logicalRunKey(),
						  candidate,
						  lookupResult.event(),
						  ReconciliationOutcome.POSITIVE_EVENT_FOUND_EXPIRED_MANUAL_REQUIRED,
						  nowUtc());

		  reportStore.save(report);

		  return ReconciliationCandidateProcessingResult.forManualRequired();
	  }

	  if (candidate.ppStatus() == DebtPositionStatus.INVALID) {
		  ReconciliationReportDocument report =
				  reportMapper.manualRequiredReport(
						  run.executionId(),
						  run.logicalRunKey(),
						  candidate,
						  lookupResult.event(),
						  ReconciliationOutcome.POSITIVE_EVENT_FOUND_INVALID_MANUAL_REQUIRED,
						  nowUtc());

		  reportStore.save(report);

		  return ReconciliationCandidateProcessingResult.forManualRequired();
	  }

	  return executePayRecovery(run, candidate, lookupResult);
  }

  private ReconciliationCandidateProcessingResult executePayRecovery(
      ReconciliationRunCreationResult run,
      ReconciliationCandidate candidate,
      BizPositiveEventLookupResult lookupResult) {

    GpdPayRecoveryResult payResult =
        gpdPayClient.executePayRecovery(candidate, lookupResult.event());

    if (!payResult.succeeded()) {
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

      return ReconciliationCandidateProcessingResult.forPayFailed();
    }

    ReconciliationReportDocument report =
        reportMapper.recoveredReport(
            run.executionId(),
            run.logicalRunKey(),
            candidate,
            lookupResult.event(),
            nowUtc());

    reportStore.save(report);

    return ReconciliationCandidateProcessingResult.forRecovered();
  }

  private OffsetDateTime nowUtc() {
    return OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC);
  }

  private static class ReconciliationCounterAccumulator {

    private long scanned;
    private long positiveEventsFound;
    private long recovered;
    private long manualRequired;
    private long technicalFailures;
    private long payExecuted;
    private long payFailed;

    void add(ReconciliationCandidateProcessingResult result) {
      scanned += result.scanned();
      positiveEventsFound += result.positiveEventsFound();
      recovered += result.recovered();
      manualRequired += result.manualRequired();
      technicalFailures += result.technicalFailures();
      payExecuted += result.payExecuted();
      payFailed += result.payFailed();
    }

    ReconciliationCounters toCounters() {
      return new ReconciliationCounters(
          scanned,
          positiveEventsFound,
          recovered + manualRequired + technicalFailures,
          recovered,
          manualRequired,
          technicalFailures,
          payExecuted,
          payFailed);
    }
  }

  private record ReconciliationCandidateProcessingResult(
      long scanned,
      long positiveEventsFound,
      long recovered,
      long manualRequired,
      long technicalFailures,
      long payExecuted,
      long payFailed) {

    private static final long SCANNED = 1L;

    static ReconciliationCandidateProcessingResult forNotFound() {
      return fromFlags(false, false, false, false, false, false);
    }

    static ReconciliationCandidateProcessingResult forTechnicalFailure() {
      return fromFlags(false, false, false, true, false, false);
    }

    static ReconciliationCandidateProcessingResult forManualRequired() {
      return fromFlags(true, false, true, false, false, false);
    }

    static ReconciliationCandidateProcessingResult forPayFailed() {
      return fromFlags(true, false, false, true, false, true);
    }

    static ReconciliationCandidateProcessingResult forRecovered() {
      return fromFlags(true, true, false, false, true, false);
    }

    private static ReconciliationCandidateProcessingResult fromFlags(
        boolean positiveEventFound,
        boolean recovered,
        boolean manualRequired,
        boolean technicalFailure,
        boolean payExecuted,
        boolean payFailed) {

      return new ReconciliationCandidateProcessingResult(
          SCANNED,
          counter(positiveEventFound),
          counter(recovered),
          counter(manualRequired),
          counter(technicalFailure),
          counter(payExecuted),
          counter(payFailed));
    }

    private static long counter(boolean increment) {
      return increment ? 1L : 0L;
    }
  }
}