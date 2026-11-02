package com.eventhq.pricing;

import java.math.*;

public record MultiplierPricingStrategy(String name, BigDecimal multiplier)
    implements PricingStrategy {
  public BigDecimal price(BigDecimal base) {
    return base.multiply(multiplier).setScale(2, RoundingMode.HALF_UP);
  }
}
