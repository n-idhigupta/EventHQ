package com.eventhq.payment;

import com.fasterxml.jackson.annotation.*;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "payment_attempts")
@Getter
@Setter
public class PaymentAttempt {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(name = "booking_id")
  private com.eventhq.booking.Booking booking;

  @Column(nullable = false, unique = true, length = 80)
  private String idempotencyKey;

  @Column(nullable = false, length = 20)
  private String outcome;

  @Column(nullable = false, length = 64)
  private String requestHash;

  @Column(nullable = false)
  @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.TIMESTAMP)
  private Instant createdAt;
}
