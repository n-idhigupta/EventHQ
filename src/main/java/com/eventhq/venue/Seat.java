package com.eventhq.venue;

import com.fasterxml.jackson.annotation.*;
import jakarta.persistence.*;
import java.util.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "seats", uniqueConstraints = @UniqueConstraint(columnNames = {"venue_id", "label"}))
@Getter
@Setter
public class Seat {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(name = "venue_id")
  @JsonBackReference
  private Venue venue;

  @Column(nullable = false, length = 40)
  private String label;

  @Column(nullable = false, length = 20)
  private String tier;
}
