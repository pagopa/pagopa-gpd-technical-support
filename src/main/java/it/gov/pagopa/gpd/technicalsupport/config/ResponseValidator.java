package it.gov.pagopa.gpd.technicalsupport.config;

import it.gov.pagopa.gpd.technicalsupport.exception.AppException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Aspect
@Component
@RequiredArgsConstructor
public class ResponseValidator {

  private final Validator validator;

  /**
   * Validates responses returned by application controllers when the response body contains
   * Jakarta Bean Validation constraints.
   *
   * @param result the response returned by the controller
   */
  @AfterReturning(
      pointcut = "execution(* it.gov.pagopa.gpd.technicalsupport.controller..*(..))",
      returning = "result")
  public void validateResponse(Object result) {
    if (result instanceof ResponseEntity<?> response) {
      validateResponseBody(response);
    }
  }

  private void validateResponseBody(ResponseEntity<?> response) {
    Object body = response.getBody();

    if (body == null) {
      return;
    }

    Set<ConstraintViolation<Object>> validationResults = validator.validate(body);

    if (!validationResults.isEmpty()) {
      StringBuilder sb = new StringBuilder();

      for (ConstraintViolation<Object> error : validationResults) {
        sb.append(error.getPropertyPath())
            .append(" ")
            .append(error.getMessage())
            .append(". ");
      }

      String message = StringUtils.chop(sb.toString());

      throw new AppException(
          HttpStatus.INTERNAL_SERVER_ERROR,
          "Invalid response",
          message);
    }
  }
}