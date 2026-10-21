package com.psytrance.psytrance_tracker_backend.service;

import com.psytrance.psytrance_tracker_backend.client.GoabaseClient;
import com.psytrance.psytrance_tracker_backend.dto.TimetableDto;
import com.psytrance.psytrance_tracker_backend.model.AdminChange;
import com.psytrance.psytrance_tracker_backend.model.TimetableSlot;
import com.psytrance.psytrance_tracker_backend.repository.AdminChangeRepository;
import com.psytrance.psytrance_tracker_backend.repository.LineupScanRepository;
import com.psytrance.psytrance_tracker_backend.repository.TimetableSlotRepository;
import com.psytrance.psytrance_tracker_backend.util.ArtistNames;
import com.psytrance.psytrance_tracker_backend.util.TimetableParser;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Event timetables. The artist index fills them from set times in line-ups ("21:00 Artist"); an admin can correct
 * one or write it from scratch, which makes it MANUAL: from then on the line-up no longer changes it, until the
 * admin hands it back. Times are on the clock at the venue. Admin changes are logged in admin_changes (as a count of sets; they can't be undone).
 */
@Service
public class EventTimetables {

    static final int MAX_SETS = 200;

    private final TimetableSlotRepository slotRepository;
    private final LineupScanRepository lineupScanRepository;
    private final AdminChangeRepository changeRepository;
    private final EventService eventService;
    private final GoabaseClient goabaseClient;
    private final Clock clock;

    public EventTimetables(TimetableSlotRepository slotRepository, LineupScanRepository lineupScanRepository,
                           AdminChangeRepository changeRepository, EventService eventService,
                           GoabaseClient goabaseClient, Clock clock) {
        this.slotRepository = slotRepository;
        this.lineupScanRepository = lineupScanRepository;
        this.changeRepository = changeRepository;
        this.eventService = eventService;
        this.goabaseClient = goabaseClient;
        this.clock = clock;
    }

    public TimetableDto get(long eventId) {
        return toDto(eventId, slotRepository.findByEventIdOrderByPositionAsc(eventId));
    }

    /** The timetables of these events in one query, for the galaxy map; events without set times are left out */
    public List<TimetableDto> forEvents(Collection<Long> eventIds) {
        if (eventIds.isEmpty()) {
            return List.of();
        }
        Map<Long, List<TimetableSlot>> byEvent = slotRepository.findByEventIdInOrderByEventIdAscPositionAsc(eventIds).stream()
                .collect(Collectors.groupingBy(TimetableSlot::getEventId, LinkedHashMap::new, Collectors.toList()));
        return byEvent.entrySet().stream().map(e -> toDto(e.getKey(), e.getValue())).toList();
    }

    private static TimetableDto toDto(long eventId, List<TimetableSlot> slots) {
        boolean manual = slots.stream().anyMatch(s -> TimetableSlot.MANUAL.equals(s.getSource()));
        Integer offset = slots.isEmpty() ? null : slots.get(0).getUtcOffsetMinutes();
        return new TimetableDto(eventId, manual, offset, slots.stream()
                .map(s -> new TimetableDto.Set(s.getStage(), s.getArtistName(), ArtistNames.slug(s.getArtistName()),
                        s.getStartsAt(), s.getEndsAt()))
                .toList());
    }

    /** The timetable read from a line-up; an admin's timetable is kept as it is */
    @Transactional
    public void saveParsed(long eventId, List<TimetableParser.Slot> slots) {
        if (slotRepository.existsByEventIdAndSource(eventId, TimetableSlot.MANUAL)) {
            return;
        }
        Integer offset = slots.isEmpty() ? null : slots.get(0).startsAt().getOffset().getTotalSeconds() / 60;
        replace(eventId, slots.stream()
                .map(s -> new TimetableDto.Set(s.stage(), s.artist(), null, s.startsAt().toLocalDateTime(),
                        s.endsAt() == null ? null : s.endsAt().toLocalDateTime()))
                .toList(), offset, TimetableSlot.AUTO);
    }

    /** An admin's timetable for the event; it replaces the one read from the line-up */
    @Transactional
    public TimetableDto setManual(long eventId, List<TimetableDto.Set> sets, String adminUsername) {
        if (sets.size() > MAX_SETS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At most " + MAX_SETS + " sets");
        }
        for (TimetableDto.Set set : sets) {
            if (set.artist() == null || set.artist().isBlank() || set.startsAt() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Every set needs an artist and a start time");
            }
            if (set.endsAt() != null && !set.endsAt().isAfter(set.startsAt())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, set.artist().strip() + " ends before they start");
            }
        }
        TimetableDto before = get(eventId);
        replace(eventId, sets, before.utcOffsetMinutes() != null ? before.utcOffsetMinutes() : venueOffset(eventId),
                TimetableSlot.MANUAL);
        log(eventId, before, sets.size() + " sets, set by hand", adminUsername);
        return get(eventId);
    }

    /** Back to the timetable in the line-up: read again within a few minutes */
    @Transactional
    public TimetableDto handBack(long eventId, String adminUsername) {
        TimetableDto before = get(eventId);
        if (!before.manual()) {
            return before;
        }
        slotRepository.deleteByEventId(eventId);
        lineupScanRepository.findById(eventId).ifPresent(scan -> {
            scan.setScannedAt(Instant.EPOCH);
            lineupScanRepository.save(scan);
        });
        log(eventId, before, "from the line-up", adminUsername);
        return get(eventId);
    }

    // The venue clock's UTC offset, from the start time Goabase gives ("2026-10-24T22:00:00+02:00"); null when unknown
    private Integer venueOffset(long eventId) {
        try {
            return goabaseClient.fetchParty(eventId)
                    .map(party -> OffsetDateTime.parse(party.dateStart()).getOffset().getTotalSeconds() / 60)
                    .orElse(null);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private void replace(long eventId, List<TimetableDto.Set> sets, Integer utcOffsetMinutes, String source) {
        slotRepository.deleteByEventId(eventId);
        slotRepository.flush();
        List<TimetableSlot> rows = new ArrayList<>();
        for (int i = 0; i < sets.size(); i++) {
            TimetableDto.Set set = sets.get(i);
            String stage = set.stage() == null || set.stage().isBlank() ? null : cut(set.stage().strip());
            rows.add(new TimetableSlot(eventId, i, stage, cut(set.artist().strip()), set.startsAt(), set.endsAt(), utcOffsetMinutes, source));
        }
        slotRepository.saveAll(rows);
    }

    private void log(long eventId, TimetableDto before, String after, String adminUsername) {
        String name = eventService.findById(eventId).nameParty();
        String target = eventId + " · " + name;
        String beforeValue = before.sets().isEmpty() ? "no timetable"
                : before.sets().size() + " sets, " + (before.manual() ? "set by hand" : "from the line-up");
        changeRepository.save(new AdminChange(adminUsername, AdminChange.EVENT_TIMETABLE,
                target.length() > 200 ? target.substring(0, 199) + "…" : target, beforeValue, after, clock.instant()));
    }

    private static String cut(String value) {
        return value.length() > 100 ? value.substring(0, 100) : value;
    }
}
