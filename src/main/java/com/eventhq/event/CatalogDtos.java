package com.eventhq.event;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class CatalogDtos {
  private CatalogDtos() {}

  public record VenueInput(
      @NotBlank @Size(max = 150) String name,
      @NotBlank @Size(max = 255) String address,
      @DecimalMin("-90") @DecimalMax("90") double latitude,
      @DecimalMin("-180") @DecimalMax("180") double longitude) {}

  public record VenueView(
      Long id, String name, String address, double latitude, double longitude) {}

  public record SeatInput(@NotBlank @Size(max = 40) String label, @NotNull Tier tier) {}

  public enum Tier {
    STANDARD,
    VIP
  }

  public record SeatView(Long id, String label, String tier) {}

  public record SponsorInput(@NotBlank @Size(max = 150) String name) {}

  public record SponsorView(Long id, String name) {}

  public record EventInput(
      @NotBlank @Size(max = 150) String name,
      @NotNull Long venueId,
      @NotNull @Future Instant startsAt,
      @NotNull Instant earlyBirdUntil,
      @NotNull @DecimalMin("0.01") @Digits(integer = 12, fraction = 2) BigDecimal basePrice,
      @NotNull @Size(max = 50) List<@NotNull Long> sponsorIds) {}

  public record EventView(
      Long id,
      String name,
      Long venueId,
      Instant startsAt,
      Instant earlyBirdUntil,
      BigDecimal basePrice,
      String currency,
      boolean published,
      List<SponsorView> sponsors) {}

  public record InventoryView(Long id, Long seatId, String label, String tier, boolean available) {}

  public record PageView<T>(
      List<T> content, int page, int size, long totalElements, int totalPages) {}
}
