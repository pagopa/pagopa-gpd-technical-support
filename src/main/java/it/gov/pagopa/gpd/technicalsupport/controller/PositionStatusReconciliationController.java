package it.gov.pagopa.gpd.technicalsupport.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.PositionStatusReconciliationRequest;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.PositionStatusReconciliationResponse;
import it.gov.pagopa.gpd.technicalsupport.service.reconciliation.PositionStatusReconciliationOrchestrator;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class PositionStatusReconciliationController {

  private final PositionStatusReconciliationOrchestrator orchestrator;

  @Operation(
      summary = "Start payment position status reconciliation",
      description =
          "Starts an asynchronous reconciliation process for the requested date interval and service types.")
  
  @ApiResponse(responseCode = "202", description = "Reconciliation request accepted")
  @ApiResponse(responseCode = "400", description = "Invalid reconciliation request")
  @ApiResponse(responseCode = "401", description = "Unauthorized")
  @ApiResponse(responseCode = "500", description = "Internal server error")
  @PostMapping(
      value = "/internal/position-status-reconciliation",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<PositionStatusReconciliationResponse> start(
      @Valid @RequestBody PositionStatusReconciliationRequest request) {

    return ResponseEntity.status(HttpStatus.ACCEPTED).body(orchestrator.start(request));
  }
}