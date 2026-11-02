package com.eventhq.event;

import static com.eventhq.event.CatalogDtos.*;

import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminCatalogController {
  private final CatalogService service;

  @PostMapping("/venues")
  @ResponseStatus(HttpStatus.CREATED)
  public VenueView createVenue(@Valid @RequestBody VenueInput r) {
    return service.createVenue(r);
  }

  @PutMapping("/venues/{id}")
  public VenueView updateVenue(@PathVariable Long id, @Valid @RequestBody VenueInput r) {
    return service.updateVenue(id, r);
  }

  @DeleteMapping("/venues/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteVenue(@PathVariable Long id) {
    service.deleteVenue(id);
  }

  @PostMapping("/venues/{id}/seats")
  @ResponseStatus(HttpStatus.CREATED)
  public SeatView createSeat(@PathVariable Long id, @Valid @RequestBody SeatInput r) {
    return service.createSeat(id, r);
  }

  @PutMapping("/venues/{id}/seats/{seatId}")
  public SeatView updateSeat(
      @PathVariable Long id, @PathVariable Long seatId, @Valid @RequestBody SeatInput r) {
    return service.updateSeat(id, seatId, r);
  }

  @DeleteMapping("/venues/{id}/seats/{seatId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteSeat(@PathVariable Long id, @PathVariable Long seatId) {
    service.deleteSeat(id, seatId);
  }

  @GetMapping("/sponsors")
  public List<SponsorView> sponsors() {
    return service.sponsors();
  }

  @PostMapping("/sponsors")
  @ResponseStatus(HttpStatus.CREATED)
  public SponsorView createSponsor(@Valid @RequestBody SponsorInput r) {
    return service.createSponsor(r);
  }

  @PutMapping("/sponsors/{id}")
  public SponsorView updateSponsor(@PathVariable Long id, @Valid @RequestBody SponsorInput r) {
    return service.updateSponsor(id, r);
  }

  @DeleteMapping("/sponsors/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteSponsor(@PathVariable Long id) {
    service.deleteSponsor(id);
  }

  @GetMapping("/events")
  public PageView<EventView> events(
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
    return service.events(page, size, true);
  }

  @GetMapping("/events/{id}")
  public EventView event(@PathVariable Long id) {
    return service.event(id, true);
  }

  @PostMapping("/events")
  @ResponseStatus(HttpStatus.CREATED)
  public EventView createEvent(@Valid @RequestBody EventInput r) {
    return service.createEvent(r);
  }

  @PutMapping("/events/{id}")
  public EventView updateEvent(@PathVariable Long id, @Valid @RequestBody EventInput r) {
    return service.updateEvent(id, r);
  }

  @DeleteMapping("/events/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteEvent(@PathVariable Long id) {
    service.deleteEvent(id);
  }

  @PostMapping("/events/{id}/publish")
  public EventView publish(@PathVariable Long id) {
    return service.publish(id);
  }
}
