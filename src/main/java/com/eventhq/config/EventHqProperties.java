package com.eventhq.config;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Duration;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties("eventhq")
@Validated
public record EventHqProperties(
    @NotBlank @Size(min = 32) String jwtSecret,
    @NotNull @DurationMin(seconds = 1) Duration jwtLifetime,
    @NotNull @DurationMin(seconds = 1) Duration holdDuration,
    @Pattern(regexp = "[A-Z]{3}") String currency,
    @NotNull @DecimalMin("1.0") BigDecimal vipMultiplier,
    @NotNull @DecimalMin("0.01") @DecimalMax("1.0") BigDecimal earlyBirdMultiplier,
    @Min(1) @Max(10000) int cacheSize,
    @NotNull @DurationMin(seconds = 1) Duration cacheTtl,
    @NotNull @DurationMin(seconds = 1) Duration httpTimeout,
    String currencyUrl,
    String weatherUrl,
    String adminEmail,
    String adminPassword) {}
