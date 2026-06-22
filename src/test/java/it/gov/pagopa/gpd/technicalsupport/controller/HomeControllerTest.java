package it.gov.pagopa.gpd.technicalsupport.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(HomeController.class)
@TestPropertySource(
    properties = {
      "server.servlet.context-path=/",
      "info.application.name=pagopa-gpd-technical-support",
      "info.application.version=0.1.0",
      "info.properties.environment=test"
    })
class HomeControllerTest {

  @Autowired private MockMvc mockMvc;

  @Test
  void info_shouldReturnApplicationInfo() throws Exception {
    mockMvc
        .perform(get("/info"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("pagopa-gpd-technical-support"))
        .andExpect(jsonPath("$.version").value("0.1.0"))
        .andExpect(jsonPath("$.environment").value("test"));
  }

  @Test
  void home_shouldRedirectToSwaggerUi() throws Exception {
    mockMvc
        .perform(get("/"))
        .andExpect(status().isFound())
        .andExpect(header().string("Location", "/swagger-ui/index.html"));
  }
}