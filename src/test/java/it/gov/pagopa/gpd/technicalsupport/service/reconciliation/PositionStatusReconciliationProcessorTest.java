package it.gov.pagopa.gpd.technicalsupport.service.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import it.gov.pagopa.gpd.technicalsupport.config.reconciliation.ReconciliationProperties;
import it.gov.pagopa.gpd.technicalsupport.model.gpd.DebtPositionStatus;
import it.gov.pagopa.gpd.technicalsupport.model.gpd.PaymentOptionStatus;
import it.gov.pagopa.gpd.technicalsupport.model.gpd.ServiceType;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.ReconciliationRunCreationResult;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.ReconciliationRunStatus;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.apd.ReconciliationCandidate;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.biz.BizPositiveEvent;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.biz.BizPositiveEventLookupResult;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.cosmos.ReconciliationReportDocument;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.gpd.GpdPayRecoveryResult;
import it.gov.pagopa.gpd.technicalsupport.service.reconciliation.key.ReconciliationReportKeyBuilder;
import it.gov.pagopa.gpd.technicalsupport.service.reconciliation.lookup.BizPositiveEventLookup;
import it.gov.pagopa.gpd.technicalsupport.service.reconciliation.mapper.ReconciliationReportDocumentMapper;
import it.gov.pagopa.gpd.technicalsupport.service.reconciliation.pay.GpdPayClient;
import it.gov.pagopa.gpd.technicalsupport.service.reconciliation.reader.PaymentOptionCandidateReader;
import it.gov.pagopa.gpd.technicalsupport.service.reconciliation.store.ReconciliationReportStore;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class PositionStatusReconciliationProcessorTest {

  private static final int CANDIDATE_CHUNK_SIZE = 500;

  private final PaymentOptionCandidateReader candidateReader =
      Mockito.mock(PaymentOptionCandidateReader.class);

  private final BizPositiveEventLookup bizPositiveEventLookup =
      Mockito.mock(BizPositiveEventLookup.class);

  private final GpdPayClient gpdPayClient =
      Mockito.mock(GpdPayClient.class);

  private final ReconciliationReportStore reportStore =
      Mockito.mock(ReconciliationReportStore.class);

  private final ReconciliationProperties properties = reconciliationProperties();

  private final ReconciliationReportDocumentMapper reportMapper =
      new ReconciliationReportDocumentMapper(new ReconciliationReportKeyBuilder(properties));

  private final Clock clock =
      Clock.fixed(Instant.parse("2026-05-26T10:00:00Z"), ZoneOffset.UTC);

  private final PositionStatusReconciliationProcessor processor =
      new PositionStatusReconciliationProcessor(
          candidateReader,
          bizPositiveEventLookup,
          gpdPayClient,
          reportStore,
          reportMapper,
          properties,
          clock);

  @Test
  void process_shouldLoadCandidatesLookupBizEventExecutePayAndWriteRecoveredReport() {
    ReconciliationRunCreationResult run = run();
    ReconciliationCandidate candidate = candidate();
    BizPositiveEvent event = bizPositiveEvent(candidate);

    mockCandidateReader(run, List.of(candidate));

    when(bizPositiveEventLookup.findPositiveEvents(List.of(candidate)))
        .thenReturn(
            Map.of(
                BizPositiveEventLookup.key(candidate),
                BizPositiveEventLookupResult.found(event)));

    when(gpdPayClient.executePayRecovery(candidate, event))
        .thenReturn(GpdPayRecoveryResult.success());

    ReconciliationCounters counters = processor.process(run);

    assertThat(counters.scanned()).isEqualTo(1);
    assertThat(counters.positiveEventsFound()).isEqualTo(1);
    assertThat(counters.reconciliationCases()).isEqualTo(1);
    assertThat(counters.recovered()).isEqualTo(1);
    assertThat(counters.manualRequired()).isZero();
    assertThat(counters.technicalFailures()).isZero();
    assertThat(counters.payExecuted()).isEqualTo(1);
    assertThat(counters.payFailed()).isZero();
    assertThat(counters.notRecovered()).isZero();

    verifyCandidateReaderCalled(run);
    verify(bizPositiveEventLookup).findPositiveEvents(List.of(candidate));
    verify(bizPositiveEventLookup, never()).findPositiveEvent(Mockito.any());
    verify(gpdPayClient).executePayRecovery(candidate, event);
    verify(reportStore).save(Mockito.any(ReconciliationReportDocument.class));
  }

  @Test
  void process_shouldNotWriteReportWhenBizEventIsNotFound() {
    ReconciliationRunCreationResult run = run();
    ReconciliationCandidate candidate = candidate();

    mockCandidateReader(run, List.of(candidate));

    when(bizPositiveEventLookup.findPositiveEvents(List.of(candidate)))
        .thenReturn(Map.of());

    ReconciliationCounters counters = processor.process(run);

    assertThat(counters.scanned()).isEqualTo(1);
    assertThat(counters.positiveEventsFound()).isZero();
    assertThat(counters.reconciliationCases()).isZero();
    assertThat(counters.recovered()).isZero();
    assertThat(counters.manualRequired()).isZero();
    assertThat(counters.technicalFailures()).isZero();
    assertThat(counters.payExecuted()).isZero();
    assertThat(counters.payFailed()).isZero();
    assertThat(counters.notRecovered()).isZero();

    verifyCandidateReaderCalled(run);
    verify(bizPositiveEventLookup).findPositiveEvents(List.of(candidate));
    verify(bizPositiveEventLookup, never()).findPositiveEvent(Mockito.any());
    verify(gpdPayClient, never()).executePayRecovery(Mockito.any(), Mockito.any());
    verify(reportStore, never()).save(Mockito.any());
  }

  @Test
  void process_shouldWriteTechnicalFailureReportWhenBizLookupFails() {
    ReconciliationRunCreationResult run = run();
    ReconciliationCandidate candidate = candidate();

    mockCandidateReader(run, List.of(candidate));

    when(bizPositiveEventLookup.findPositiveEvents(List.of(candidate)))
        .thenReturn(
            Map.of(
                BizPositiveEventLookup.key(candidate),
                BizPositiveEventLookupResult.failed("CosmosException", "Biz lookup failed")));

    ReconciliationCounters counters = processor.process(run);

    assertThat(counters.scanned()).isEqualTo(1);
    assertThat(counters.positiveEventsFound()).isZero();
    assertThat(counters.reconciliationCases()).isEqualTo(1);
    assertThat(counters.recovered()).isZero();
    assertThat(counters.manualRequired()).isZero();
    assertThat(counters.technicalFailures()).isEqualTo(1);
    assertThat(counters.payExecuted()).isZero();
    assertThat(counters.payFailed()).isZero();
    assertThat(counters.notRecovered()).isEqualTo(1);

    verifyCandidateReaderCalled(run);
    verify(bizPositiveEventLookup).findPositiveEvents(List.of(candidate));
    verify(bizPositiveEventLookup, never()).findPositiveEvent(Mockito.any());
    verify(gpdPayClient, never()).executePayRecovery(Mockito.any(), Mockito.any());
    verify(reportStore).save(Mockito.any(ReconciliationReportDocument.class));
  }

  @Test
  void process_shouldWritePayFailedReportWhenPayRecoveryFails() {
    ReconciliationRunCreationResult run = run();
    ReconciliationCandidate candidate = candidate();
    BizPositiveEvent event = bizPositiveEvent(candidate);

    mockCandidateReader(run, List.of(candidate));

    when(bizPositiveEventLookup.findPositiveEvents(List.of(candidate)))
        .thenReturn(
            Map.of(
                BizPositiveEventLookup.key(candidate),
                BizPositiveEventLookupResult.found(event)));

    when(gpdPayClient.executePayRecovery(candidate, event))
        .thenReturn(GpdPayRecoveryResult.failed("PayRecoveryException", "PAY recovery failed"));

    ReconciliationCounters counters = processor.process(run);

    assertThat(counters.scanned()).isEqualTo(1);
    assertThat(counters.positiveEventsFound()).isEqualTo(1);
    assertThat(counters.reconciliationCases()).isEqualTo(1);
    assertThat(counters.recovered()).isZero();
    assertThat(counters.manualRequired()).isZero();
    assertThat(counters.technicalFailures()).isEqualTo(1);
    assertThat(counters.payExecuted()).isZero();
    assertThat(counters.payFailed()).isEqualTo(1);
    assertThat(counters.notRecovered()).isEqualTo(1);

    verifyCandidateReaderCalled(run);
    verify(bizPositiveEventLookup).findPositiveEvents(List.of(candidate));
    verify(bizPositiveEventLookup, never()).findPositiveEvent(Mockito.any());
    verify(gpdPayClient).executePayRecovery(candidate, event);
    verify(reportStore).save(Mockito.any(ReconciliationReportDocument.class));
  }

  @Test
  void process_shouldWriteManualRequiredReportWhenPositiveEventFoundForExpiredPosition() {
    ReconciliationRunCreationResult run = run();
    ReconciliationCandidate candidate = candidate(DebtPositionStatus.EXPIRED);
    BizPositiveEvent event = bizPositiveEvent(candidate);

    mockCandidateReader(run, List.of(candidate));

    when(bizPositiveEventLookup.findPositiveEvents(List.of(candidate)))
        .thenReturn(
            Map.of(
                BizPositiveEventLookup.key(candidate),
                BizPositiveEventLookupResult.found(event)));

    ReconciliationCounters counters = processor.process(run);

    assertThat(counters.scanned()).isEqualTo(1);
    assertThat(counters.positiveEventsFound()).isEqualTo(1);
    assertThat(counters.reconciliationCases()).isEqualTo(1);
    assertThat(counters.recovered()).isZero();
    assertThat(counters.manualRequired()).isEqualTo(1);
    assertThat(counters.technicalFailures()).isZero();
    assertThat(counters.payExecuted()).isZero();
    assertThat(counters.payFailed()).isZero();
    assertThat(counters.notRecovered()).isEqualTo(1);

    verifyCandidateReaderCalled(run);
    verify(bizPositiveEventLookup).findPositiveEvents(List.of(candidate));
    verify(bizPositiveEventLookup, never()).findPositiveEvent(Mockito.any());
    verify(gpdPayClient, never()).executePayRecovery(Mockito.any(), Mockito.any());
    verify(reportStore).save(Mockito.any(ReconciliationReportDocument.class));
  }

  @Test
  void process_shouldWriteManualRequiredReportWhenPositiveEventFoundForInvalidPosition() {
    ReconciliationRunCreationResult run = run();
    ReconciliationCandidate candidate = candidate(DebtPositionStatus.INVALID);
    BizPositiveEvent event = bizPositiveEvent(candidate);

    mockCandidateReader(run, List.of(candidate));

    when(bizPositiveEventLookup.findPositiveEvents(List.of(candidate)))
        .thenReturn(
            Map.of(
                BizPositiveEventLookup.key(candidate),
                BizPositiveEventLookupResult.found(event)));

    ReconciliationCounters counters = processor.process(run);

    assertThat(counters.scanned()).isEqualTo(1);
    assertThat(counters.positiveEventsFound()).isEqualTo(1);
    assertThat(counters.reconciliationCases()).isEqualTo(1);
    assertThat(counters.recovered()).isZero();
    assertThat(counters.manualRequired()).isEqualTo(1);
    assertThat(counters.technicalFailures()).isZero();
    assertThat(counters.payExecuted()).isZero();
    assertThat(counters.payFailed()).isZero();
    assertThat(counters.notRecovered()).isEqualTo(1);

    verifyCandidateReaderCalled(run);
    verify(bizPositiveEventLookup).findPositiveEvents(List.of(candidate));
    verify(bizPositiveEventLookup, never()).findPositiveEvent(Mockito.any());
    verify(gpdPayClient, never()).executePayRecovery(Mockito.any(), Mockito.any());
    verify(reportStore).save(Mockito.any(ReconciliationReportDocument.class));
  }

  @Test
  void process_shouldAccumulateCountersAcrossMultipleChunks() {
    ReconciliationRunCreationResult run = run();

    ReconciliationCandidate notFoundCandidate =
        candidate(
            "payment-position-id-1",
            "payment-option-id-1",
            "300000000000000001",
            "00000000000000001",
            DebtPositionStatus.VALID);

    ReconciliationCandidate recoveredCandidate =
        candidate(
            "payment-position-id-2",
            "payment-option-id-2",
            "300000000000000002",
            "00000000000000002",
            DebtPositionStatus.VALID);

    ReconciliationCandidate manualRequiredCandidate =
        candidate(
            "payment-position-id-3",
            "payment-option-id-3",
            "300000000000000003",
            "00000000000000003",
            DebtPositionStatus.EXPIRED);

    ReconciliationCandidate payFailedCandidate =
        candidate(
            "payment-position-id-4",
            "payment-option-id-4",
            "300000000000000004",
            "00000000000000004",
            DebtPositionStatus.VALID);

    BizPositiveEvent recoveredEvent = bizPositiveEvent(recoveredCandidate);
    BizPositiveEvent manualRequiredEvent = bizPositiveEvent(manualRequiredCandidate);
    BizPositiveEvent payFailedEvent = bizPositiveEvent(payFailedCandidate);

    List<ReconciliationCandidate> firstChunk =
        List.of(notFoundCandidate, recoveredCandidate);

    List<ReconciliationCandidate> secondChunk =
        List.of(manualRequiredCandidate, payFailedCandidate);

    mockCandidateReaderChunks(run, firstChunk, secondChunk);

    when(bizPositiveEventLookup.findPositiveEvents(firstChunk))
        .thenReturn(
            Map.of(
                BizPositiveEventLookup.key(recoveredCandidate),
                BizPositiveEventLookupResult.found(recoveredEvent)));

    when(bizPositiveEventLookup.findPositiveEvents(secondChunk))
        .thenReturn(
            Map.of(
                BizPositiveEventLookup.key(manualRequiredCandidate),
                BizPositiveEventLookupResult.found(manualRequiredEvent),
                BizPositiveEventLookup.key(payFailedCandidate),
                BizPositiveEventLookupResult.found(payFailedEvent)));

    when(gpdPayClient.executePayRecovery(recoveredCandidate, recoveredEvent))
        .thenReturn(GpdPayRecoveryResult.success());

    when(gpdPayClient.executePayRecovery(payFailedCandidate, payFailedEvent))
        .thenReturn(GpdPayRecoveryResult.failed("PayRecoveryException", "PAY recovery failed"));

    ReconciliationCounters counters = processor.process(run);

    assertThat(counters.scanned()).isEqualTo(4);
    assertThat(counters.positiveEventsFound()).isEqualTo(3);
    assertThat(counters.reconciliationCases()).isEqualTo(3);
    assertThat(counters.recovered()).isEqualTo(1);
    assertThat(counters.manualRequired()).isEqualTo(1);
    assertThat(counters.technicalFailures()).isEqualTo(1);
    assertThat(counters.payExecuted()).isEqualTo(1);
    assertThat(counters.payFailed()).isEqualTo(1);
    assertThat(counters.notRecovered()).isEqualTo(2);

    verifyCandidateReaderCalled(run);
    verify(bizPositiveEventLookup).findPositiveEvents(firstChunk);
    verify(bizPositiveEventLookup).findPositiveEvents(secondChunk);
    verify(bizPositiveEventLookup, never()).findPositiveEvent(Mockito.any());
    verify(reportStore, Mockito.times(3)).save(Mockito.any(ReconciliationReportDocument.class));
  }

  private ReconciliationProperties reconciliationProperties() {
    ReconciliationProperties prop = new ReconciliationProperties();
    prop.setCandidateChunkSize(CANDIDATE_CHUNK_SIZE);
    return prop;
  }

  private void mockCandidateReader(
      ReconciliationRunCreationResult run,
      List<ReconciliationCandidate> candidates) {

    mockCandidateReaderChunks(run, candidates);
  }

  @SafeVarargs
  private final void mockCandidateReaderChunks(
      ReconciliationRunCreationResult run,
      List<ReconciliationCandidate>... chunks) {

    doAnswer(
            invocation -> {
              Consumer<List<ReconciliationCandidate>> chunkConsumer =
                  invocation.getArgument(3);

              for (List<ReconciliationCandidate> chunk : chunks) {
                chunkConsumer.accept(chunk);
              }

              return null;
            })
        .when(candidateReader)
        .forEachCandidateChunk(
            eq(run.day()),
            eq(run.serviceTypes()),
            anyInt(),
            any());
  }

  private void verifyCandidateReaderCalled(ReconciliationRunCreationResult run) {
    verify(candidateReader)
        .forEachCandidateChunk(
            eq(run.day()),
            eq(run.serviceTypes()),
            eq(properties.getCandidateChunkSize()),
            any());
  }

  private ReconciliationRunCreationResult run() {
    return new ReconciliationRunCreationResult(
        LocalDate.of(2026, 5, 20),
        List.of(ServiceType.WISP, ServiceType.GPD),
        "2026-05-20__GPD|WISP",
        "2026-05-20__GPD|WISP__20260526T100000Z",
        ReconciliationRunStatus.CREATED,
        true);
  }

  private ReconciliationCandidate candidate() {
    return candidate(DebtPositionStatus.VALID);
  }

  private ReconciliationCandidate candidate(DebtPositionStatus ppStatus) {
    return candidate("payment-position-id", "payment-option-id", ppStatus);
  }

  private ReconciliationCandidate candidate(
      String paymentPositionId,
      String paymentOptionId,
      DebtPositionStatus ppStatus) {

    return new ReconciliationCandidate(
        LocalDate.of(2026, 5, 20),
        ServiceType.GPD,
        paymentPositionId,
        paymentOptionId,
        "77777777777",
        "302131563536065220",
        "02131563536065220",
        ppStatus,
        PaymentOptionStatus.PO_UNPAID,
        "payment-plan-id");
  }
  
  private ReconciliationCandidate candidate(
		  String paymentPositionId,
		  String paymentOptionId,
		  String nav,
		  String iuv,
		  DebtPositionStatus ppStatus) {

	  return new ReconciliationCandidate(
			  LocalDate.of(2026, 5, 20),
			  ServiceType.GPD,
			  paymentPositionId,
			  paymentOptionId,
			  "77777777777",
			  nav,
			  iuv,
			  ppStatus,
			  PaymentOptionStatus.PO_UNPAID,
			  "payment-plan-id");
  }

  private BizPositiveEvent bizPositiveEvent(ReconciliationCandidate candidate) {
    return new BizPositiveEvent(
        "biz-event-id-" + candidate.paymentOptionId(),
        "receipt-id-" + candidate.paymentOptionId(),
        candidate.ec(),
        candidate.nav(),
        candidate.iuv(),
        "iur",
        "2026-06-10T14:50:20.524566",
        "other",
        "0.0",
        1781168213044L,
        "DONE",
        "TEST",
        "ABI03034",
        "91010030400",
        "Banca Agricola Commerciale SpA",
        "97249640588",
        "97249640588_01");
  }
}
