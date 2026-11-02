package com.eventhq.pricing;

import com.eventhq.config.EventHqProperties;
import java.math.BigDecimal;
import org.springframework.context.annotation.*;

@Configuration
public class PricingConfiguration {
  @Bean
  PricingStrategy standardPricing() {
    return new MultiplierPricingStrategy("STANDARD", BigDecimal.ONE);
  }

  @Bean
  PricingStrategy vipPricing(EventHqProperties p) {
    return new MultiplierPricingStrategy("VIP", p.vipMultiplier());
  }

  @Bean
  PricingStrategy earlyBirdPricing(EventHqProperties p) {
    return new MultiplierPricingStrategy("EARLY_BIRD", p.earlyBirdMultiplier());
  }
}
