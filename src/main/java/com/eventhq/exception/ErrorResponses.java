package com.eventhq.exception;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Map;

public final class ErrorResponses {
  private ErrorResponses() {}

  public static ApiError body(
      int status, String code, String message, HttpServletRequest r, Map<String, String> fields) {
    return new ApiError(
        Instant.now(),
        status,
        code,
        message,
        r.getRequestURI(),
        String.valueOf(r.getAttribute("correlationId")),
        fields);
  }
}
