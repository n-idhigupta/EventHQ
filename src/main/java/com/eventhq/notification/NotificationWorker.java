package com.eventhq.notification;

import java.time.Clock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationWorker {
  private final NotificationOutboxRepository outbox;
  private final Clock clock;
  private NotificationSink sink =
      (key, recipient, id) ->
          log.info("Simulated notification delivered: key={} booking={}", key, id);

  @Autowired(required = false)
  public void setNotificationSink(NotificationSink sink) {
    this.sink = sink;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void deliver(Long id) {
    var n = outbox.lockById(id).orElse(null);
    if (n == null || n.isDelivered() || n.getNextAttemptAt().isAfter(clock.instant())) return;
    n.setAttempts(n.getAttempts() + 1);
    try {
      sink.deliver("booking-confirmed-" + n.getBookingId(), n.getRecipient(), n.getBookingId());
      n.setDelivered(true);
    } catch (RuntimeException e) {
      n.setNextAttemptAt(
          clock.instant().plusSeconds(Math.min(3600L, 30L * (1L << Math.min(n.getAttempts(), 7)))));
      log.warn("Notification delivery will retry: outbox={}", id);
    }
  }
}
