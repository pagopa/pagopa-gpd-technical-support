package it.gov.pagopa.gpd.technicalsupport.config;

import it.gov.pagopa.gpd.technicalsupport.exception.AppError;
import it.gov.pagopa.gpd.technicalsupport.model.ProblemJson;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.CodeSignature;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Aspect
@Component
@Slf4j
public class LoggingAspect {

  public static final String START_TIME = "startTime";
  public static final String METHOD = "method";
  public static final String STATUS = "status";
  public static final String CODE = "httpCode";
  public static final String RESPONSE_TIME = "responseTime";
  public static final String FAULT_CODE = "faultCode";
  public static final String FAULT_DETAIL = "faultDetail";
  public static final String REQUEST_ID = "requestId";
  public static final String OPERATION_ID = "operationId";
  public static final String ARGS = "args";

  private static final int MAX_LOG_VALUE_LENGTH = 500;
  private static final String HEADER_REQUEST_ID = "X-Request-Id";
  private static final String HEADER_CORRELATION_ID = "X-Correlation-Id";

  private final HttpServletRequest httpRequest;
  private final HttpServletResponse httpResponse;
  private final String name;
  private final String version;
  private final String environment;

  public LoggingAspect(
      HttpServletRequest httpRequest,
      HttpServletResponse httpResponse,
      @Value("${info.application.name}") String name,
      @Value("${info.application.version}") String version,
      @Value("${info.properties.environment}") String environment) {
    this.httpRequest = httpRequest;
    this.httpResponse = httpResponse;
    this.name = name;
    this.version = version;
    this.environment = environment;
  }

  @Pointcut("within(it.gov.pagopa.gpd.technicalsupport..*)")
  public void applicationPackage() {
    // application package
  }

  @Pointcut("@within(org.springframework.web.bind.annotation.RestController)")
  public void restController() {
    // all rest controllers
  }

  @Pointcut("@within(org.springframework.stereotype.Controller)")
  public void controller() {
    // all mvc controllers
  }

  @Pointcut("@within(org.springframework.stereotype.Repository)")
  public void repository() {
    // all repositories
  }

  @Pointcut("@within(org.springframework.stereotype.Service)")
  public void service() {
    // all services
  }

  /** Log essential info of application during the startup. */
  @PostConstruct
  public void logStartup() {
    log.info("-> Starting {} version {} - environment {}", name, version, environment);
  }

  @Around(value = "applicationPackage() && (restController() || controller())")
  public Object logApiInvocation(ProceedingJoinPoint joinPoint) throws Throwable {
    boolean completedSuccessfully = false;

    setupApiMdc(joinPoint);

    try {
      Map<String, String> params = getParams(joinPoint);
      MDC.put(ARGS, params.toString());

      log.debug("Invoking API operation {} - args: {}", joinPoint.getSignature().getName(), params);

      Object result = joinPoint.proceed();

      completedSuccessfully = true;

      MDC.put(STATUS, "OK");
      MDC.put(CODE, resolveHttpCode(result));
      MDC.put(RESPONSE_TIME, getExecutionTime());

      log.info(
          "Successful API operation {} - result: {}",
          joinPoint.getSignature().getName(),
          safeLogValue("result", result));

      return result;

    } catch (Throwable throwable) {
      MDC.put(STATUS, "KO");
      MDC.put(RESPONSE_TIME, getExecutionTime());
      MDC.put(FAULT_CODE, throwable.getClass().getSimpleName());
      MDC.put(FAULT_DETAIL, safeLogValue(FAULT_DETAIL, throwable.getMessage()));

      throw throwable;

    } finally {
      if (completedSuccessfully) {
        clearApplicationMdc();
      }
    }
  }

  @AfterReturning(value = "execution(* *..exception.ErrorHandler.*(..))", returning = "result")
  public void throwingApiInvocation(JoinPoint joinPoint, Object result) {
    try {
      ResponseEntity<?> response = result instanceof ResponseEntity<?> responseEntity ? responseEntity : null;

      MDC.put(STATUS, "KO");
      MDC.put(CODE, resolveHttpCode(response));
      MDC.put(RESPONSE_TIME, getExecutionTime());
      MDC.put(FAULT_CODE, getTitle(response));
      MDC.put(FAULT_DETAIL, getDetail(response));

      log.info(
          "Failed API operation {} - error: {}",
          MDC.get(METHOD),
          safeLogValue("error", result));

    } finally {
      clearApplicationMdc();
    }
  }

  @Around(value = "applicationPackage() && (repository() || service())")
  public Object logTrace(ProceedingJoinPoint joinPoint) throws Throwable {
    Map<String, String> params = getParams(joinPoint);

    log.debug("Call method {} - args: {}", joinPoint.getSignature().toShortString(), params);

    Object result = joinPoint.proceed();

    log.debug(
        "Return method {} - result: {}",
        joinPoint.getSignature().toShortString(),
        safeLogValue("result", result));

    return result;
  }

  private void setupApiMdc(ProceedingJoinPoint joinPoint) {
    MDC.put(METHOD, joinPoint.getSignature().getName());
    MDC.put(START_TIME, String.valueOf(System.currentTimeMillis()));
    MDC.put(OPERATION_ID, UUID.randomUUID().toString());

    if (MDC.get(REQUEST_ID) == null) {
      MDC.put(REQUEST_ID, resolveRequestId());
    }
  }

  private String resolveRequestId() {
    String requestId = httpRequest.getHeader(HEADER_REQUEST_ID);

    if (isBlank(requestId)) {
      requestId = httpRequest.getHeader(HEADER_CORRELATION_ID);
    }

    if (isBlank(requestId)) {
      requestId = UUID.randomUUID().toString();
    }

    return requestId;
  }

  public static String getExecutionTime() {
    String startTime = MDC.get(START_TIME);

    if (startTime == null) {
      return "-";
    }

    try {
      long executionTime = System.currentTimeMillis() - Long.parseLong(startTime);
      return String.valueOf(executionTime);
    } catch (NumberFormatException e) {
      return "-";
    }
  }

  private String resolveHttpCode(Object result) {
    if (result instanceof ResponseEntity<?> responseEntity) {
      return String.valueOf(responseEntity.getStatusCode().value());
    }

    return String.valueOf(httpResponse.getStatus());
  }

  private static String getDetail(ResponseEntity<?> result) {
    ProblemJson body = getProblemJsonBody(result);

    if (body == null || body.getDetail() == null) {
      return AppError.UNKNOWN.getDetails();
    }

    return body.getDetail();
  }

  private static String getTitle(ResponseEntity<?> result) {
    ProblemJson body = getProblemJsonBody(result);

    if (body == null || body.getTitle() == null) {
      return AppError.UNKNOWN.getTitle();
    }

    return body.getTitle();
  }

  private static ProblemJson getProblemJsonBody(ResponseEntity<?> result) {
    if (result == null) {
      return null;
    }

    Object body = result.getBody();

    if (body instanceof ProblemJson problemJson) {
      return problemJson;
    }

    return null;
  }

  private static Map<String, String> getParams(ProceedingJoinPoint joinPoint) {
    CodeSignature codeSignature = (CodeSignature) joinPoint.getSignature();

    String[] parameterNames = codeSignature.getParameterNames();
    Object[] args = joinPoint.getArgs();

    Map<String, String> params = new LinkedHashMap<>();

    for (int i = 0; i < parameterNames.length && i < args.length; i++) {
      params.put(parameterNames[i], safeLogValue(parameterNames[i], args[i]));
    }

    return params;
  }

  private static String safeLogValue(String name, Object value) {
    if (value == null) {
      return "";
    }

    if (isSensitiveName(name)) {
      return "***";
    }

    if (value instanceof HttpServletRequest) {
      return HttpServletRequest.class.getSimpleName();
    }

    if (value instanceof HttpServletResponse) {
      return HttpServletResponse.class.getSimpleName();
    }

    String text;

    try {
      text = String.valueOf(value);
    } catch (Exception e) {
      return "<unprintable>";
    }

    if (text.length() > MAX_LOG_VALUE_LENGTH) {
      return text.substring(0, MAX_LOG_VALUE_LENGTH) + "...";
    }

    return text;
  }

  private static boolean isSensitiveName(String name) {
    if (name == null) {
      return false;
    }

    String normalizedName = name.toLowerCase(Locale.ROOT);

    return normalizedName.contains("password")
        || normalizedName.contains("pwd")
        || normalizedName.contains("token")
        || normalizedName.contains("secret")
        || normalizedName.contains("authorization")
        || normalizedName.contains("apikey")
        || normalizedName.contains("api-key")
        || normalizedName.contains("key");
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  private static void clearApplicationMdc() {
    MDC.remove(START_TIME);
    MDC.remove(METHOD);
    MDC.remove(STATUS);
    MDC.remove(CODE);
    MDC.remove(RESPONSE_TIME);
    MDC.remove(FAULT_CODE);
    MDC.remove(FAULT_DETAIL);
    MDC.remove(REQUEST_ID);
    MDC.remove(OPERATION_ID);
    MDC.remove(ARGS);
  }
}