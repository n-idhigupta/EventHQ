package com.eventhq.notification;

import com.fasterxml.jackson.annotation.*;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "notification_outbox")
@Getter
@Setter
public class NotificationOutbox {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true)
  private Long bookingId;

  @Column(nullable = false, length = 254)
  private String recipient;

  @Column(nullable = false)
  private int attempts;

  @Column(nullable = false)
  private boolean delivered;

  @Column(nullable = false)
  @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.TIMESTAMP)
  private Instant nextAttemptAt;
}
