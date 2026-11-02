package com.eventhq.notification;

/** Implementations must deduplicate the stable delivery key before delivering externally. */
public interface NotificationSink {
  void deliver(String deliveryKey, String recipient, Long bookingId);
}
