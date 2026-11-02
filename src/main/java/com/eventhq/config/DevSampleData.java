package com.eventhq.config;

import static com.eventhq.event.CatalogDtos.*;
import com.eventhq.event.CatalogService;
import com.eventhq.venue.VenueRepository;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("dev")
@ConditionalOnProperty(name = "eventhq.seed", havingValue = "true")
@RequiredArgsConstructor
public class DevSampleData implements ApplicationRunner {
  private final CatalogService catalog;
  private final VenueRepository venues;
  private final Clock clock;

  @Override
  public void run(ApplicationArguments args) {
    if (venues.count() > 0) return;
    String[] names = {"After Hours: Live Sessions", "Design Tomorrow", "The Sunday Social"};
    String[] halls = {"The Piano Room", "Studio Collective", "The Garden Pavilion"};
    String[] addresses = {"Hauz Khas, New Delhi", "Lodhi Colony, New Delhi", "Mehrauli, New Delhi"};
    String[] prices = {"1200.00", "850.00", "650.00"};
    for (int n = 0; n < names.length; n++) {
      var venue = catalog.createVenue(new VenueInput(halls[n], addresses[n], 28.55 + n * 0.02, 77.20));
      for (char row = 'A'; row <= 'C'; row++) {
        for (int seat = 1; seat <= 6; seat++) {
          catalog.createSeat(venue.id(), new SeatInput("" + row + seat, row == 'A' ? Tier.VIP : Tier.STANDARD));
        }
      }
      var event = catalog.createEvent(new EventInput(names[n], venue.id(),
          clock.instant().plus(Duration.ofDays(7L + n * 7L)),
          clock.instant().plus(Duration.ofDays(3)), new BigDecimal(prices[n]), List.of()));
      catalog.publish(event.id());
    }
  }
}
