package com.psytrance.psytrance_tracker_backend.service;

import com.psytrance.psytrance_tracker_backend.dto.AdminEventGenresDto;
import com.psytrance.psytrance_tracker_backend.dto.GenreShare;
import com.psytrance.psytrance_tracker_backend.model.AdminChange;
import com.psytrance.psytrance_tracker_backend.model.LineupScan;
import com.psytrance.psytrance_tracker_backend.repository.AdminChangeRepository;
import com.psytrance.psytrance_tracker_backend.repository.LineupScanRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Genres an admin sets by hand for one event, when the line-up doesn't tell (or tells wrong). They are
 * stored as MANUAL, which the artist index never overwrites; setting none hands the event back to it.
 * Every change is logged in admin_changes, as "forest, twilight" for hand-set genres and "" for automatic.
 */
@Service
public class EventGenreAdmin {

    private final MapService mapService;
    private final EventService eventService;
    private final MapGenres mapGenres;
    private final AdminChangeRepository changeRepository;
    private final LineupScanRepository lineupScanRepository;
    private final Clock clock;

    public EventGenreAdmin(MapService mapService, EventService eventService, MapGenres mapGenres,
                           AdminChangeRepository changeRepository, LineupScanRepository lineupScanRepository, Clock clock) {
        this.mapService = mapService;
        this.eventService = eventService;
        this.mapGenres = mapGenres;
        this.changeRepository = changeRepository;
        this.lineupScanRepository = lineupScanRepository;
        this.clock = clock;
    }

    public AdminEventGenresDto get(long eventId) {
        String name = eventService.findById(eventId).nameParty();
        List<GenreShare> genres = mapService.findGenres(List.of(eventId)).getOrDefault(eventId, List.of());
        boolean manual = genres.stream().anyMatch(g -> g.source() == GenreShare.Source.MANUAL);
        return new AdminEventGenresDto(eventId, name, genres, manual);
    }

    @Transactional
    public AdminEventGenresDto setGenres(long eventId, List<String> genreIds, String adminUsername) {
        return setGenres(eventId, genreIds, adminUsername, null);
    }

    /** Hand-set genres, shared equally; none goes back to the genres worked out from the line-up. */
    @Transactional
    AdminEventGenresDto setGenres(long eventId, List<String> genreIds, String adminUsername, Long undoOf) {
        List<String> wanted = List.copyOf(new LinkedHashSet<>(genreIds));
        for (String genreId : wanted) {
            if (!mapGenres.isGenre(genreId)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown genre: " + genreId);
            }
        }
        AdminEventGenresDto before = get(eventId);
        String beforeValue = manualValue(before);
        String afterValue = String.join(", ", wanted);
        if (beforeValue.equals(afterValue)) {
            return before;
        }

        if (wanted.isEmpty()) {
            mapService.replaceGenres(eventId, List.of());
            // Read soon again, so the worked-out genres come back within a few minutes
            lineupScanRepository.findById(eventId).ifPresent(scan -> {
                scan.setScannedAt(Instant.EPOCH);
                lineupScanRepository.save(scan);
            });
        } else {
            mapService.replaceGenres(eventId, wanted.stream()
                    .map(id -> new GenreShare(id, 1.0 / wanted.size(), GenreShare.Source.MANUAL))
                    .toList());
            // Marked as read, so the map doesn't show it as "line-up not read yet"
            if (!lineupScanRepository.existsById(eventId)) {
                lineupScanRepository.save(new LineupScan(eventId, clock.instant()));
            }
        }
        changeRepository.save(new AdminChange(adminUsername, AdminChange.EVENT_GENRES, target(eventId, before.nameParty()),
                beforeValue, afterValue, clock.instant(), undoOf));
        return get(eventId);
    }

    /** What the history stores for the event now: the hand-set genres, or "" when they are worked out */
    String manualValue(long eventId) {
        return manualValue(get(eventId));
    }

    private static String manualValue(AdminEventGenresDto event) {
        return event.manual()
                ? String.join(", ", event.genres().stream().map(GenreShare::genreId).toList())
                : "";
    }

    /** "118537 · OUT OF SPACE", cut to fit the history column */
    private static String target(long eventId, String name) {
        String target = eventId + " · " + name;
        return target.length() > 200 ? target.substring(0, 199) + "…" : target;
    }

    /** The event id at the start of a history target */
    static long eventIdOf(String target) {
        return Long.parseLong(target.substring(0, target.indexOf(' ')));
    }
}
