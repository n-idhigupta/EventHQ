package com.eventhq.event;

import com.fasterxml.jackson.annotation.*;
import jakarta.persistence.*;
import java.util.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(
    name = "event_seats",
    uniqueConstraints = @UniqueConstraint(columnNames = {"event_id", "seat_id"}))
@Getter
@Setter
public class EventSeat {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(name = "event_id")
  private Event event;

  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(name = "seat_id")
  private com.eventhq.venue.Seat seat;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "booking_id")
  private com.eventhq.booking.Booking booking;
}
