package com.eventhq.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.*;
import org.slf4j.*;
import org.springframework.dao.*;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {
  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  private ResponseEntity<ApiError> response(
      int s, String c, String m, HttpServletRequest r, Map<String, String> f) {
    return ResponseEntity.status(s).body(ErrorResponses.body(s, c, m, r, f));
  }

  @ExceptionHandler(ApiException.class)
  ResponseEntity<ApiError> api(ApiException e, HttpServletRequest r) {
    return response(e.getStatus().value(), e.getCode(), e.getMessage(), r, Map.of());
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ApiError> validation(MethodArgumentNotValidException e, HttpServletRequest r) {
    Map<String, String> f = new TreeMap<>();
    e.getBindingResult()
        .getFieldErrors()
        .forEach(x -> f.putIfAbsent(x.getField(), x.getDefaultMessage()));
    return response(400, "VALIDATION_FAILED", "Request validation failed", r, f);
  }

  @ExceptionHandler({
    HttpMessageNotReadableException.class,
    MethodArgumentTypeMismatchException.class,
    ConstraintViolationException.class,
    IllegalArgumentException.class
  })
  ResponseEntity<ApiError> malformed(Exception e, HttpServletRequest r) {
    return response(400, "INVALID_REQUEST", "Invalid request content or parameters", r, Map.of());
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  ResponseEntity<ApiError> constraint(Exception e, HttpServletRequest r) {
    return response(409, "DATA_CONFLICT", "Request conflicts with existing data", r, Map.of());
  }

  @ExceptionHandler({PessimisticLockingFailureException.class, QueryTimeoutException.class})
  ResponseEntity<ApiError> locked(Exception e, HttpServletRequest r) {
    return response(
        409,
        "CONCURRENT_UPDATE",
        "Concurrent update; retry with the same idempotency key",
        r,
        Map.of());
  }

  @ExceptionHandler(AccessDeniedException.class)
  ResponseEntity<ApiError> denied(Exception e, HttpServletRequest r) {
    return response(403, "FORBIDDEN", "Access denied", r, Map.of());
  }

  @ExceptionHandler(AuthenticationException.class)
  ResponseEntity<ApiError> unauthenticated(Exception e, HttpServletRequest r) {
    return response(401, "UNAUTHENTICATED", "Invalid credentials", r, Map.of());
  }

  @ExceptionHandler(NoResourceFoundException.class)
  ResponseEntity<ApiError> missing(Exception e, HttpServletRequest r) {
    return response(404, "NOT_FOUND", "Resource not found", r, Map.of());
  }

  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  ResponseEntity<ApiError> method(Exception e, HttpServletRequest r) {
    return response(405, "METHOD_NOT_ALLOWED", "Method not allowed", r, Map.of());
  }

  @ExceptionHandler({
    org.springframework.web.bind.MissingServletRequestParameterException.class,
    org.springframework.web.bind.ServletRequestBindingException.class,
    org.springframework.web.method.annotation.HandlerMethodValidationException.class
  })
  ResponseEntity<ApiError> missingInput(Exception e, HttpServletRequest r) {
    return response(400, "INVALID_REQUEST", "Missing or invalid request parameters", r, Map.of());
  }

  @ExceptionHandler(org.springframework.web.HttpMediaTypeNotSupportedException.class)
  ResponseEntity<ApiError> mediaType(Exception e, HttpServletRequest r) {
    return response(
        415, "UNSUPPORTED_MEDIA_TYPE", "Use application/json for request bodies", r, Map.of());
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ApiError> unexpected(Exception e, HttpServletRequest r) {
    log.error("Unhandled request failure", e);
    return response(500, "INTERNAL_ERROR", "An unexpected error occurred", r, Map.of());
  }
}
