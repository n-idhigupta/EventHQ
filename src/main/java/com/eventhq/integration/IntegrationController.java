package com.eventhq.integration;

import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/integrations")
@RequiredArgsConstructor
public class IntegrationController {
  private final ExternalApiService service;

  @GetMapping("/currency")
  public ExternalApiService.Quote currency(
      @RequestParam String base, @RequestParam String target, @RequestParam BigDecimal amount) {
    return service.quote(base, target, amount);
  }

  @GetMapping("/events/{id}/weather")
  public Mono<ExternalApiService.Weather> weather(@PathVariable Long id) {
    return service.weather(id);
  }
}
