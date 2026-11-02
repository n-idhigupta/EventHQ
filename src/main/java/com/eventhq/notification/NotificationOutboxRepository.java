package com.eventhq.notification;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface NotificationOutboxRepository extends JpaRepository<NotificationOutbox, Long> {
  @Query(
      "select n.id from NotificationOutbox n where n.delivered=false and n.nextAttemptAt<=:now"
          + " order by n.id")
  List<Long> due(@Param("now") Instant now, Pageable pageable);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select n from NotificationOutbox n where n.id=:id")
  Optional<NotificationOutbox> lockById(@Param("id") Long id);

  Optional<NotificationOutbox> findByBookingId(Long bookingId);
}
