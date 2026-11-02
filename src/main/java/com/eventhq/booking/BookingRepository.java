package com.eventhq.booking;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface BookingRepository extends JpaRepository<Booking, Long> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select b from Booking b where b.id=:id")
  Optional<Booking> lockById(@Param("id") Long id);

  @Query("select b.id from Booking b where b.status='HELD' and b.expiresAt<=:now order by b.id")
  List<Long> expiredIds(@Param("now") Instant now, Pageable pageable);
}
