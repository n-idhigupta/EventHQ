package com.eventhq;

import static com.eventhq.booking.BookingDtos.*;
import static com.eventhq.event.CatalogDtos.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.eventhq.auth.*;
import com.eventhq.booking.*;
import com.eventhq.event.*;
import com.eventhq.exception.*;
import com.eventhq.notification.*;
import com.eventhq.ticket.*;
import com.eventhq.user.*;
import com.eventhq.venue.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestConfiguration.class)
public abstract class BookingScenarios {
  @Autowired CatalogService catalog;
  @Autowired BookingService booking;
  @Autowired AuthService auth;
  @Autowired UserRepository users;
  @Autowired TicketService tickets;
  @Autowired NotificationOutboxRepository outbox;
  @Autowired NotificationWorker worker;
  @Autowired TestConfiguration.MutableClock clock;
  @Autowired TestConfiguration.TestSink sink;
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper mapper;

  @Test
  void browserInterfaceIsPublicWhileBookingsRemainProtected() throws Exception {
    mvc.perform(get("/")).andExpect(status().isOk()).andExpect(forwardedUrl("index.html"));
    mvc.perform(get("/index.html")).andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith("text/html"));
    mvc.perform(get("/assets/app.js")).andExpect(status().isOk());
    mvc.perform(get("/assets/app.css")).andExpect(status().isOk());
    mvc.perform(get("/api/v1/tickets")).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/v1/admin/events")).andExpect(status().isUnauthorized());
  }

  @BeforeEach
  void reset() {
    clock.reset();
    sink.fail = false;
  }

  Long user() {
    String email = UUID.randomUUID() + "@example.com";
    auth.register(email, "Password-for-tests-123");
    return users.findByEmail(email).orElseThrow().getId();
  }

  record Fixture(Long eventId, List<Long> seats) {}

  Fixture fixture() {
    var v = catalog.createVenue(new VenueInput("Hall", "Delhi", 28.6, 77.2));
    catalog.createSeat(v.id(), new SeatInput("A1", Tier.STANDARD));
    catalog.createSeat(v.id(), new SeatInput("A2", Tier.VIP));
    var e =
        catalog.createEvent(
            new EventInput(
                "Concert",
                v.id(),
                clock.instant().plus(Duration.ofDays(30)),
                clock.instant().plus(Duration.ofDays(10)),
                new BigDecimal("100.00"),
                List.of()));
    catalog.publish(e.id());
    return new Fixture(
        e.id(), catalog.availability(e.id()).stream().map(InventoryView::id).toList());
  }

  HoldRequest request(Fixture f, Long... ids) {
    return new HoldRequest(f.eventId(), List.of(ids));
  }

  PaymentRequest approved() {
    return new PaymentRequest("SIMULATED", "tok_approved");
  }

  @Test
  void holdPayAndRetryIssuesExactlyOneTicket() {
    Long u = user();
    Fixture f = fixture();
    var held = booking.hold(u, request(f, f.seats().get(0)));
    assertThat(held.total()).isEqualByComparingTo("80.00");
    var paid = booking.pay(u, held.id(), "payment-test-" + held.id(), approved());
    assertThat(paid.outcome()).isEqualTo("APPROVED");
    assertThat(booking.pay(u, held.id(), "payment-test-" + held.id(), approved())).isEqualTo(paid);
    assertThat(tickets.owned(u)).hasSize(1);
    assertThat(catalog.availability(f.eventId()).get(0).available()).isFalse();
    assertThat(sink.delivered).contains("booking-confirmed-" + held.id());
    assertThatThrownBy(
            () ->
                booking.pay(
                    u,
                    held.id(),
                    "payment-test-" + held.id(),
                    new PaymentRequest("SIMULATED", "tok_declined")))
        .isInstanceOf(ApiException.class);
  }

  @Test
  void declinedCanRetryWithNewKeyAndCancellationReleasesSeats() {
    Long u = user();
    Fixture f = fixture();
    var b = booking.hold(u, request(f, f.seats().get(0)));
    assertThat(
            booking
                .pay(
                    u,
                    b.id(),
                    "declined-" + b.id(),
                    new PaymentRequest("SIMULATED", "tok_declined"))
                .outcome())
        .isEqualTo("DECLINED");
    assertThat(tickets.owned(u)).isEmpty();
    assertThat(booking.cancel(u, b.id()).status()).isEqualTo("CANCELLED");
    assertThat(catalog.availability(f.eventId()).get(0).available()).isTrue();
    var next = booking.hold(u, request(f, f.seats().get(0)));
    assertThat(booking.pay(u, next.id(), "approved-" + next.id(), approved()).outcome())
        .isEqualTo("APPROVED");
  }

  @Test
  void expiredHoldCanBeReclaimedWithoutCleanupStealingNewHold() {
    Long first = user(), second = user();
    Fixture f = fixture();
    var a = booking.hold(first, request(f, f.seats().get(0)));
    clock.advance(Duration.ofMinutes(10));
    assertThat(booking.get(first, a.id()).status()).isEqualTo("EXPIRED");
    var b = booking.hold(second, request(f, f.seats().get(0)));
    booking.expire(a.id());
    assertThat(booking.pay(second, b.id(), "reclaimed-" + b.id(), approved()).outcome())
        .isEqualTo("APPROVED");
    assertThatThrownBy(() -> booking.pay(first, a.id(), "expired-" + a.id(), approved()))
        .isInstanceOf(ApiException.class);
  }

  @Test
  void mixedAvailabilityRollsBackAndOwnershipIsEnforced() {
    Long a = user(), b = user();
    Fixture f = fixture();
    var held = booking.hold(a, request(f, f.seats().get(0)));
    assertThatThrownBy(() -> booking.hold(b, request(f, f.seats().get(0), f.seats().get(1))))
        .isInstanceOf(ApiException.class);
    assertThat(catalog.availability(f.eventId()).get(1).available()).isTrue();
    assertThatThrownBy(() -> booking.get(b, held.id())).isInstanceOf(ApiException.class);
    assertThatThrownBy(() -> booking.cancel(b, held.id())).isInstanceOf(ApiException.class);
    assertThatThrownBy(() -> booking.pay(b, held.id(), "other-user-key", approved()))
        .isInstanceOf(ApiException.class);
  }

  @Test
  void concurrentHoldsHaveOneWinner() throws Exception {
    Long a = user(), b = user();
    Fixture f = fixture();
    var start = new CountDownLatch(1);
    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      List<Future<Boolean>> results = new ArrayList<>();
      for (Long u : List.of(a, b))
        results.add(
            pool.submit(
                () -> {
                  start.await();
                  try {
                    booking.hold(u, request(f, f.seats().get(0)));
                    return true;
                  } catch (ApiException e) {
                    return false;
                  }
                }));
      start.countDown();
      int wins = 0;
      for (var result : results) if (result.get(20, TimeUnit.SECONDS)) wins++;
      assertThat(wins).isEqualTo(1);
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void concurrentPaymentRetriesIssueOneTicket() throws Exception {
    Long u = user();
    Fixture f = fixture();
    var b = booking.hold(u, request(f, f.seats().get(0)));
    var start = new CountDownLatch(1);
    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      List<Future<PaymentView>> results = new ArrayList<>();
      for (int i = 0; i < 2; i++)
        results.add(
            pool.submit(
                () -> {
                  start.await();
                  return booking.pay(u, b.id(), "concurrent-" + b.id(), approved());
                }));
      start.countDown();
      for (var result : results)
        assertThat(result.get(20, TimeUnit.SECONDS).outcome()).isEqualTo("APPROVED");
      assertThat(tickets.owned(u)).hasSize(1);
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void expiryAndPaymentRaceCannotIssueTicketForExpiredHold() throws Exception {
    Long u = user();
    Fixture f = fixture();
    var b = booking.hold(u, request(f, f.seats().get(0)));
    clock.advance(Duration.ofMinutes(10));
    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      var expiry = pool.submit(() -> booking.expire(b.id()));
      var payment =
          pool.submit(
              () -> {
                try {
                  return booking.pay(u, b.id(), "expiry-race-" + b.id(), approved()).outcome();
                } catch (ApiException e) {
                  return "CONFLICT";
                }
              });
      expiry.get(20, TimeUnit.SECONDS);
      assertThat(payment.get(20, TimeUnit.SECONDS)).isIn("EXPIRED", "CONFLICT");
      assertThat(tickets.owned(u)).isEmpty();
      assertThat(catalog.availability(f.eventId()).get(0).available()).isTrue();
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void outboxRetriesWithoutRollingBackPayment() {
    Long u = user();
    Fixture f = fixture();
    var b = booking.hold(u, request(f, f.seats().get(0)));
    sink.fail = true;
    booking.pay(u, b.id(), "outbox-test-" + b.id(), approved());
    var n = outbox.findByBookingId(b.id()).orElseThrow();
    assertThat(n.isDelivered()).isFalse();
    assertThat(tickets.owned(u)).hasSize(1);
    clock.advance(Duration.ofHours(1));
    sink.fail = false;
    worker.deliver(n.getId());
    worker.deliver(n.getId());
    assertThat(outbox.findById(n.getId()).orElseThrow().isDelivered()).isTrue();
    assertThat(sink.delivered).contains("booking-confirmed-" + b.id());
  }

  @Test
  void catalogFreezesSeatsAndDraftsArePrivate() {
    var v = catalog.createVenue(new VenueInput("Draft Hall", "Mumbai", 19, 72));
    catalog.createSeat(v.id(), new SeatInput("B1", Tier.STANDARD));
    var e =
        catalog.createEvent(
            new EventInput(
                "Draft",
                v.id(),
                clock.instant().plus(Duration.ofDays(1)),
                clock.instant(),
                new BigDecimal("10"),
                List.of()));
    assertThatThrownBy(() -> catalog.event(e.id(), false)).isInstanceOf(ApiException.class);
    assertThatThrownBy(() -> catalog.createSeat(v.id(), new SeatInput("B2", Tier.STANDARD)))
        .isInstanceOf(ApiException.class);
  }

  @Test
  void securityValidationAndPaginationUseStructuredResponses() throws Exception {
    mvc.perform(get("/api/v1/bookings/1"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
        .andExpect(jsonPath("$.correlationId").isString());
    mvc.perform(
            post("/api/v1/admin/venues")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                .contentType("application/json")
                .content("{}"))
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/api/v1/auth/register")
                .contentType("application/json")
                .content(mapper.writeValueAsString(Map.of("email", "bad", "password", "short"))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.email").exists());
    mvc.perform(get("/api/v1/events?size=101")).andExpect(status().isBadRequest());
    mvc.perform(get("/api/v1/events?size=2"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.size").value(2));
  }

  @Test
  void missingQueryParametersReturnClientErrors() throws Exception {
    mvc.perform(get("/api/v1/integrations/currency").with(jwt()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
  }

  @Test
  void realJwtWorksAndWrongPasswordFails() throws Exception {
    clock.resetToWallTime();
    String email = UUID.randomUUID() + "@example.com";
    var token = auth.register(email, "Password-for-tests-123");
    mvc.perform(get("/api/v1/tickets").header("Authorization", "Bearer " + token.accessToken()))
        .andExpect(status().isOk());
    mvc.perform(get("/api/v1/tickets").header("Authorization", "Bearer invalid"))
        .andExpect(status().isUnauthorized());
    assertThatThrownBy(() -> auth.login(email, "incorrect-password"))
        .isInstanceOf(org.springframework.security.authentication.BadCredentialsException.class);
    assertThat(mapper.writeValueAsString(users.findByEmail(email).orElseThrow()))
        .doesNotContain("passwordHash", "Password-for-tests");
  }
}
