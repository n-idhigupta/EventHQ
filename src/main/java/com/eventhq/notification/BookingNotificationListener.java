package com.eventhq.notification;

import java.time.Clock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.*;

@Component
@RequiredArgsConstructor
@Slf4j
public class BookingNotificationListener {
  private final NotificationOutboxRepository outbox;
  private final NotificationWorker worker;
  private final Clock clock;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void confirmed(BookingConfirmedEvent event) {
    try {
      outbox.findByBookingId(event.bookingId()).ifPresent(n -> worker.deliver(n.getId()));
    } catch (RuntimeException e) {
      log.warn("Immediate notification deferred to outbox poller");
    }
  }

  @Scheduled(fixedDelayString = "${eventhq.notification-poll-ms:10000}")
  public void retry() {
    for (Long id : outbox.due(clock.instant(), PageRequest.of(0, 100))) {
      try {
        worker.deliver(id);
      } catch (RuntimeException e) {
        log.warn("Outbox delivery failed for {}", id);
      }
    }
  }
}
