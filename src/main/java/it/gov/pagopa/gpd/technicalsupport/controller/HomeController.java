package it.gov.pagopa.gpd.technicalsupport.controller;

import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import it.gov.pagopa.gpd.technicalsupport.model.AppInfo;
import it.gov.pagopa.gpd.technicalsupport.model.ProblemJson;
import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
public class HomeController {

  @Value("${server.servlet.context-path:/}")
  private String basePath;

  @Value("${info.application.name}")
  private String name;

  @Value("${info.application.version}")
  private String version;

  @Value("${info.properties.environment}")
  private String environment;

  @Hidden
  @GetMapping({"", "/"})
  public ResponseEntity<Void> home() {
    return ResponseEntity
        .status(HttpStatus.FOUND)
        .location(URI.create(normalizedBasePath() + "swagger-ui/index.html"))
        .build();
  }

  @Operation(
      summary = "Return application info",
      security = {
          @SecurityRequirement(name = "ApiKey")
      },
      tags = {"Home"})
  @ApiResponses(
      value = {
          @ApiResponse(
              responseCode = "200",
              description = "OK.",
              content =
                  @Content(
                      mediaType = MediaType.APPLICATION_JSON_VALUE,
                      schema = @Schema(implementation = AppInfo.class))),
          @ApiResponse(
              responseCode = "401",
              description = "Wrong or missing function key.",
              content = @Content(schema = @Schema())),
          @ApiResponse(
              responseCode = "403",
              description = "Forbidden.",
              content = @Content(schema = @Schema())),
          @ApiResponse(
              responseCode = "500",
              description = "Service unavailable.",
              content =
                  @Content(
                      mediaType = MediaType.APPLICATION_JSON_VALUE,
                      schema = @Schema(implementation = ProblemJson.class)))
      })
  @GetMapping(value = "/info", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<AppInfo> info() {
    AppInfo appInfo =
        AppInfo.builder()
            .name(name)
            .version(version)
            .environment(environment)
            .build();

    return ResponseEntity.status(HttpStatus.OK).body(appInfo);
  }

  private String normalizedBasePath() {
    if (basePath == null || basePath.isBlank() || "/".equals(basePath)) {
      return "/";
    }

    return basePath.endsWith("/") ? basePath : basePath + "/";
  }
}