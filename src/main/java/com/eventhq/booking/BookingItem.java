package com.eventhq.booking;

import com.fasterxml.jackson.annotation.*;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "booking_items")
@Getter
@Setter
public class BookingItem {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(name = "booking_id")
  @JsonBackReference
  private Booking booking;

  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(name = "event_seat_id")
  private com.eventhq.event.EventSeat eventSeat;

  @Column(nullable = false, precision = 19, scale = 2)
  private BigDecimal price;

  @Column(nullable = false, length = 3)
  private String currency;
}
