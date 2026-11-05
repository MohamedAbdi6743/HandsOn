package com.handson.exception;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Turns exceptions thrown anywhere in a controller or service into a consistent JSON error.
 * Controllers never need their own try/catch blocks.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	/** @Valid failed: return 400 and say which fields were wrong. */
	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
		Map<String, String> fieldErrors = new LinkedHashMap<>();
		for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
			// Keep the first message per field so the response stays short.
			fieldErrors.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
		}
		return respond(ApiError.withFieldErrors(HttpStatus.BAD_REQUEST, "Some fields are invalid.", fieldErrors));
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ApiError> handleUnreadableBody(HttpMessageNotReadableException ex) {
		return respond(ApiError.of(HttpStatus.BAD_REQUEST, "Request body is missing or is not valid JSON."));
	}

	/** A URL value has the wrong type, e.g. /api/events/abc where a number was expected. */
	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
		return respond(ApiError.of(HttpStatus.BAD_REQUEST, "Invalid value for '" + ex.getName() + "'."));
	}

	@ExceptionHandler(BadRequestException.class)
	public ResponseEntity<ApiError> handleBadRequest(BadRequestException ex) {
		return respond(ApiError.of(HttpStatus.BAD_REQUEST, ex.getMessage()));
	}

	@ExceptionHandler(NotFoundException.class)
	public ResponseEntity<ApiError> handleNotFound(NotFoundException ex) {
		return respond(ApiError.of(HttpStatus.NOT_FOUND, ex.getMessage()));
	}

	@ExceptionHandler(BusinessRuleException.class)
	public ResponseEntity<ApiError> handleBusinessRule(BusinessRuleException ex) {
		return respond(ApiError.of(HttpStatus.CONFLICT, ex.getMessage()));
	}

	/**
	 * A database constraint said no, e.g. two people registering the same email at the same moment.
	 * The real database message is logged but not shown, because it reveals table and column names.
	 */
	@ExceptionHandler(DataIntegrityViolationException.class)
	public ResponseEntity<ApiError> handleDataIntegrity(DataIntegrityViolationException ex) {
		log.warn("Database constraint violated: {}", ex.getMostSpecificCause().getMessage());
		return respond(ApiError.of(HttpStatus.CONFLICT, "This change conflicts with existing data."));
	}

	/** Anything else. Spring's own errors (404 for unknown URLs, 405 wrong method) keep their status. */
	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiError> handleEverythingElse(Exception ex) {
		if (ex instanceof ErrorResponse springError) {
			return respond(ApiError.of(springError.getStatusCode(), springError.getBody().getDetail()));
		}
		log.error("Unexpected error", ex);
		return respond(ApiError.of(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong on our side."));
	}

	private ResponseEntity<ApiError> respond(ApiError error) {
		return ResponseEntity.status(error.status()).body(error);
	}

}
