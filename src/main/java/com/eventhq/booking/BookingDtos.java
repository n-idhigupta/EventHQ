package com.eventhq.booking;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class BookingDtos {
  private BookingDtos() {}

  public record HoldRequest(
      @NotNull Long eventId,
      @NotEmpty @Size(max = 10) List<@NotNull @Positive Long> eventSeatIds) {}

  public record PaymentRequest(
      @NotBlank @Pattern(regexp = "SIMULATED") String gateway,
      @NotBlank @Pattern(regexp = "tok_approved|tok_declined") String token) {}

  public record ItemView(
      Long id, Long eventSeatId, String label, BigDecimal price, String currency) {}

  public record BookingView(
      Long id,
      Long eventId,
      String status,
      Instant expiresAt,
      BigDecimal total,
      String currency,
      List<ItemView> items) {}

  public record PaymentView(Long bookingId, String outcome, String bookingStatus) {}
}
