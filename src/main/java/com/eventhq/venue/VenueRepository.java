package com.eventhq.venue;

import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface VenueRepository extends JpaRepository<Venue, Long> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select v from Venue v where v.id=:id")
  Optional<Venue> lockById(@Param("id") Long id);
}
