package it.gov.pagopa.gpd.technicalsupport.service.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

import it.gov.pagopa.gpd.technicalsupport.model.gpd.ServiceType;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.PositionStatusReconciliationRequest;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.PositionStatusReconciliationResponse;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.ReconciliationRunCreationResult;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.ReconciliationRunStatus;
import it.gov.pagopa.gpd.technicalsupport.service.reconciliation.store.ReconciliationRunStore;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class PositionStatusReconciliationOrchestratorTest {

  private final PositionStatusReconciliationValidator validator =
      mock(PositionStatusReconciliationValidator.class);

  private final ReconciliationRunStore runStore =
      mock(ReconciliationRunStore.class);

  private final PositionStatusReconciliationRunner runner =
      mock(PositionStatusReconciliationRunner.class);

  private final PositionStatusReconciliationOrchestrator orchestrator =
      new PositionStatusReconciliationOrchestrator(validator, runStore, runner);

  @Test
  void start_shouldValidateRequestCreateRunAndStartRunnerWhenRunShouldStart() {
    PositionStatusReconciliationRequest request = request(true);

    ReconciliationRunCreationResult run =
        new ReconciliationRunCreationResult(
            LocalDate.of(2026, 6, 10),
            List.of(ServiceType.GPD),
            "2026-06-10__GPD",
            "2026-06-10__GPD__20260615T145835Z",
            ReconciliationRunStatus.CREATED,
            true);

    when(runStore.createOrEvaluateRun(
            LocalDate.of(2026, 6, 10),
            List.of(ServiceType.GPD),
            true))
        .thenReturn(run);

    PositionStatusReconciliationResponse response = orchestrator.start(request);

    assertThat(response.accepted()).isTrue();
    assertThat(response.runs()).hasSize(1);
    assertThat(response.runs().get(0).day()).isEqualTo(LocalDate.of(2026, 6, 10));
    assertThat(response.runs().get(0).serviceTypes()).containsExactly(ServiceType.GPD);
    assertThat(response.runs().get(0).logicalRunKey()).isEqualTo("2026-06-10__GPD");
    assertThat(response.runs().get(0).executionId())
        .isEqualTo("2026-06-10__GPD__20260615T145835Z");
    assertThat(response.runs().get(0).status()).isEqualTo(ReconciliationRunStatus.CREATED);

    verify(validator).validate(request);
    verify(runStore)
        .createOrEvaluateRun(
            LocalDate.of(2026, 6, 10),
            List.of(ServiceType.GPD),
            true);
    verify(runner).run(run);
  }

  @Test
  void start_shouldNotStartRunnerWhenRunShouldNotStart() {
    PositionStatusReconciliationRequest request = request(false);

    ReconciliationRunCreationResult run =
        new ReconciliationRunCreationResult(
            LocalDate.of(2026, 6, 10),
            List.of(ServiceType.GPD),
            "2026-06-10__GPD",
            "2026-06-10__GPD__20260615T145835Z",
            ReconciliationRunStatus.RUNNING,
            false);

    when(runStore.createOrEvaluateRun(
            LocalDate.of(2026, 6, 10),
            List.of(ServiceType.GPD),
            false))
        .thenReturn(run);

    PositionStatusReconciliationResponse response = orchestrator.start(request);

    assertThat(response.accepted()).isTrue();
    assertThat(response.runs()).hasSize(1);
    assertThat(response.runs().get(0).status()).isEqualTo(ReconciliationRunStatus.RUNNING);

    verify(validator).validate(request);
    verify(runStore)
        .createOrEvaluateRun(
            LocalDate.of(2026, 6, 10),
            List.of(ServiceType.GPD),
            false);
    verify(runner, never()).run(Mockito.any());
  }

  private PositionStatusReconciliationRequest request(boolean force) {
    return new PositionStatusReconciliationRequest(
        LocalDate.of(2026, 6, 10),
        LocalDate.of(2026, 6, 10),
        List.of(ServiceType.GPD),
        force);
  }
}