package com.eventhq.payment;

import com.eventhq.exception.ApiException;
import java.math.BigDecimal;

public class SimulatedPaymentGateway implements PaymentGateway {
  public String name() {
    return "SIMULATED";
  }

  public boolean charge(String token, BigDecimal amount, String currency, String key) {
    if (amount.signum() <= 0) throw ApiException.bad("Payment amount must be positive");
    return switch (token) {
      case "tok_approved" -> true;
      case "tok_declined" -> false;
      default -> throw ApiException.bad("Use tok_approved or tok_declined");
    };
  }
}
