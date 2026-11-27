package it.gov.pagopa.gpd.technicalsupport.client.reconciliation;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import it.gov.pagopa.gpd.technicalsupport.exception.ReconciliationDailyApiRejectedException;
import it.gov.pagopa.gpd.technicalsupport.exception.ReconciliationDailySubmissionUnknownException;
import it.gov.pagopa.gpd.technicalsupport.model.gpd.ServiceType;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.ReconciliationRunResponse;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Sends one daily request per call. An unknown outcome must be checked in Cosmos before resubmission. */
public class ReconciliationDailyApiClient {
  private static final String PATH = "/internal/position-status-reconciliation";
  private static final String JSON = "application/json";
  private static final String API_KEY_HEADER = "Ocp-Apim-Subscription-Key";
  private static final Set<Integer> REJECTED = Set.of(400, 401, 403, 404, 405, 413, 415, 422);
  private final URI url;
  private final String apiKey;
  private final Duration requestTimeout;
  private final HttpClient client;
  private final ReconciliationDailyApiResponseMapper mapper = new ReconciliationDailyApiResponseMapper();

  public ReconciliationDailyApiClient(URI baseUrl, String apiKey) {
    this(baseUrl, apiKey, Duration.ofSeconds(3), Duration.ofSeconds(15));
  }

  public ReconciliationDailyApiClient(
      URI baseUrl, String apiKey, Duration connectTimeout, Duration requestTimeout) {
    validateBaseUrl(baseUrl);
    positive(connectTimeout);
    positive(requestTimeout);
    if (apiKey != null && (apiKey.isBlank() || apiKey.chars().anyMatch(Character::isISOControl))) {
      throw new IllegalArgumentException("API key must be nonblank and contain no control characters");
    }
    this.url = endpoint(baseUrl);
    this.apiKey = apiKey;
    this.requestTimeout = requestTimeout;
    this.client = HttpClient.newBuilder().connectTimeout(connectTimeout)
        .followRedirects(HttpClient.Redirect.NEVER).version(HttpClient.Version.HTTP_1_1).build();
  }

  /**
   * Submits one day with {@code force=false} to reuse an already completed daily run.
   *
   * <p>The daily API also supports explicit reprocessing with {@code force=true}.
   * Range recovery deliberately does not expose that option: resuming an interval
   * must preserve completed days. The flag is sent explicitly to make this policy
   * visible in the HTTP request.
   */
  public ReconciliationRunResponse startDay(LocalDate day, List<ServiceType> serviceTypes) {
    var services = canonicalServices(day, serviceTypes);
    var payload = JsonNodeFactory.instance.objectNode();
    payload.put("from", day.toString()).put("to", day.toString()).put("force", false);
    var array = payload.putArray("serviceTypes");
    services.forEach(service -> array.add(service.name()));
    var builder = HttpRequest.newBuilder(url).timeout(requestTimeout)
        .header("Accept", JSON).header("Content-Type", JSON)
        .POST(HttpRequest.BodyPublishers.ofString(payload.toString()));
    if (apiKey != null) {
      builder.header(API_KEY_HEADER, apiKey);
    }
    var response = send(builder.build());
    int status = response.statusCode();
    if (REJECTED.contains(status)) {
      throw new ReconciliationDailyApiRejectedException(status);
    }
    if (status != 202) {
      throw new ReconciliationDailySubmissionUnknownException("Unexpected API HTTP " + status + "; inspect Cosmos");
    }
    return mapper.map(response.body(), day, services);
  }

  private HttpResponse<byte[]> send(HttpRequest request) {
    if (Thread.currentThread().isInterrupted()) {
      throw new ReconciliationDailySubmissionUnknownException("API submission interrupted; inspect Cosmos");
    }
    var pending = client.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray());
    try {
      // Bound the complete response, including a body stalled after the headers have arrived.
      return pending.get(requestTimeout.toNanos(), TimeUnit.NANOSECONDS);
    } catch (InterruptedException error) {
      pending.cancel(true);
      Thread.currentThread().interrupt();
      throw new ReconciliationDailySubmissionUnknownException("API submission interrupted; inspect Cosmos", error);
    } catch (TimeoutException error) {
      pending.cancel(true);
      throw new ReconciliationDailySubmissionUnknownException("API response timed out; inspect Cosmos", error);
    } catch (ExecutionException error) {
      throw new ReconciliationDailySubmissionUnknownException("API transport failure; inspect Cosmos", error.getCause());
    }
  }

  private List<ServiceType> canonicalServices(LocalDate day, List<ServiceType> services) {
    if (day == null || day.getYear() < 1 || day.getYear() > 9999) {
      throw new IllegalArgumentException("Day must have a year between 0001 and 9999");
    }
    if (services == null || services.isEmpty() || services.stream().anyMatch(Objects::isNull)) {
      throw new IllegalArgumentException("At least one non-null service type is required");
    }
    return services.stream().distinct().sorted(Comparator.comparing(Enum::name)).toList();
  }

  private void positive(Duration value) {
    if (value == null) {
      throw new IllegalArgumentException("HTTP timeouts must be positive");
    }
    try {
      if (value.toNanos() <= 0) {
        throw new IllegalArgumentException("HTTP timeouts must be positive");
      }
    } catch (ArithmeticException error) {
      throw new IllegalArgumentException("HTTP timeout is too large", error);
    }
  }

  private void validateBaseUrl(URI baseUrl) {
    if (baseUrl == null) {
      throw new IllegalArgumentException("Reconciliation API base URL is required");
    }
    boolean valid = ("http".equals(baseUrl.getScheme()) || "https".equals(baseUrl.getScheme()))
        && baseUrl.getHost() != null && baseUrl.getPort() <= 65535
        && baseUrl.getRawUserInfo() == null && baseUrl.getRawQuery() == null && baseUrl.getRawFragment() == null;
    if (!valid) {
      throw new IllegalArgumentException("Invalid reconciliation API base URL");
    }
  }

  private URI endpoint(URI baseUrl) {
    String base = baseUrl.toString();
    int end = base.length();

    while (end > 0 && base.charAt(end - 1) == '/') {
      end--;
    }

    return URI.create(base.substring(0, end) + PATH);
  }
}
