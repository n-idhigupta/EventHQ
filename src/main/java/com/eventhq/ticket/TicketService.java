package com.eventhq.ticket;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TicketService {
  private final TicketRepository tickets;

  public record TicketView(
      String code,
      Long bookingId,
      Long eventId,
      String seat,
      BigDecimal price,
      String currency,
      Instant issuedAt) {}

  @Transactional(readOnly = true)
  public List<TicketView> owned(Long userId) {
    return tickets.findByUserIdOrderByIdDesc(userId).stream()
        .map(
            t -> {
              var i = t.getBookingItem();
              return new TicketView(
                  t.getCode(),
                  i.getBooking().getId(),
                  i.getBooking().getEvent().getId(),
                  i.getEventSeat().getSeat().getLabel(),
                  i.getPrice(),
                  i.getCurrency(),
                  t.getIssuedAt());
            })
        .toList();
  }
}
