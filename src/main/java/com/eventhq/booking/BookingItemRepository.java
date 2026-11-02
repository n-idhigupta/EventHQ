package com.eventhq.booking;

import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;

public interface BookingItemRepository extends JpaRepository<BookingItem, Long> {}
