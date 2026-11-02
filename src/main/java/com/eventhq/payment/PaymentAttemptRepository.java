package com.eventhq.payment;

import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;

public interface PaymentAttemptRepository extends JpaRepository<PaymentAttempt, Long> {
  Optional<PaymentAttempt> findByIdempotencyKey(String key);
}
