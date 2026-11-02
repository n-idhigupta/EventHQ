package com.eventhq.event;

import com.fasterxml.jackson.annotation.*;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "events")
@Getter
@Setter
public class Event {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 150)
  private String name;

  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(name = "venue_id")
  private com.eventhq.venue.Venue venue;

  @Column(nullable = false)
  @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.TIMESTAMP)
  private Instant startsAt;

  @Column(nullable = false)
  @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.TIMESTAMP)
  private Instant earlyBirdUntil;

  @Column(nullable = false, precision = 19, scale = 2)
  private BigDecimal basePrice;

  @Column(nullable = false, length = 3)
  private String currency;

  @Column(nullable = false)
  private boolean published;

  @ManyToMany
  @JoinTable(
      name = "event_sponsors",
      joinColumns = @JoinColumn(name = "event_id"),
      inverseJoinColumns = @JoinColumn(name = "sponsor_id"))
  @JsonIgnore
  private Set<com.eventhq.sponsor.Sponsor> sponsors = new HashSet<>();
}
