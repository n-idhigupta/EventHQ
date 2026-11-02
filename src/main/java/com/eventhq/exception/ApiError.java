package com.eventhq.exception;

import java.time.Instant;
import java.util.Map;

public record ApiError(
    Instant timestamp,
    int status,
    String code,
    String message,
    String path,
    String correlationId,
    Map<String, String> fieldErrors) {}
