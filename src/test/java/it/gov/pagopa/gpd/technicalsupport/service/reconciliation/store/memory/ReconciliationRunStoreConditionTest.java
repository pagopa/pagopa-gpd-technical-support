package it.gov.pagopa.gpd.technicalsupport.service.reconciliation.store.memory;

import static org.assertj.core.api.Assertions.assertThat;

import it.gov.pagopa.gpd.technicalsupport.service.reconciliation.key.ReconciliationKeyBuilder;
import it.gov.pagopa.gpd.technicalsupport.service.reconciliation.mapper.ReconciliationRunDocumentMapper;
import it.gov.pagopa.gpd.technicalsupport.service.reconciliation.store.ReconciliationRunStore;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ReconciliationRunStoreConditionTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of())
          .withBean(ReconciliationKeyBuilder.class)
          .withBean(ReconciliationRunDocumentMapper.class)
          .withBean(Clock.class, Clock::systemUTC)
          .withUserConfiguration(InMemoryReconciliationRunStore.class);

  @Test
  void inMemoryStore_shouldBeCreatedWhenCosmosIsDisabled() {
    contextRunner
        .withPropertyValues("reconciliation.cosmos.enabled=false")
        .run(
            context ->
                assertThat(context)
                    .hasSingleBean(ReconciliationRunStore.class)
                    .hasSingleBean(InMemoryReconciliationRunStore.class));
  }

  @Test
  void inMemoryStore_shouldBeCreatedWhenCosmosPropertyIsMissing() {
    contextRunner.run(
        context ->
            assertThat(context)
                .hasSingleBean(ReconciliationRunStore.class)
                .hasSingleBean(InMemoryReconciliationRunStore.class));
  }

  @Test
  void inMemoryStore_shouldNotBeCreatedWhenCosmosIsEnabled() {
    contextRunner
        .withPropertyValues("reconciliation.cosmos.enabled=true")
        .run(context -> assertThat(context).doesNotHaveBean(InMemoryReconciliationRunStore.class));
  }
}