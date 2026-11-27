package it.gov.pagopa.gpd.technicalsupport.client.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import it.gov.pagopa.gpd.technicalsupport.exception.ReconciliationDailyApiRejectedException;
import it.gov.pagopa.gpd.technicalsupport.exception.ReconciliationDailySubmissionUnknownException;
import it.gov.pagopa.gpd.technicalsupport.model.gpd.ServiceType;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.ReconciliationRunStatus;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.Month;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ReconciliationDailyApiClientTest {
  private static final LocalDate DAY = LocalDate.of(2026, Month.SEPTEMBER, 1);
  private static final List<ServiceType> SERVICES = List.of(ServiceType.GPD);
  private static final String PATH = "/internal/position-status-reconciliation";
  private HttpServer server;
  private ExecutorService executor;
  private URI baseUrl;
  private final AtomicInteger posts = new AtomicInteger();
  private final AtomicInteger gets = new AtomicInteger();
  private final AtomicReference<String> requestBody = new AtomicReference<>();
  private final AtomicReference<String> requestPath = new AtomicReference<>();
  private final AtomicReference<Headers> requestHeaders = new AtomicReference<>();
  private final CountDownLatch releaseResponse = new CountDownLatch(1);
  private volatile int status = 202;
  private volatile boolean dropResponse;
  private volatile boolean delayResponse;
  private volatile boolean delayBody;
  private volatile String responseBody;

  @BeforeEach
  void setUp() throws IOException {
    responseBody = """
        {"accepted":true,"runs":[{"day":"2026-09-01","serviceTypes":["GPD"],
        "logicalRunKey":"2026-09-01__GPD","executionId":"daily-execution","status":"CREATED"}]}
        """;
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    executor = Executors.newCachedThreadPool();
    server.setExecutor(executor);
    server.createContext("/", this::handle);
    server.start();
    baseUrl = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
  }

  @AfterEach
  void tearDown() {
    releaseResponse.countDown();
    server.stop(0);
    executor.shutdownNow();
  }

  private void handle(HttpExchange exchange) throws IOException {
    try {
      if ("POST".equals(exchange.getRequestMethod())) {
        posts.incrementAndGet();
      } else {
        gets.incrementAndGet();
      }
      requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
      requestPath.set(exchange.getRequestURI().getPath());
      requestHeaders.set(exchange.getRequestHeaders());
      if (dropResponse) {
        return;
      }
      if (delayResponse && !awaitRelease()) {
        return;
      }
      byte[] body = responseBody.getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().set("Content-Type", "application/json");
      exchange.getResponseHeaders().set("Location", baseUrl + "/redirect-target");
      exchange.sendResponseHeaders(status, body.length);
      if (delayBody) {
        exchange.getResponseBody().write(body, 0, 1);
        exchange.getResponseBody().flush();
        if (!awaitRelease()) {
          return;
        }
        exchange.getResponseBody().write(body, 1, body.length - 1);
      } else {
        exchange.getResponseBody().write(body);
      }
    } finally {
      exchange.close();
    }
  }

  private boolean awaitRelease() {
    try {
      return releaseResponse.await(5, TimeUnit.SECONDS);
    } catch (InterruptedException error) {
      Thread.currentThread().interrupt();
      return false;
    }
  }

  private ReconciliationDailyApiClient client(String apiKey) {
    return new ReconciliationDailyApiClient(baseUrl, apiKey, Duration.ofSeconds(3), Duration.ofSeconds(5));
  }

  @Test
  void sendsOneDailyPostWithForceFalseAndOptionalApiKey() throws Exception {
    var client = client("test-subscription-key");
    var result = client.startDay(DAY, SERVICES);
    assertThat(result.executionId()).isEqualTo("daily-execution");
    assertThat(result.status()).isEqualTo(ReconciliationRunStatus.CREATED);
    assertThat(posts.get()).isEqualTo(1);
    assertThat(gets.get()).isZero();
    assertThat(requestPath.get()).isEqualTo(PATH);
    assertThat(requestHeaders.get().getFirst("Ocp-Apim-Subscription-Key")).isEqualTo("test-subscription-key");
    assertThat(requestHeaders.get().getFirst("Accept")).isEqualTo("application/json");
    assertThat(requestHeaders.get().getFirst("Content-Type")).isEqualTo("application/json");
    var payload = new ObjectMapper().readTree(requestBody.get());
    assertThat(payload.size()).isEqualTo(4);
    assertThat(payload.path("from").asText()).isEqualTo(DAY.toString());
    assertThat(payload.path("to").asText()).isEqualTo(DAY.toString());
    assertThat(payload.path("force").isBoolean()).isTrue();
    assertThat(payload.path("force").booleanValue()).isFalse();
  }

  @Test
  void canonicalizesServicesAndPreservesBasePathWithoutSendingAnApiKey() throws Exception {
    responseBody = responseBody.replace("[\"GPD\"]", "[\"WISP\",\"GPD\",\"GPD\"]")
        .replace("2026-09-01__GPD\"", "2026-09-01__GPD|WISP\"");
    var url = URI.create(baseUrl + "/support/");
    var client = new ReconciliationDailyApiClient(url, null);
    var result = client.startDay(DAY, List.of(ServiceType.WISP, ServiceType.GPD, ServiceType.GPD));
    assertThat(result.serviceTypes()).containsExactly(ServiceType.GPD, ServiceType.WISP);
    assertThat(requestPath.get()).isEqualTo("/support" + PATH);
    assertThat(requestHeaders.get().getFirst("Ocp-Apim-Subscription-Key")).isNull();
    var payload = new ObjectMapper().readTree(requestBody.get());
    assertThat(payload.path("serviceTypes")).hasToString("[\"GPD\",\"WISP\"]");
    assertThat(posts.get()).isEqualTo(1);
  }

  @ParameterizedTest
  @ValueSource(ints = {400, 401, 403, 404, 405, 413, 415, 422})
  void explicitRejectionDoesNotRetry(int code) {
    status = code;
    var client = client(null);
    assertThatThrownBy(() -> client.startDay(DAY, SERVICES))
        .isInstanceOfSatisfying(ReconciliationDailyApiRejectedException.class,
            error -> assertThat(error.getStatusCode()).isEqualTo(code));
    assertThat(posts.get()).isEqualTo(1);
  }

  @ParameterizedTest
  @ValueSource(ints = {200, 301, 302, 307, 308, 408, 409, 429, 500, 503})
  void unexpectedStatusDoesNotRetryOrFollowRedirects(int code) {
    status = code;
    var client = client(null);
    assertThatThrownBy(() -> client.startDay(DAY, SERVICES))
        .isInstanceOf(ReconciliationDailySubmissionUnknownException.class);
    assertThat(posts.get()).isEqualTo(1);
    assertThat(gets.get()).isZero();
  }

  @Test
  void malformedAcceptedResponseIsUnknownAndDoesNotRetry() {
    responseBody = "not-json";
    var client = client(null);
    assertThatThrownBy(() -> client.startDay(DAY, SERVICES))
        .isInstanceOf(ReconciliationDailySubmissionUnknownException.class);
    assertThat(posts.get()).isEqualTo(1);
  }

  @Test
  void responseLostAfterReceivingPostDoesNotTriggerAnotherPost() {
    dropResponse = true;
    var client = client(null);
    assertThatThrownBy(() -> client.startDay(DAY, SERVICES))
        .isInstanceOf(ReconciliationDailySubmissionUnknownException.class);
    assertThat(posts.get()).isEqualTo(1);
  }

  @Test
  void requestTimeoutDoesNotTriggerAnotherPost() {
    delayResponse = true;
    var client = new ReconciliationDailyApiClient(baseUrl, null,
        Duration.ofSeconds(3), Duration.ofSeconds(1));
    assertThatThrownBy(() -> client.startDay(DAY, SERVICES))
        .isInstanceOf(ReconciliationDailySubmissionUnknownException.class);
    assertThat(posts.get()).isEqualTo(1);
  }

  @Test
  void stalledResponseBodyIsAlsoBoundedByTheRequestDeadline() {
    delayBody = true;
    var client = new ReconciliationDailyApiClient(baseUrl, null,
        Duration.ofSeconds(3), Duration.ofSeconds(1));
    assertThatThrownBy(() -> client.startDay(DAY, SERVICES))
        .isInstanceOf(ReconciliationDailySubmissionUnknownException.class);
    assertThat(posts.get()).isEqualTo(1);
  }

  @Test
  void interruptedSubmissionRestoresTheInterruptFlag() {
    var client = client(null);
    Thread.currentThread().interrupt();
    try {
      assertThatThrownBy(() -> client.startDay(DAY, SERVICES))
          .isInstanceOf(ReconciliationDailySubmissionUnknownException.class);
      assertThat(Thread.currentThread().isInterrupted()).isTrue();
    } finally {
      Thread.interrupted();
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"ftp://localhost", "/relative", "http://user:secret@localhost",
      "http://localhost?query=1", "http://localhost#fragment", "http://localhost:99999"})
  void invalidBaseUrlIsRejected(String value) {
    var url = URI.create(value);
    assertThatThrownBy(() -> new ReconciliationDailyApiClient(url, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(posts.get()).isZero();
  }

  @Test
  void invalidTimeoutsKeysAndArgumentsAreRejectedBeforeHttp() {
    var client = client(null);
    var empty = List.<ServiceType>of();
    var invalidServices = Arrays.asList(ServiceType.GPD, null);
    var zero = Duration.ZERO;
    var positive = Duration.ofSeconds(1);
    assertThatThrownBy(() -> new ReconciliationDailyApiClient(null, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new ReconciliationDailyApiClient(baseUrl, " "))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new ReconciliationDailyApiClient(baseUrl, "key\r\nvalue"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new ReconciliationDailyApiClient(baseUrl, null, zero, positive))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new ReconciliationDailyApiClient(baseUrl, null, positive, zero))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> client.startDay(null, SERVICES)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> client.startDay(DAY, null)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> client.startDay(DAY, empty)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> client.startDay(DAY, invalidServices)).isInstanceOf(IllegalArgumentException.class);
    assertThat(posts.get()).isZero();
  }
}
