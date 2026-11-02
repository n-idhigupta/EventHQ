package com.eventhq;

import static com.eventhq.event.CatalogDtos.*;
import static org.assertj.core.api.Assertions.*;

import com.eventhq.event.*;
import com.eventhq.exception.ApiException;
import com.eventhq.integration.*;
import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.math.BigDecimal;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.*;
import reactor.test.StepVerifier;

@SpringBootTest(properties = "eventhq.http-timeout=1s")
@ActiveProfiles("test")
@Import(TestConfiguration.class)
class ExternalApiTest {
  static HttpServer server;
  static volatile String body;
  static volatile int status = 200;
  static volatile long delay;
  static AtomicInteger calls = new AtomicInteger();

  static {
    try {
      server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      server.createContext(
          "/",
          e -> {
            calls.incrementAndGet();
            try {
              if (delay > 0) Thread.sleep(delay);
              byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
              e.getResponseHeaders().set("Content-Type", "application/json");
              e.sendResponseHeaders(status, bytes.length);
              e.getResponseBody().write(bytes);
            } catch (InterruptedException ex) {
              Thread.currentThread().interrupt();
            } catch (IOException ignored) {
            } finally {
              e.close();
            }
          });
      server.setExecutor(
          Executors.newCachedThreadPool(
              r -> {
                Thread t = new Thread(r);
                t.setDaemon(true);
                return t;
              }));
      server.start();
    } catch (IOException e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  @DynamicPropertySource
  static void urls(DynamicPropertyRegistry r) {
    String url = "http://127.0.0.1:" + server.getAddress().getPort();
    r.add("eventhq.currency-url", () -> url + "/currency");
    r.add("eventhq.weather-url", () -> url + "/weather");
  }

  @Autowired ExternalApiService api;
  @Autowired CatalogService catalog;
  @Autowired TestConfiguration.MutableClock clock;

  @BeforeEach
  void reset() {
    clock.reset();
    status = 200;
    delay = 0;
    calls.set(0);
    body = "{}";
  }

  @AfterAll
  static void stop() {
    server.stop(0);
  }

  Long event() {
    var v = catalog.createVenue(new VenueInput("Weather Hall", "Delhi", 28.6, 77.2));
    catalog.createSeat(v.id(), new SeatInput("W1", Tier.STANDARD));
    var e =
        catalog.createEvent(
            new EventInput(
                "Weather Event",
                v.id(),
                clock.instant().plus(Duration.ofDays(5)),
                clock.instant(),
                new BigDecimal("100"),
                List.of()));
    catalog.publish(e.id());
    return e.id();
  }

  @Test
  void currencyIsParsedRoundedAndCached() {
    body = "{\"rates\":{\"USD\":1.2345}}";
    var first = api.quote("INR", "USD", new BigDecimal("10.00"));
    assertThat(first.converted()).isEqualByComparingTo("12.35");
    api.quote("INR", "USD", new BigDecimal("20.00"));
    assertThat(calls.get()).isEqualTo(1);
    clock.advance(Duration.ofMinutes(6));
    api.quote("INR", "USD", BigDecimal.ONE);
    assertThat(calls.get()).isEqualTo(2);
  }

  @Test
  void malformedCurrencyIsRejected() {
    body = "{\"rates\":{}}";
    assertThatThrownBy(() -> api.quote("EUR", "JPY", BigDecimal.ONE))
        .isInstanceOf(ApiException.class);
  }

  @Test
  void unavailableCurrencyIsRejected() {
    status = 503;
    assertThatThrownBy(() -> api.quote("GBP", "JPY", BigDecimal.ONE))
        .isInstanceOf(ApiException.class);
  }

  @Test
  void currencyReadTimeoutIsBounded() {
    delay = 1500;
    assertThatThrownBy(() -> api.quote("CAD", "JPY", BigDecimal.ONE))
        .isInstanceOf(ApiException.class);
  }

  @Test
  void weatherUsesReactivePipeline() {
    body = "{\"current\":{\"temperature_2m\":24.5,\"weather_code\":3}}";
    StepVerifier.create(api.weather(event()))
        .assertNext(
            w -> {
              assertThat(w.temperatureCelsius()).isEqualTo(24.5);
              assertThat(w.weatherCode()).isEqualTo(3);
            })
        .verifyComplete();
  }

  @Test
  void malformedWeatherFails() {
    body = "{\"current\":{}}";
    StepVerifier.create(api.weather(event())).expectError(ApiException.class).verify();
  }

  @Test
  void weatherTimeoutFails() {
    delay = 1500;
    StepVerifier.create(api.weather(event())).expectError(ApiException.class).verify();
  }
}
