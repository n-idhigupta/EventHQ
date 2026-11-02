package com.eventhq.ticket;

import com.fasterxml.jackson.annotation.*;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "tickets")
@Getter
@Setter
public class Ticket {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id")
  @JsonIgnore
  private com.eventhq.user.User user;

  @OneToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(name = "booking_item_id", unique = true)
  private com.eventhq.booking.BookingItem bookingItem;

  @Column(nullable = false, unique = true, length = 36)
  private String code;

  @Column(nullable = false)
  @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.TIMESTAMP)
  private Instant issuedAt;
}
