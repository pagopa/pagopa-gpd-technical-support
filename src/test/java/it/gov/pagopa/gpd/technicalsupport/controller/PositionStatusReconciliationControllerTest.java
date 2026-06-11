package it.gov.pagopa.gpd.technicalsupport.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import it.gov.pagopa.gpd.technicalsupport.model.gpd.ServiceType;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.PositionStatusReconciliationResponse;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.ReconciliationRunResponse;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.ReconciliationRunStatus;
import it.gov.pagopa.gpd.technicalsupport.service.reconciliation.PositionStatusReconciliationOrchestrator;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PositionStatusReconciliationController.class)
class PositionStatusReconciliationControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private PositionStatusReconciliationOrchestrator orchestrator;

  @Test
  void start_shouldReturnAcceptedAndCreatedRun() throws Exception {
    when(orchestrator.start(any()))
        .thenReturn(
            new PositionStatusReconciliationResponse(
                true,
                List.of(
                    new ReconciliationRunResponse(
                        LocalDate.of(2026, 5, 20),
                        List.of(ServiceType.WISP, ServiceType.GPD),
                        "2026-05-20__GPD|WISP",
                        "2026-05-20__GPD|WISP__20260526T100000Z",
                        ReconciliationRunStatus.CREATED))));

    String request =
        """
        {
          "from": "2026-05-20",
          "to": "2026-05-20",
          "serviceTypes": ["WISP", "GPD"],
          "force": false
        }
        """;

    mockMvc
        .perform(
            post("/internal/position-status-reconciliation")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.accepted", is(true)))
        .andExpect(jsonPath("$.runs", hasSize(1)))
        .andExpect(jsonPath("$.runs[0].day", is("2026-05-20")))
        .andExpect(jsonPath("$.runs[0].logicalRunKey", is("2026-05-20__GPD|WISP")))
        .andExpect(jsonPath("$.runs[0].status", is("CREATED")));
  }
}