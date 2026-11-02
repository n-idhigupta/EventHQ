package com.eventhq.booking;

import com.fasterxml.jackson.annotation.*;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "bookings")
@Getter
@Setter
public class Booking {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id")
  private com.eventhq.user.User user;

  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(name = "event_id")
  private com.eventhq.event.Event event;

  @Column(nullable = false, length = 20)
  private String status;

  @Column(nullable = false)
  @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.TIMESTAMP)
  private Instant expiresAt;

  @Column(nullable = false)
  @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.TIMESTAMP)
  private Instant createdAt;

  @Column(nullable = false, precision = 19, scale = 2)
  private BigDecimal total;

  @Column(nullable = false, length = 3)
  private String currency;

  @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL)
  @JsonManagedReference
  private List<BookingItem> items = new ArrayList<>();
}
