package com.eventhq.payment;

import java.math.BigDecimal;

public interface PaymentGateway {
  String name();

  boolean charge(String token, BigDecimal amount, String currency, String idempotencyKey);
}
