package com.quoteflow.common.api;

import com.quoteflow.auth.AccountNotEligibleException;
import com.quoteflow.auth.AuthenticationFailedException;
import com.quoteflow.auth.DuplicateEmailException;
import com.quoteflow.invoice.QuotationAlreadyInvoicedException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
		List<ApiError.FieldViolation> violations = ex.getBindingResult().getFieldErrors().stream()
				.map(this::toViolation)
				.toList();
		return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Request validation failed", request, violations, null);
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
		return build(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "Malformed request body", request, null, null);
	}

	@ExceptionHandler(HttpMediaTypeNotSupportedException.class)
	public ResponseEntity<ApiError> handleUnsupportedMedia(
			HttpMediaTypeNotSupportedException ex,
			HttpServletRequest request) {
		return build(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE", "Unsupported content type", request, null, null);
	}

	@ExceptionHandler(NoResourceFoundException.class)
	public ResponseEntity<ApiError> handleNotFound(NoResourceFoundException ex, HttpServletRequest request) {
		return build(HttpStatus.NOT_FOUND, "NOT_FOUND", "Resource not found", request, null, null);
	}

	@ExceptionHandler(AuthenticationFailedException.class)
	public ResponseEntity<ApiError> handleAuthFailed(AuthenticationFailedException ex, HttpServletRequest request) {
		return build(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", ex.getMessage(), request, null, null);
	}

	@ExceptionHandler(AccountNotEligibleException.class)
	public ResponseEntity<ApiError> handleNotEligible(AccountNotEligibleException ex, HttpServletRequest request) {
		return build(HttpStatus.FORBIDDEN, "FORBIDDEN", ex.getMessage(), request, null, null);
	}

	@ExceptionHandler(DuplicateEmailException.class)
	public ResponseEntity<ApiError> handleDuplicate(DuplicateEmailException ex, HttpServletRequest request) {
		return build(HttpStatus.CONFLICT, "CONFLICT", ex.getMessage(), request, null, null);
	}

	@ExceptionHandler(QuotationAlreadyInvoicedException.class)
	public ResponseEntity<ApiError> handleAlreadyInvoiced(
			QuotationAlreadyInvoicedException ex,
			HttpServletRequest request) {
		return build(
				HttpStatus.CONFLICT,
				"QUOTATION_ALREADY_INVOICED",
				ex.getMessage(),
				request,
				null,
				Map.of("existingInvoiceId", ex.getExistingInvoiceId().toString()));
	}

	@ExceptionHandler(DomainApiException.class)
	public ResponseEntity<ApiError> handleDomain(DomainApiException ex, HttpServletRequest request) {
		return build(ex.getStatus(), ex.getCode(), ex.getMessage(), request, null, ex.getDetails());
	}

	@ExceptionHandler(ResponseStatusException.class)
	public ResponseEntity<ApiError> handleStatus(ResponseStatusException ex, HttpServletRequest request) {
		HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
		String message = ex.getReason() != null ? ex.getReason() : status.getReasonPhrase();
		return build(status, status.name(), message, request, null, null);
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiError> handleGeneric(Exception ex, HttpServletRequest request) {
		log.error("Unhandled error path={}", request.getRequestURI(), ex);
		return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An unexpected error occurred", request, null, null);
	}

	private ApiError.FieldViolation toViolation(FieldError error) {
		return new ApiError.FieldViolation(error.getField(), error.getDefaultMessage());
	}

	private ResponseEntity<ApiError> build(
			HttpStatus status,
			String code,
			String message,
			HttpServletRequest request,
			List<ApiError.FieldViolation> violations,
			Map<String, Object> details) {
		Object attr = request.getAttribute(com.quoteflow.security.CorrelationIdFilter.HEADER);
		String correlationId = attr instanceof String s && !s.isBlank()
				? s
				: com.quoteflow.security.CorrelationIdFilter.sanitizeOrGenerate(request.getHeader("X-Correlation-Id"));
		ApiError body = new ApiError(
				Instant.now(),
				status.value(),
				code,
				message,
				request.getRequestURI(),
				correlationId,
				violations,
				details);
		return ResponseEntity.status(status).body(body);
	}
}
