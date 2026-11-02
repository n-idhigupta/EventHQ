package com.eventhq.ticket;

import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;

public interface TicketRepository extends JpaRepository<Ticket, Long> {
  List<Ticket> findByUserIdOrderByIdDesc(Long userId);
}
