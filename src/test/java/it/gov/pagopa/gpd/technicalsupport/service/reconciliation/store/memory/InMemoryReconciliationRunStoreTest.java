package it.gov.pagopa.gpd.technicalsupport.service.reconciliation.store.memory;

import static org.assertj.core.api.Assertions.assertThat;

import it.gov.pagopa.gpd.technicalsupport.model.gpd.ServiceType;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.ReconciliationRunCreationResult;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.ReconciliationRunStatus;
import it.gov.pagopa.gpd.technicalsupport.service.reconciliation.ReconciliationCounters;
import it.gov.pagopa.gpd.technicalsupport.service.reconciliation.key.ReconciliationKeyBuilder;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class InMemoryReconciliationRunStoreTest {

  private final InMemoryReconciliationRunStore store =
      new InMemoryReconciliationRunStore(new ReconciliationKeyBuilder(), Clock.systemUTC());
  private final LocalDate day = LocalDate.parse("2026-05-20");
  private final List<ServiceType> services = List.of(ServiceType.GPD);

  @ParameterizedTest
  @CsvSource({"false,false", "false,true", "true,false", "true,true"})
  void activeRun_shouldKeepOwner(boolean running, boolean force) {
    ReconciliationRunCreationResult first = store.createOrEvaluateRun(day, services, false);
    if (running) {
      store.markRunning(first.logicalRunKey(), first.executionId());
    }

    ReconciliationRunCreationResult second = store.createOrEvaluateRun(day, services, force);

    assertThat(second.shouldStart()).isFalse();
    assertThat(second.executionId()).isEqualTo(first.executionId());
    assertThat(second.status()).isEqualTo(running
        ? ReconciliationRunStatus.RUNNING : ReconciliationRunStatus.CREATED);
  }

  @Test
  void simultaneousSubmissions_shouldDispatchExactlyOneWorker() throws Exception {
    var executor = Executors.newFixedThreadPool(8);
    var start = new CountDownLatch(1);
    try {
      List<Future<ReconciliationRunCreationResult>> futures = new ArrayList<>();
      for (int i = 0; i < 8; i++) {
        futures.add(executor.submit(() -> {
          assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
          return store.createOrEvaluateRun(day, services, false);
        }));
      }
      start.countDown();
      int workers = 0;
      for (Future<ReconciliationRunCreationResult> future : futures) {
        if (future.get(10, TimeUnit.SECONDS).shouldStart()) {
          workers++;
        }
      }
      assertThat(workers).isEqualTo(1);
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  void doneRun_shouldSkipUnlessExplicitlyForced() {
    ReconciliationRunCreationResult first = store.createOrEvaluateRun(day, services, false);
    store.markDone(first.logicalRunKey(), first.executionId(), ReconciliationCounters.empty());

    ReconciliationRunCreationResult skipped = store.createOrEvaluateRun(day, services, false);
    assertThat(skipped.status()).isEqualTo(ReconciliationRunStatus.SKIPPED);
    assertThat(skipped.shouldStart()).isFalse();
    assertThat(store.createOrEvaluateRun(day, services, true).shouldStart()).isTrue();
  }
}
