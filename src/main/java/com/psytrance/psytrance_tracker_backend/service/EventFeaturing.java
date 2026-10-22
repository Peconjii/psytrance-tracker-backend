package com.psytrance.psytrance_tracker_backend.service;

import com.psytrance.psytrance_tracker_backend.dto.EventDto;
import com.psytrance.psytrance_tracker_backend.model.AdminChange;
import com.psytrance.psytrance_tracker_backend.model.FeaturedEvent;
import com.psytrance.psytrance_tracker_backend.repository.AdminChangeRepository;
import com.psytrance.psytrance_tracker_backend.repository.FeaturedEventRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Admins feature a few events on the map: the ship is marked and the event leads the departures. Only upcoming
 * events count towards the limit, so an event that is over makes room by itself. In the history (EVENT_FEATURED).
 */
@Service
public class EventFeaturing {

    /** Featured at once: more and nothing stands out */
    static final int MAX_FEATURED = 3;

    private final FeaturedEventRepository repository;
    private final EventService eventService;
    private final AdminChangeRepository changeRepository;
    private final Clock clock;

    public EventFeaturing(FeaturedEventRepository repository, EventService eventService,
                          AdminChangeRepository changeRepository, Clock clock) {
        this.repository = repository;
        this.eventService = eventService;
        this.changeRepository = changeRepository;
        this.clock = clock;
    }

    public record Featured(long eventId, String name, String note, String featuredBy, Instant featuredAt, boolean upcoming) {
    }

    /** Every featured event, newest first; {@code upcoming} false for the ones that are over */
    public List<Featured> list() {
        Set<Long> upcoming = upcomingIds();
        return repository.findAllByOrderByFeaturedAtDesc().stream()
                .map(f -> new Featured(f.getEventId(), f.getName(), f.getNote(), f.getFeaturedBy(), f.getFeaturedAt(),
                        upcoming.contains(f.getEventId())))
                .toList();
    }

    /** Event id -> note ("" when none), for the map */
    public Map<Long, String> notesById() {
        return repository.findAll().stream()
                .collect(Collectors.toMap(FeaturedEvent::getEventId, f -> f.getNote() == null ? "" : f.getNote()));
    }

    @Transactional
    public Featured feature(long eventId, String note, String adminUsername) {
        EventDto event = eventService.findById(eventId);
        Set<Long> upcoming = upcomingIds();
        long current = repository.findAll().stream()
                .filter(f -> upcoming.contains(f.getEventId()) && f.getEventId() != eventId)
                .count();
        if (current >= MAX_FEATURED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Already " + MAX_FEATURED + " featured events: take one off first");
        }
        String text = note == null || note.isBlank() ? null : note.strip();
        repository.save(new FeaturedEvent(eventId, event.nameParty(), text, adminUsername, clock.instant()));
        changeRepository.save(new AdminChange(adminUsername, AdminChange.EVENT_FEATURED, event.nameParty() + " (#" + eventId + ")",
                "", text == null ? "featured" : "featured: " + text, clock.instant()));
        return new Featured(eventId, event.nameParty(), text, adminUsername, clock.instant(), upcoming.contains(eventId));
    }

    @Transactional
    public void unfeature(long eventId, String adminUsername) {
        repository.findById(eventId).ifPresent(f -> {
            repository.delete(f);
            changeRepository.save(new AdminChange(adminUsername, AdminChange.EVENT_FEATURED, f.getName() + " (#" + eventId + ")",
                    "featured", "", clock.instant()));
        });
    }

    private Set<Long> upcomingIds() {
        return eventService.findUpcoming().stream().map(EventDto::id).collect(Collectors.toSet());
    }
}
