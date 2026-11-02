package com.eventhq.event;

import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface EventSeatRepository extends JpaRepository<EventSeat, Long> {
  List<EventSeat> findByEventIdOrderById(Long eventId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select s from EventSeat s where s.event.id=:eventId and s.id in :ids order by s.id")
  List<EventSeat> lockSeats(@Param("eventId") Long eventId, @Param("ids") List<Long> ids);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select s from EventSeat s where s.booking.id=:bookingId order by s.id")
  List<EventSeat> lockForBooking(@Param("bookingId") Long bookingId);
}
