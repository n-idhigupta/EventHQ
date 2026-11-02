package com.eventhq.event;

import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface EventRepository extends JpaRepository<Event, Long> {
  Page<Event> findByPublishedTrue(Pageable pageable);

  @Query("select e from Event e join fetch e.venue where e.id=:id and e.published=true")
  Optional<Event> findPublishedWithVenue(@Param("id") Long id);

  boolean existsByVenueId(Long venueId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select e from Event e where e.id=:id")
  Optional<Event> lockById(@Param("id") Long id);
}
