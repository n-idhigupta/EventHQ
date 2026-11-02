package com.eventhq.integration;

import com.eventhq.config.EventHqProperties;
import com.eventhq.event.*;
import com.eventhq.exception.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.*;
import java.net.URI;
import java.util.Currency;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class ExternalApiService {
  private final RestTemplate rest;
  private final WebClient web;
  private final CurrencyCache cache;
  private final EventHqProperties properties;
  private final EventRepository events;

  public record Quote(
      String base, String target, BigDecimal amount, BigDecimal rate, BigDecimal converted) {}

  public record Weather(Long eventId, double temperatureCelsius, int weatherCode) {}

  private record Coordinates(double latitude, double longitude) {}

  public Quote quote(String base, String target, BigDecimal amount) {
    validateCurrency(base);
    validateCurrency(target);
    if (amount == null || amount.signum() < 0 || amount.precision() > 16 || amount.scale() > 2)
      throw ApiException.bad("Amount must be nonnegative with at most 16 digits and two decimals");
    BigDecimal rate;
    if (base.equals(target)) rate = BigDecimal.ONE;
    else {
      String key = base + ":" + target;
      rate = cache.get(key).orElseGet(() -> fetchRate(base, target, key));
    }
    return new Quote(
        base, target, amount, rate, amount.multiply(rate).setScale(2, RoundingMode.HALF_UP));
  }

  private BigDecimal fetchRate(String base, String target, String key) {
    if (properties.currencyUrl() == null || properties.currencyUrl().isBlank())
      throw unavailable("Currency integration is not configured");
    try {
      URI uri =
          UriComponentsBuilder.fromUriString(properties.currencyUrl())
              .queryParam("base", base)
              .queryParam("symbols", target)
              .build()
              .encode()
              .toUri();
      JsonNode json = rest.getForObject(uri, JsonNode.class);
      if (json == null || !json.path("rates").path(target).isNumber())
        throw unavailable("Invalid currency response");
      BigDecimal rate = json.path("rates").path(target).decimalValue();
      if (rate.signum() <= 0) throw unavailable("Invalid currency rate");
      cache.put(key, rate);
      return rate;
    } catch (ApiException e) {
      throw e;
    } catch (RuntimeException e) {
      throw unavailable("Currency provider unavailable");
    }
  }

  public Mono<Weather> weather(Long eventId) {
    if (properties.weatherUrl() == null || properties.weatherUrl().isBlank())
      return Mono.error(unavailable("Weather integration is not configured"));
    return Mono.fromCallable(() -> coordinates(eventId))
        .subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic())
        .flatMap(
            c ->
                web.get()
                    .uri(
                        UriComponentsBuilder.fromUriString(properties.weatherUrl())
                            .queryParam("latitude", c.latitude())
                            .queryParam("longitude", c.longitude())
                            .queryParam("current", "temperature_2m,weather_code")
                            .build()
                            .encode()
                            .toUri())
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .switchIfEmpty(Mono.error(unavailable("Empty weather response")))
                    .map(
                        j -> {
                          JsonNode current = j.path("current");
                          if (!current.path("temperature_2m").isNumber()
                              || !current.path("weather_code").isIntegralNumber())
                            throw unavailable("Invalid weather response");
                          return new Weather(
                              eventId,
                              current.path("temperature_2m").doubleValue(),
                              current.path("weather_code").intValue());
                        }))
        .timeout(properties.httpTimeout())
        .onErrorMap(
            e -> !(e instanceof ApiException), e -> unavailable("Weather provider unavailable"));
  }

  private Coordinates coordinates(Long id) {
    return events
        .findPublishedWithVenue(id)
        .map(e -> new Coordinates(e.getVenue().getLatitude(), e.getVenue().getLongitude()))
        .orElseThrow(() -> ApiException.missing("Event"));
  }

  private void validateCurrency(String value) {
    try {
      Currency.getInstance(value);
    } catch (RuntimeException e) {
      throw ApiException.bad("Invalid ISO 4217 currency");
    }
  }

  private ApiException unavailable(String message) {
    return new ApiException(HttpStatus.BAD_GATEWAY, "UPSTREAM_UNAVAILABLE", message);
  }
}
