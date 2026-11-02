package com.eventhq.sponsor;

import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;

public interface SponsorRepository extends JpaRepository<Sponsor, Long> {}
