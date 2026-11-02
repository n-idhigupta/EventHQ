package com.eventhq.event;

import static com.eventhq.event.CatalogDtos.*;

import com.eventhq.config.EventHqProperties;
import com.eventhq.exception.ApiException;
import com.eventhq.sponsor.*;
import com.eventhq.venue.*;
import java.time.Clock;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CatalogService {
  private final VenueRepository venues;
  private final SeatRepository seats;
  private final SponsorRepository sponsors;
  private final EventRepository events;
  private final EventSeatRepository inventory;
  private final EventHqProperties properties;
  private final Clock clock;

  public List<VenueView> venues() {
    return venues.findAll(Sort.by("id")).stream().map(this::venueView).toList();
  }

  public VenueView venue(Long id) {
    return venueView(venues.findById(id).orElseThrow(() -> ApiException.missing("Venue")));
  }

  public List<SeatView> seats(Long id) {
    venue(id);
    return seats.findByVenueIdOrderById(id).stream()
        .map(s -> new SeatView(s.getId(), s.getLabel(), s.getTier()))
        .toList();
  }

  @Transactional
  public VenueView createVenue(VenueInput r) {
    Venue v = new Venue();
    applyVenue(v, r);
    return venueView(venues.save(v));
  }

  @Transactional
  public VenueView updateVenue(Long id, VenueInput r) {
    Venue v = venues.lockById(id).orElseThrow(() -> ApiException.missing("Venue"));
    applyVenue(v, r);
    return venueView(v);
  }

  @Transactional
  public void deleteVenue(Long id) {
    Venue v = venues.lockById(id).orElseThrow(() -> ApiException.missing("Venue"));
    if (events.existsByVenueId(id)) throw ApiException.conflict("Venue is used by events");
    seats.deleteAll(seats.findByVenueIdOrderById(id));
    venues.delete(v);
  }

  @Transactional
  public SeatView createSeat(Long id, SeatInput r) {
    Venue v = editableVenue(id);
    Seat s = new Seat();
    s.setVenue(v);
    s.setLabel(r.label().strip());
    s.setTier(r.tier().name());
    seats.saveAndFlush(s);
    return new SeatView(s.getId(), s.getLabel(), s.getTier());
  }

  @Transactional
  public SeatView updateSeat(Long venueId, Long seatId, SeatInput r) {
    editableVenue(venueId);
    Seat s =
        seats
            .findById(seatId)
            .filter(x -> x.getVenue().getId().equals(venueId))
            .orElseThrow(() -> ApiException.missing("Seat"));
    s.setLabel(r.label().strip());
    s.setTier(r.tier().name());
    return new SeatView(s.getId(), s.getLabel(), s.getTier());
  }

  @Transactional
  public void deleteSeat(Long venueId, Long seatId) {
    editableVenue(venueId);
    Seat s =
        seats
            .findById(seatId)
            .filter(x -> x.getVenue().getId().equals(venueId))
            .orElseThrow(() -> ApiException.missing("Seat"));
    seats.delete(s);
  }

  private Venue editableVenue(Long id) {
    Venue v = venues.lockById(id).orElseThrow(() -> ApiException.missing("Venue"));
    if (events.existsByVenueId(id))
      throw ApiException.conflict("Seating is frozen once a venue has an event");
    return v;
  }

  public List<SponsorView> sponsors() {
    return sponsors.findAll(Sort.by("id")).stream()
        .map(s -> new SponsorView(s.getId(), s.getName()))
        .toList();
  }

  @Transactional
  public SponsorView createSponsor(SponsorInput r) {
    Sponsor s = new Sponsor();
    s.setName(r.name().strip());
    sponsors.save(s);
    return new SponsorView(s.getId(), s.getName());
  }

  @Transactional
  public SponsorView updateSponsor(Long id, SponsorInput r) {
    Sponsor s = sponsors.findById(id).orElseThrow(() -> ApiException.missing("Sponsor"));
    s.setName(r.name().strip());
    return new SponsorView(s.getId(), s.getName());
  }

  @Transactional
  public void deleteSponsor(Long id) {
    Sponsor s = sponsors.findById(id).orElseThrow(() -> ApiException.missing("Sponsor"));
    sponsors.delete(s);
    sponsors.flush();
  }

  @Transactional
  public EventView createEvent(EventInput r) {
    Venue v = venues.lockById(r.venueId()).orElseThrow(() -> ApiException.missing("Venue"));
    if (seats.findByVenueIdOrderById(v.getId()).isEmpty())
      throw ApiException.bad("Venue must have seats");
    Event e = new Event();
    e.setVenue(v);
    applyEvent(e, r);
    e.setCurrency(properties.currency());
    return eventView(events.save(e));
  }

  @Transactional
  public EventView updateEvent(Long id, EventInput r) {
    Event e = events.lockById(id).orElseThrow(() -> ApiException.missing("Event"));
    if (e.isPublished()) throw ApiException.conflict("Published event cannot be edited");
    if (!e.getVenue().getId().equals(r.venueId()))
      throw ApiException.bad("Venue cannot be changed; create a new draft");
    applyEvent(e, r);
    return eventView(e);
  }

  @Transactional
  public void deleteEvent(Long id) {
    Event e = events.lockById(id).orElseThrow(() -> ApiException.missing("Event"));
    if (e.isPublished()) throw ApiException.conflict("Published event cannot be deleted");
    events.delete(e);
  }

  @Transactional
  public EventView publish(Long id) {
    Event e = events.lockById(id).orElseThrow(() -> ApiException.missing("Event"));
    if (e.isPublished()) return eventView(e);
    if (!e.getStartsAt().isAfter(clock.instant()))
      throw ApiException.bad("Event must start in the future");
    for (Seat s : seats.findByVenueIdOrderById(e.getVenue().getId())) {
      EventSeat es = new EventSeat();
      es.setEvent(e);
      es.setSeat(s);
      inventory.save(es);
    }
    e.setPublished(true);
    return eventView(e);
  }

  public EventView event(Long id, boolean admin) {
    Event e = events.findById(id).orElseThrow(() -> ApiException.missing("Event"));
    if (!admin && !e.isPublished()) throw ApiException.missing("Event");
    return eventView(e);
  }

  public PageView<EventView> events(int page, int size, boolean admin) {
    if (page < 0 || size < 1 || size > 100)
      throw ApiException.bad("page must be nonnegative and size between 1 and 100");
    var p = PageRequest.of(page, size, Sort.by("startsAt", "id"));
    Page<Event> result = admin ? events.findAll(p) : events.findByPublishedTrue(p);
    return new PageView<>(
        result.stream().map(this::eventView).toList(),
        page,
        size,
        result.getTotalElements(),
        result.getTotalPages());
  }

  public List<InventoryView> availability(Long id) {
    event(id, false);
    var now = clock.instant();
    return inventory.findByEventIdOrderById(id).stream()
        .map(
            s -> {
              var b = s.getBooking();
              boolean available =
                  b == null
                      || (!"PAID".equals(b.getStatus())
                          && (!"HELD".equals(b.getStatus()) || !b.getExpiresAt().isAfter(now)));
              return new InventoryView(
                  s.getId(),
                  s.getSeat().getId(),
                  s.getSeat().getLabel(),
                  s.getSeat().getTier(),
                  available);
            })
        .toList();
  }

  private void applyVenue(Venue v, VenueInput r) {
    v.setName(r.name().strip());
    v.setAddress(r.address().strip());
    v.setLatitude(r.latitude());
    v.setLongitude(r.longitude());
  }

  private void applyEvent(Event e, EventInput r) {
    if (r.earlyBirdUntil().isAfter(r.startsAt()))
      throw ApiException.bad("Early bird cutoff must not follow event start");
    var ids = new HashSet<>(r.sponsorIds());
    var found = sponsors.findAllById(ids);
    if (found.size() != ids.size()) throw ApiException.missing("Sponsor");
    e.setName(r.name().strip());
    e.setStartsAt(r.startsAt());
    e.setEarlyBirdUntil(r.earlyBirdUntil());
    e.setBasePrice(r.basePrice());
    e.setSponsors(new HashSet<>(found));
  }

  private VenueView venueView(Venue v) {
    return new VenueView(v.getId(), v.getName(), v.getAddress(), v.getLatitude(), v.getLongitude());
  }

  private EventView eventView(Event e) {
    return new EventView(
        e.getId(),
        e.getName(),
        e.getVenue().getId(),
        e.getStartsAt(),
        e.getEarlyBirdUntil(),
        e.getBasePrice(),
        e.getCurrency(),
        e.isPublished(),
        e.getSponsors().stream()
            .sorted(Comparator.comparing(Sponsor::getId))
            .map(s -> new SponsorView(s.getId(), s.getName()))
            .toList());
  }
}
