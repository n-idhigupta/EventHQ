package com.eventhq.payment;

import org.springframework.context.annotation.*;

@Configuration
public class PaymentConfiguration {
  @Bean
  PaymentGateway simulatedPaymentGateway() {
    return new SimulatedPaymentGateway();
  }
}
