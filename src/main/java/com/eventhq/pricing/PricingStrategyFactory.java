package com.eventhq.pricing;

import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class PricingStrategyFactory {
  private final Map<String, PricingStrategy> strategies;

  public PricingStrategyFactory(List<PricingStrategy> strategies) {
    this.strategies =
        strategies.stream()
            .collect(Collectors.toUnmodifiableMap(PricingStrategy::name, Function.identity()));
  }

  public PricingStrategy select(String tier, Instant now, Instant cutoff) {
    return strategies.get(
        "VIP".equals(tier) ? "VIP" : now.isBefore(cutoff) ? "EARLY_BIRD" : "STANDARD");
  }
}
