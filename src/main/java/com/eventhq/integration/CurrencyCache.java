package com.eventhq.integration;

import com.eventhq.config.EventHqProperties;
import jakarta.annotation.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CurrencyCache {
  private final EventHqProperties properties;
  private final Clock clock;
  private Map<String, Entry> entries;

  private record Entry(BigDecimal rate, Instant expiresAt) {}

  @PostConstruct
  public synchronized void initialize() {
    entries = new LinkedHashMap<>(16, 0.75f, true);
  }

  public synchronized Optional<BigDecimal> get(String key) {
    Entry e = entries.get(key);
    if (e == null) return Optional.empty();
    if (!e.expiresAt().isAfter(clock.instant())) {
      entries.remove(key);
      return Optional.empty();
    }
    return Optional.of(e.rate());
  }

  public synchronized void put(String key, BigDecimal rate) {
    entries.put(key, new Entry(rate, clock.instant().plus(properties.cacheTtl())));
    while (entries.size() > properties.cacheSize())
      entries.remove(entries.keySet().iterator().next());
  }

  @PreDestroy
  public synchronized void close() {
    entries.clear();
  }
}
