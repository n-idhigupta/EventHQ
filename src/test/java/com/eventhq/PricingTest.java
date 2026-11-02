package com.eventhq;

import static org.assertj.core.api.Assertions.*;

import com.eventhq.pricing.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class PricingTest {
  @Test
  void precedenceCutoffAndRounding() {
    var f =
        new PricingStrategyFactory(
            List.of(
                new MultiplierPricingStrategy("STANDARD", BigDecimal.ONE),
                new MultiplierPricingStrategy("VIP", new BigDecimal("1.5")),
                new MultiplierPricingStrategy("EARLY_BIRD", new BigDecimal("0.8"))));
    var cutoff = Instant.parse("2030-01-01T00:00:00Z");
    assertThat(f.select("VIP", cutoff.minusSeconds(1), cutoff).name()).isEqualTo("VIP");
    assertThat(f.select("STANDARD", cutoff.minusSeconds(1), cutoff).name()).isEqualTo("EARLY_BIRD");
    assertThat(f.select("STANDARD", cutoff, cutoff).name()).isEqualTo("STANDARD");
    assertThat(f.select("VIP", cutoff, cutoff).price(new BigDecimal("10.01")))
        .isEqualByComparingTo("15.02");
  }
}
