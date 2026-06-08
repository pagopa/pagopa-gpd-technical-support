package it.gov.pagopa.gpd.technicalsupport;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class OpenApiGenerationTest {

  @Autowired
  private MockMvc mockMvc;

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