package it.gov.pagopa.gpd.technicalsupport;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import it.gov.pagopa.gpd.technicalsupport.config.OpenApiConfig;
import it.gov.pagopa.gpd.technicalsupport.controller.PositionStatusReconciliationController;
import it.gov.pagopa.gpd.technicalsupport.service.reconciliation.PositionStatusReconciliationOrchestrator;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springdoc.core.configuration.SpringDocConfiguration;
import org.springdoc.core.properties.SpringDocConfigProperties;
import org.springdoc.webmvc.core.configuration.SpringDocWebMvcConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@WebMvcTest(
    controllers = PositionStatusReconciliationController.class,
    properties = {
        "springdoc.api-docs.enabled=true",
        "springdoc.enable-default-api-docs=true"
    })
@Import(OpenApiConfig.class)
@EnableConfigurationProperties(SpringDocConfigProperties.class)
@ImportAutoConfiguration({
    SpringDocConfiguration.class,
    SpringDocWebMvcConfiguration.class
})
class OpenApiGenerationTest {

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private PositionStatusReconciliationOrchestrator orchestrator;

  @Test
  void swaggerSpringPlugin() throws Exception {
    String openApiJson =
        mockMvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);

    ObjectMapper objectMapper = new ObjectMapper();

    String formattedOpenApiJson =
        objectMapper.writerWithDefaultPrettyPrinter()
            .writeValueAsString(objectMapper.readTree(openApiJson));

    Files.createDirectories(Path.of("openapi"));
    Files.writeString(
        Path.of("openapi/openapi.json"),
        formattedOpenApiJson,
        StandardCharsets.UTF_8);
  }
}