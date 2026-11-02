package com.eventhq.event;

import static com.eventhq.event.CatalogDtos.*;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class CatalogController {
  private final CatalogService service;

  @GetMapping("/venues")
  public List<VenueView> venues() {
    return service.venues();
  }

  @GetMapping("/venues/{id}")
  public VenueView venue(@PathVariable Long id) {
    return service.venue(id);
  }

  @GetMapping("/venues/{id}/seats")
  public List<SeatView> seats(@PathVariable Long id) {
    return service.seats(id);
  }

  @GetMapping("/events")
  public PageView<EventView> events(
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
    return service.events(page, size, false);
  }

  @GetMapping("/events/{id}")
  public EventView event(@PathVariable Long id) {
    return service.event(id, false);
  }

  @GetMapping("/events/{id}/seats")
  public List<InventoryView> inventory(@PathVariable Long id) {
    return service.availability(id);
  }
}
