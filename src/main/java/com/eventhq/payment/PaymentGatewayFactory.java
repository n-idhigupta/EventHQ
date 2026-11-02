package com.eventhq.payment;

import com.eventhq.exception.ApiException;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class PaymentGatewayFactory {
  private final Map<String, PaymentGateway> gateways;

  public PaymentGatewayFactory(List<PaymentGateway> all) {
    Map<String, PaymentGateway> map = new HashMap<>();
    all.forEach(g -> map.put(g.name(), g));
    gateways = Map.copyOf(map);
  }

  public PaymentGateway get(String name) {
    PaymentGateway g = gateways.get(name);
    if (g == null) throw ApiException.bad("Unsupported payment gateway");
    return g;
  }
}
