package com.psytrance.psytrance_tracker_backend.service;

import com.psytrance.psytrance_tracker_backend.model.AdminChange;
import com.psytrance.psytrance_tracker_backend.model.HiddenEvent;
import com.psytrance.psytrance_tracker_backend.repository.AdminChangeRepository;
import com.psytrance.psytrance_tracker_backend.repository.HiddenEventRepository;
import com.psytrance.psytrance_tracker_backend.repository.LineupScanRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Admins hide events that don't belong on the site (not psytrance, a duplicate): {@link Parties} leaves them out, so
 * they leave the lists, the map, the artist index and the reminders. Hiding and showing again are in the history.
 */
@Service
public class EventHiding {

    private final HiddenEventRepository repository;
    private final EventService eventService;
    private final LineupScanRepository lineupScanRepository;
    private final AdminChangeRepository changeRepository;
    private final Clock clock;

    public EventHiding(HiddenEventRepository repository, EventService eventService, LineupScanRepository lineupScanRepository,
                       AdminChangeRepository changeRepository, Clock clock) {
        this.repository = repository;
        this.eventService = eventService;
        this.lineupScanRepository = lineupScanRepository;
        this.changeRepository = changeRepository;
        this.clock = clock;
    }

    public record Hidden(long eventId, String name, String reason, String hiddenBy, Instant hiddenAt) {
        static Hidden of(HiddenEvent event) {
            return new Hidden(event.getEventId(), event.getName(), event.getReason(), event.getHiddenBy(), event.getHiddenAt());
        }
    }

    public List<Hidden> list() {
        return repository.findAllByOrderByHiddenAtDesc().stream().map(Hidden::of).toList();
    }

    @Transactional
    public Hidden hide(long eventId, String reason, String adminUsername) {
        if (repository.existsById(eventId)) {
            return Hidden.of(repository.getReferenceById(eventId));
        }
        String name = eventService.findById(eventId).nameParty();
        String why = reason == null || reason.isBlank() ? null : reason.strip();
        HiddenEvent hidden = repository.save(new HiddenEvent(eventId, name, why, adminUsername, clock.instant()));
        eventService.refreshSoon(eventId);
        changeRepository.save(new AdminChange(adminUsername, AdminChange.EVENT_HIDDEN, target(eventId, name),
                "shown", why == null ? "hidden" : "hidden: " + why, clock.instant()));
        return Hidden.of(hidden);
    }

    @Transactional
    public void show(long eventId, String adminUsername) {
        HiddenEvent hidden = repository.findById(eventId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event " + eventId + " is not hidden"));
        repository.delete(hidden);
        eventService.refreshSoon(eventId);
        // Read soon again, in case the line-up changed while it was hidden
        lineupScanRepository.rereadSoon(List.of(eventId));
        changeRepository.save(new AdminChange(adminUsername, AdminChange.EVENT_HIDDEN, target(eventId, hidden.getName()),
                "hidden", "shown", clock.instant()));
    }

    private static String target(long eventId, String name) {
        return name + " (#" + eventId + ")";
    }
}
