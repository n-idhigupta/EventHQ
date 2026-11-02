package com.eventhq;

import com.eventhq.notification.NotificationSink;
import java.time.*;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.*;

@org.springframework.boot.test.context.TestConfiguration
public class TestConfiguration {
  @Bean
  @Primary
  MutableClock testClock() {
    return new MutableClock();
  }

  @Bean
  TestSink testSink() {
    return new TestSink();
  }

  public static class MutableClock extends Clock {
    private volatile Instant now = Instant.parse("2035-01-01T00:00:00Z");

    public void reset() {
      now = Instant.parse("2035-01-01T00:00:00Z");
    }

    public void resetToWallTime() {
      now = Instant.now();
    }

    public void advance(Duration d) {
      now = now.plus(d);
    }

    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    public Clock withZone(ZoneId z) {
      return this;
    }

    public Instant instant() {
      return now;
    }
  }

  public static class TestSink implements NotificationSink {
    public volatile boolean fail;
    public final Set<String> delivered = ConcurrentHashMap.newKeySet();

    public void deliver(String key, String recipient, Long bookingId) {
      if (fail) throw new IllegalStateException("Simulated outage");
      delivered.add(key);
    }
  }
}
