package com.eventhq.venue;

import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;

public interface SeatRepository extends JpaRepository<Seat, Long> {
  List<Seat> findByVenueIdOrderById(Long venueId);
}
