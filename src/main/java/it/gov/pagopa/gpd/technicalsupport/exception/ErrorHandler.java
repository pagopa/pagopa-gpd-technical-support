package it.gov.pagopa.gpd.technicalsupport.exception;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;
import it.gov.pagopa.gpd.technicalsupport.model.ProblemJson;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/** All Exceptions are handled by this class. */
@ControllerAdvice
@Slf4j
public class ErrorHandler extends ResponseEntityExceptionHandler {

	/**
	 * 
	 * Handle if the input request is not a valid JSON.
	 *
	 * @param ex      {@link HttpMessageNotReadableException} exception raised
	 * @param headers headers of the response
	 * @param status  status of the response
	 * @param request request from frontend
	 * @return a {@link ProblemJson} as response with the cause and with a 400 as
	 *         HTTP status
	 */
	@Override
	public ResponseEntity<@Nullable Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {

		log.warn("Input not readable: ", ex);

		ProblemJson errorResponse = ProblemJson.builder().status(HttpStatus.BAD_REQUEST.value())
				.title(AppError.BAD_REQUEST.getTitle()).detail("Invalid input format").build();

		return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);

	}

	/**
	 * 
	 * Handle if missing some request parameters in the request.
	 *
	 * @param ex      {@link MissingServletRequestParameterException} exception
	 *                raised
	 * @param headers headers of the response
	 * @param status  status of the response
	 * @param request request from frontend
	 * @return a {@link ProblemJson} as response with the cause and with a 400 as
	 *         HTTP status
	 */
	@Override
	public ResponseEntity<@Nullable Object> handleMissingServletRequestParameter(
			MissingServletRequestParameterException ex, HttpHeaders headers, HttpStatusCode status,
			WebRequest request) {

		log.warn("Missing request parameter: ", ex);

		ProblemJson errorResponse = ProblemJson.builder().status(HttpStatus.BAD_REQUEST.value())
				.title(AppError.BAD_REQUEST.getTitle()).detail(ex.getMessage()).build();

		return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);

	}

	/**
	 * 
	 * Customize the response for TypeMismatchException.
	 *
	 * @param ex      the exception
	 * @param headers the headers to be written to the response
	 * @param status  the selected response status
	 * @param request the current request
	 * @return a {@code ResponseEntity} instance
	 */
	@Override
	protected ResponseEntity<@Nullable Object> handleTypeMismatch(TypeMismatchException ex, HttpHeaders headers,
			HttpStatusCode status, WebRequest request) {

		log.warn("Type mismatch: ", ex);

		String propertyName = ex instanceof MethodArgumentTypeMismatchException mismatchException
				? mismatchException.getName()
				: ex.getPropertyName();

		ProblemJson errorResponse = ProblemJson.builder().status(HttpStatus.BAD_REQUEST.value())
				.title(AppError.BAD_REQUEST.getTitle())
				.detail(String.format("Invalid value %s for property %s", ex.getValue(), propertyName)).build();

		return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);

	}

	/**
	 * 
	 * Handle if validation constraints are unsatisfied.
	 *
	 * @param ex      {@link MethodArgumentNotValidException} exception raised
	 * @param headers headers of the response
	 * @param status  status of the response
	 * @param request request from frontend
	 * @return a {@link ProblemJson} as response with the cause and with a 400 as
	 *         HTTP status
	 */
	@Override
	protected ResponseEntity<@Nullable Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {

		List<String> details = new ArrayList<>();

		for (FieldError error : ex.getBindingResult().getFieldErrors()) {
			details.add(error.getField() + ": " + error.getDefaultMessage());
		}

		String detailsMessage = String.join(", ", details);

		log.warn("Input not valid: {}", detailsMessage);

		ProblemJson errorResponse = ProblemJson.builder().status(HttpStatus.BAD_REQUEST.value())
				.title(AppError.BAD_REQUEST.getTitle()).detail(detailsMessage).build();

		return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);

	}

	@ExceptionHandler({ jakarta.validation.ConstraintViolationException.class })
	public ResponseEntity<ProblemJson> handleConstraintViolationException(
			final jakarta.validation.ConstraintViolationException ex, final WebRequest request) {

		log.warn("Validation Error raised:", ex);

		ProblemJson errorResponse = ProblemJson.builder().status(HttpStatus.BAD_REQUEST.value())
				.title(AppError.BAD_REQUEST.getTitle()).detail(ex.getMessage()).build();

		return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);

	}

	/**
	 * 
	 * Handle if a {@link FeignException} is raised.
	 *
	 * @param ex      {@link FeignException} exception raised
	 * @param request request from frontend
	 * @return a {@link ProblemJson} as response with the cause and with an
	 *         appropriate HTTP status
	 */
	@ExceptionHandler({ FeignException.class })
	public ResponseEntity<ProblemJson> handleFeignException(final FeignException ex, final WebRequest request) {

		log.warn("FeignException raised: ", ex);

		ProblemJson problem;

		if (ex.responseBody().isPresent()) {
			String body = byteBufferToString(ex.responseBody().get());

			try {
				problem = new ObjectMapper().readValue(body, ProblemJson.class);
			} catch (JsonProcessingException e) {
				problem = ProblemJson.builder().status(HttpStatus.BAD_GATEWAY.value())
						.title(AppError.RESPONSE_NOT_READABLE.getTitle())
						.detail(AppError.RESPONSE_NOT_READABLE.getDetails()).build();
			}
		} else {
			problem = ProblemJson.builder().status(HttpStatus.BAD_GATEWAY.value()).title("No Response Body")
					.detail("Error with external dependency").build();
		}

		return new ResponseEntity<>(problem, HttpStatus.valueOf(problem.getStatus()));

	}

	/**
	 * 
	 * Handle if a {@link AppException} is raised.
	 *
	 * @param ex      {@link AppException} exception raised
	 * @param request request from frontend
	 * @return a {@link ProblemJson} as response with the cause and with an
	 *         appropriate HTTP status
	 */
	@ExceptionHandler({ AppException.class })
	public ResponseEntity<ProblemJson> handleAppException(final AppException ex, final WebRequest request) {

		if (ex.getCause() != null) {

			log.warn("App Exception raised: {}\nCause of the App Exception: ", ex.getMessage(), ex.getCause());
		} else {
			log.warn("App Exception raised: ", ex);
		}

		ProblemJson errorResponse = ProblemJson.builder().status(ex.getHttpStatus().value()).title(ex.getTitle())
				.detail(ex.getMessage()).build();

		return new ResponseEntity<>(errorResponse, ex.getHttpStatus());

	}

	/**
	 * 
	 * Handle if a {@link Exception} is raised.
	 *
	 * @param ex      {@link Exception} exception raised
	 * @param request request from frontend
	 * @return a {@link ProblemJson} as response with the cause and with 500 as HTTP
	 *         status
	 */
	@ExceptionHandler({ Exception.class })
	public ResponseEntity<ProblemJson> handleGenericException(final Exception ex, final WebRequest request) {

		log.error("Generic Exception raised:", ex);

		ProblemJson errorResponse = ProblemJson.builder().status(HttpStatus.INTERNAL_SERVER_ERROR.value())
				.title(AppError.INTERNAL_SERVER_ERROR.getTitle()).detail(ex.getMessage()).build();

		return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);

	}

	private static String byteBufferToString(ByteBuffer byteBuffer) {
		ByteBuffer duplicate = byteBuffer.asReadOnlyBuffer();
		byte[] bytes = new byte[duplicate.remaining()];
		duplicate.get(bytes);
		return new String(bytes, StandardCharsets.UTF_8);
	}
}
