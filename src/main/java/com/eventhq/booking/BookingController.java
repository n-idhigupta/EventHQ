package com.eventhq.booking;

import static com.eventhq.booking.BookingDtos.*;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/bookings")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('CUSTOMER','ADMIN')")
public class BookingController {
  private final BookingService service;

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public BookingView hold(@AuthenticationPrincipal Jwt user, @Valid @RequestBody HoldRequest r) {
    return service.hold(Long.valueOf(user.getSubject()), r);
  }

  @GetMapping("/{id}")
  public BookingView get(@AuthenticationPrincipal Jwt user, @PathVariable Long id) {
    return service.get(Long.valueOf(user.getSubject()), id);
  }

  @DeleteMapping("/{id}")
  public BookingView cancel(@AuthenticationPrincipal Jwt user, @PathVariable Long id) {
    return service.cancel(Long.valueOf(user.getSubject()), id);
  }

  @PostMapping("/{id}/payments")
  public PaymentView pay(
      @AuthenticationPrincipal Jwt user,
      @PathVariable Long id,
      @RequestHeader(value = "Idempotency-Key", required = false) String key,
      @Valid @RequestBody PaymentRequest r) {
    return service.pay(Long.valueOf(user.getSubject()), id, key, r);
  }
}
