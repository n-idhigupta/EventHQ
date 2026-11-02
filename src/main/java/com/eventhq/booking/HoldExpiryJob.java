package com.eventhq.booking;

import java.time.Clock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class HoldExpiryJob {
  private final BookingRepository bookings;
  private final BookingService service;
  private final Clock clock;

  @Scheduled(fixedDelayString = "${eventhq.expiry-poll-ms:30000}")
  public void run() {
    for (Long id : bookings.expiredIds(clock.instant(), PageRequest.of(0, 100))) {
      try {
        service.expire(id);
      } catch (RuntimeException e) {
        log.warn("Hold expiry failed for booking {}", id, e);
      }
    }
  }
}
