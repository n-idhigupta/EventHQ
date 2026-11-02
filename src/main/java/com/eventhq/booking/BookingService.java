package com.eventhq.booking;

import static com.eventhq.booking.BookingDtos.*;

import com.eventhq.config.EventHqProperties;
import com.eventhq.event.*;
import com.eventhq.exception.ApiException;
import com.eventhq.notification.*;
import com.eventhq.payment.*;
import com.eventhq.pricing.*;
import com.eventhq.ticket.*;
import com.eventhq.user.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@RequiredArgsConstructor
public class BookingService {
  private final BookingRepository bookings;
  private final EventRepository events;
  private final EventSeatRepository inventory;
  private final UserRepository users;
  private final PaymentAttemptRepository payments;
  private final TicketRepository tickets;
  private final NotificationOutboxRepository outbox;
  private final PricingStrategyFactory pricing;
  private final PaymentGatewayFactory gateways;
  private final EventHqProperties properties;
  private final Clock clock;
  private final ApplicationEventPublisher publisher;

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public BookingView hold(Long userId, HoldRequest r) {
    if (r.eventSeatIds() == null || r.eventSeatIds().isEmpty() || r.eventSeatIds().size() > 10)
      throw ApiException.bad("Select between 1 and 10 seats");
    List<Long> ids = r.eventSeatIds().stream().distinct().sorted().toList();
    if (ids.size() != r.eventSeatIds().size())
      throw ApiException.bad("Duplicate seats are not allowed");
    Event event =
        events
            .findById(r.eventId())
            .filter(Event::isPublished)
            .orElseThrow(() -> ApiException.missing("Event"));
    List<EventSeat> locked = inventory.lockSeats(event.getId(), ids);
    Instant now = clock.instant();
    if (!event.getStartsAt().isAfter(now)) throw ApiException.conflict("Event has already started");
    if (locked.size() != ids.size())
      throw ApiException.bad("Every seat must belong to the selected event");
    for (EventSeat seat : locked) {
      Booking owner = seat.getBooking();
      if (owner != null
          && ("PAID".equals(owner.getStatus())
              || ("HELD".equals(owner.getStatus()) && owner.getExpiresAt().isAfter(now))))
        throw ApiException.conflict("One or more seats are unavailable");
    }
    Booking b = new Booking();
    b.setUser(users.findById(userId).orElseThrow(() -> ApiException.missing("User")));
    b.setEvent(event);
    b.setStatus("HELD");
    b.setCreatedAt(now);
    b.setExpiresAt(now.plus(properties.holdDuration()));
    b.setCurrency(event.getCurrency());
    b.setTotal(BigDecimal.ZERO);
    bookings.save(b);
    BigDecimal total = BigDecimal.ZERO;
    for (EventSeat seat : locked) {
      BookingItem item = new BookingItem();
      item.setBooking(b);
      item.setEventSeat(seat);
      item.setCurrency(event.getCurrency());
      item.setPrice(
          pricing
              .select(seat.getSeat().getTier(), now, event.getEarlyBirdUntil())
              .price(event.getBasePrice()));
      b.getItems().add(item);
      seat.setBooking(b);
      total = total.add(item.getPrice());
    }
    b.setTotal(total);
    bookings.flush();
    return view(b);
  }

  @Transactional(readOnly = true)
  public BookingView get(Long userId, Long id) {
    return view(
        owned(bookings.findById(id).orElseThrow(() -> ApiException.missing("Booking")), userId));
  }

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public BookingView cancel(Long userId, Long id) {
    Booking b =
        owned(bookings.lockById(id).orElseThrow(() -> ApiException.missing("Booking")), userId);
    if ("PAID".equals(b.getStatus()))
      throw ApiException.conflict("Paid booking cannot be cancelled");
    if ("HELD".equals(b.getStatus())) {
      release(b);
      b.setStatus(b.getExpiresAt().isAfter(clock.instant()) ? "CANCELLED" : "EXPIRED");
    }
    return view(b);
  }

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public PaymentView pay(Long userId, Long id, String key, PaymentRequest r) {
    if (key == null || !key.matches("[A-Za-z0-9_-]{8,80}"))
      throw ApiException.bad(
          "Idempotency-Key must contain 8 to 80 letters, digits, underscores or hyphens");
    Booking b =
        owned(bookings.lockById(id).orElseThrow(() -> ApiException.missing("Booking")), userId);
    String hash = hash(r.gateway() + ":" + r.token());
    var previous = payments.findByIdempotencyKey(key);
    if (previous.isPresent()) {
      PaymentAttempt p = previous.get();
      if (!p.getBooking().getId().equals(id) || !p.getRequestHash().equals(hash))
        throw ApiException.conflict("Idempotency key was used for another request");
      return new PaymentView(id, p.getOutcome(), effectiveStatus(b));
    }
    if (!"HELD".equals(b.getStatus()))
      throw ApiException.conflict("Booking is not awaiting payment");
    List<EventSeat> locked = inventory.lockForBooking(id);
    Instant now = clock.instant();
    if (!b.getExpiresAt().isAfter(now) || !b.getEvent().getStartsAt().isAfter(now)) {
      for (EventSeat s : locked) s.setBooking(null);
      b.setStatus("EXPIRED");
      return new PaymentView(id, "EXPIRED", "EXPIRED");
    }
    if (locked.size() != b.getItems().size())
      throw ApiException.conflict("Seat hold no longer belongs to this booking");
    boolean approved =
        gateways.get(r.gateway()).charge(r.token(), b.getTotal(), b.getCurrency(), key);
    PaymentAttempt p = new PaymentAttempt();
    p.setBooking(b);
    p.setIdempotencyKey(key);
    p.setOutcome(approved ? "APPROVED" : "DECLINED");
    p.setRequestHash(hash);
    p.setCreatedAt(now);
    payments.saveAndFlush(p);
    if (approved) {
      b.setStatus("PAID");
      for (BookingItem item : b.getItems()) {
        Ticket ticket = new Ticket();
        ticket.setUser(b.getUser());
        ticket.setBookingItem(item);
        ticket.setCode(UUID.randomUUID().toString());
        ticket.setIssuedAt(now);
        tickets.save(ticket);
      }
      NotificationOutbox n = new NotificationOutbox();
      n.setBookingId(id);
      n.setRecipient(b.getUser().getEmail());
      n.setNextAttemptAt(now);
      outbox.save(n);
      publisher.publishEvent(new BookingConfirmedEvent(id));
    }
    return new PaymentView(id, p.getOutcome(), b.getStatus());
  }

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public void expire(Long id) {
    Booking b = bookings.lockById(id).orElse(null);
    if (b != null && "HELD".equals(b.getStatus()) && !b.getExpiresAt().isAfter(clock.instant())) {
      release(b);
      b.setStatus("EXPIRED");
    }
  }

  private void release(Booking b) {
    inventory.lockForBooking(b.getId()).forEach(s -> s.setBooking(null));
  }

  private Booking owned(Booking b, Long userId) {
    if (!b.getUser().getId().equals(userId)) throw ApiException.missing("Booking");
    return b;
  }

  private String effectiveStatus(Booking b) {
    return "HELD".equals(b.getStatus()) && !b.getExpiresAt().isAfter(clock.instant())
        ? "EXPIRED"
        : b.getStatus();
  }

  private BookingView view(Booking b) {
    return new BookingView(
        b.getId(),
        b.getEvent().getId(),
        effectiveStatus(b),
        b.getExpiresAt(),
        b.getTotal(),
        b.getCurrency(),
        b.getItems().stream()
            .map(
                i ->
                    new ItemView(
                        i.getId(),
                        i.getEventSeat().getId(),
                        i.getEventSeat().getSeat().getLabel(),
                        i.getPrice(),
                        i.getCurrency()))
            .toList());
  }

  private String hash(String input) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
