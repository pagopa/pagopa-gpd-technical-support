package it.gov.pagopa.gpd.technicalsupport.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.UUID;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.CodeSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class LoggingAspectTest {

  private HttpServletRequest httpRequest;
  private HttpServletResponse httpResponse;
  private LoggingAspect loggingAspect;

  @BeforeEach
  void setUp() {
    MDC.clear();

    httpRequest = mock(HttpServletRequest.class);
    httpResponse = mock(HttpServletResponse.class);

    when(httpResponse.getStatus()).thenReturn(200);

    loggingAspect =
        new LoggingAspect(
            httpRequest,
            httpResponse,
            "pagopa-gpd-technical-support",
            "0.1.0",
            "test");
  }

  @AfterEach
  void tearDown() {
    MDC.clear();
  }

  @Test
  void logApiInvocation_shouldUseXRequestIdProceedAndCleanApplicationMdcOnSuccess()
      throws Throwable {

    when(httpRequest.getHeader("X-Request-Id")).thenReturn("request-id-123");

    ProceedingJoinPoint joinPoint =
        joinPoint(
            "createReconciliation",
            new String[] {"request", "password"},
            new Object[] {"payload", "secret-password"});

    when(joinPoint.proceed())
        .thenAnswer(
            invocation -> {
              assertThat(MDC.get(LoggingAspect.METHOD)).isEqualTo("createReconciliation");
              assertThat(MDC.get(LoggingAspect.START_TIME)).isNotBlank();
              assertThat(MDC.get(LoggingAspect.OPERATION_ID)).isNotBlank();
              assertThat(MDC.get(LoggingAspect.REQUEST_ID)).isEqualTo("request-id-123");

              assertThat(MDC.get(LoggingAspect.ARGS))
                  .contains("request=payload")
                  .contains("password=***");

              return ResponseEntity.status(HttpStatus.CREATED).body("created");
            });

    Object result = loggingAspect.logApiInvocation(joinPoint);

    assertThat(result).isEqualTo(ResponseEntity.status(HttpStatus.CREATED).body("created"));

    assertApplicationMdcCleared();
  }

  @Test
  void logApiInvocation_shouldUseCorrelationIdWhenRequestIdIsMissing() throws Throwable {
    when(httpRequest.getHeader("X-Request-Id")).thenReturn(null);
    when(httpRequest.getHeader("X-Correlation-Id")).thenReturn("correlation-id-123");

    ProceedingJoinPoint joinPoint =
        joinPoint(
            "getInfo",
            new String[] {"arg"},
            new Object[] {"value"});

    when(joinPoint.proceed())
        .thenAnswer(
            invocation -> {
              assertThat(MDC.get(LoggingAspect.REQUEST_ID)).isEqualTo("correlation-id-123");
              return ResponseEntity.ok("ok");
            });

    Object result = loggingAspect.logApiInvocation(joinPoint);

    assertThat(result).isEqualTo(ResponseEntity.ok("ok"));

    assertApplicationMdcCleared();
  }

  @Test
  void logApiInvocation_shouldGenerateRequestIdWhenRequestIdAndCorrelationIdAreMissing()
      throws Throwable {

    when(httpRequest.getHeader("X-Request-Id")).thenReturn(null);
    when(httpRequest.getHeader("X-Correlation-Id")).thenReturn(null);

    ProceedingJoinPoint joinPoint =
        joinPoint(
            "getInfo",
            new String[] {"arg"},
            new Object[] {"value"});

    when(joinPoint.proceed())
        .thenAnswer(
            invocation -> {
              String generatedRequestId = MDC.get(LoggingAspect.REQUEST_ID);

              assertThat(generatedRequestId).isNotBlank();
              assertThat(UUID.fromString(generatedRequestId)).isNotNull();

              return ResponseEntity.ok("ok");
            });

    Object result = loggingAspect.logApiInvocation(joinPoint);

    assertThat(result).isEqualTo(ResponseEntity.ok("ok"));

    assertApplicationMdcCleared();
  }

  @Test
  void logApiInvocation_shouldKeepMdcWhenControllerThrowsSoErrorHandlerCanUseIt()
      throws Throwable {

    when(httpRequest.getHeader("X-Request-Id")).thenReturn("request-id-123");

    ProceedingJoinPoint joinPoint =
        joinPoint(
            "startReconciliation",
            new String[] {"request"},
            new Object[] {"payload"});

    when(joinPoint.proceed()).thenThrow(new IllegalStateException("Unexpected failure"));

    assertThatThrownBy(() -> loggingAspect.logApiInvocation(joinPoint))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Unexpected failure");

    assertThat(MDC.get(LoggingAspect.METHOD)).isEqualTo("startReconciliation");
    assertThat(MDC.get(LoggingAspect.REQUEST_ID)).isEqualTo("request-id-123");
    assertThat(MDC.get(LoggingAspect.STATUS)).isEqualTo("KO");
    assertThat(MDC.get(LoggingAspect.FAULT_CODE)).isEqualTo("IllegalStateException");
    assertThat(MDC.get(LoggingAspect.FAULT_DETAIL)).isEqualTo("Unexpected failure");
    assertThat(MDC.get(LoggingAspect.RESPONSE_TIME)).isNotBlank();

    loggingAspect.throwingApiInvocation(
        mock(JoinPoint.class),
        ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());

    assertApplicationMdcCleared();
  }

  @Test
  void throwingApiInvocation_shouldHandleNullResponseBodyWithoutNullPointerException() {
    MDC.put(LoggingAspect.METHOD, "operationWithError");
    MDC.put(LoggingAspect.START_TIME, String.valueOf(System.currentTimeMillis() - 10));
    MDC.put(LoggingAspect.REQUEST_ID, "request-id-123");

    ResponseEntity<Void> errorResponse =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).build();

    loggingAspect.throwingApiInvocation(mock(JoinPoint.class), errorResponse);

    assertApplicationMdcCleared();
  }

  @Test
  void throwingApiInvocation_shouldHandleNonResponseEntityResultAndCleanMdc() {
    MDC.put(LoggingAspect.METHOD, "operationWithError");
    MDC.put(LoggingAspect.START_TIME, String.valueOf(System.currentTimeMillis() - 10));
    MDC.put(LoggingAspect.REQUEST_ID, "request-id-123");

    when(httpResponse.getStatus()).thenReturn(500);

    loggingAspect.throwingApiInvocation(mock(JoinPoint.class), "generic-error-result");

    assertApplicationMdcCleared();
  }

  @Test
  void logTrace_shouldProceedAndReturnResult() throws Throwable {
    ProceedingJoinPoint joinPoint =
        joinPoint(
            "serviceMethod",
            new String[] {"apiKey", "normalArg"},
            new Object[] {"secret-api-key", "value"});

    when(joinPoint.proceed()).thenReturn("service-result");

    Object result = loggingAspect.logTrace(joinPoint);

    assertThat(result).isEqualTo("service-result");
  }

  @Test
  void getExecutionTime_shouldReturnDashWhenStartTimeIsMissing() {
    MDC.remove(LoggingAspect.START_TIME);

    assertThat(LoggingAspect.getExecutionTime()).isEqualTo("-");
  }

  @Test
  void getExecutionTime_shouldReturnDashWhenStartTimeIsInvalid() {
    MDC.put(LoggingAspect.START_TIME, "not-a-number");

    assertThat(LoggingAspect.getExecutionTime()).isEqualTo("-");
  }

  @Test
  void getExecutionTime_shouldReturnElapsedMillisecondsWhenStartTimeIsValid() {
    MDC.put(LoggingAspect.START_TIME, String.valueOf(System.currentTimeMillis() - 100));

    assertThat(Long.parseLong(LoggingAspect.getExecutionTime()))
        .isGreaterThanOrEqualTo(100L);
  }

  @Test
  void logStartup_shouldNotThrowException() {
    loggingAspect.logStartup();
  }

  private ProceedingJoinPoint joinPoint(
      String methodName,
      String[] parameterNames,
      Object[] args) {

    ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
    CodeSignature signature = mock(CodeSignature.class);

    when(joinPoint.getSignature()).thenReturn(signature);
    when(joinPoint.getArgs()).thenReturn(args);

    when(signature.getName()).thenReturn(methodName);
    when(signature.getParameterNames()).thenReturn(parameterNames);
    when(signature.toShortString()).thenReturn("TestClass." + methodName + "(..)");

    return joinPoint;
  }

  private void assertApplicationMdcCleared() {
    assertThat(MDC.get(LoggingAspect.START_TIME)).isNull();
    assertThat(MDC.get(LoggingAspect.METHOD)).isNull();
    assertThat(MDC.get(LoggingAspect.STATUS)).isNull();
    assertThat(MDC.get(LoggingAspect.CODE)).isNull();
    assertThat(MDC.get(LoggingAspect.RESPONSE_TIME)).isNull();
    assertThat(MDC.get(LoggingAspect.FAULT_CODE)).isNull();
    assertThat(MDC.get(LoggingAspect.FAULT_DETAIL)).isNull();
    assertThat(MDC.get(LoggingAspect.REQUEST_ID)).isNull();
    assertThat(MDC.get(LoggingAspect.OPERATION_ID)).isNull();
    assertThat(MDC.get(LoggingAspect.ARGS)).isNull();
  }
}