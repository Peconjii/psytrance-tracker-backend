package com.psytrance.psytrance_tracker_backend.service.events;

import com.psytrance.psytrance_tracker_backend.service.map.MapGenres;


import com.psytrance.psytrance_tracker_backend.dto.TimetableDto;
import com.psytrance.psytrance_tracker_backend.model.AdminChange;
import com.psytrance.psytrance_tracker_backend.model.OurEvent;
import com.psytrance.psytrance_tracker_backend.repository.OurEventRepository;
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
import java.time.LocalDateTime;
import java.time.format.TextStyle;
import java.util.Comparator;
import java.util.Locale;
import java.util.regex.Pattern;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
    private final Parties parties;
    private final MapGenres mapGenres;
    private final Clock clock;
    private final OurEventRepository ourEventRepository;

    public EventTimetables(TimetableSlotRepository slotRepository, LineupScanRepository lineupScanRepository,
                           AdminChangeRepository changeRepository, EventService eventService,
                           Parties parties, MapGenres mapGenres, Clock clock, OurEventRepository ourEventRepository) {
        this.ourEventRepository = ourEventRepository;
        this.slotRepository = slotRepository;
        this.lineupScanRepository = lineupScanRepository;
        this.changeRepository = changeRepository;
        this.eventService = eventService;
        this.parties = parties;
        this.mapGenres = mapGenres;
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

    private TimetableDto toDto(long eventId, List<TimetableSlot> slots) {
        boolean manual = slots.stream().anyMatch(s -> TimetableSlot.MANUAL.equals(s.getSource()));
        Integer offset = slots.isEmpty() ? null : slots.get(0).getUtcOffsetMinutes();
        return new TimetableDto(eventId, manual, offset, slots.stream()
                .map(s -> new TimetableDto.Set(s.getStage(), s.getArtistName(), ArtistNames.slug(s.getArtistName()),
                        s.getStartsAt(), s.getEndsAt(), genreOf(s)))
                .toList());
    }

    // An admin who picks a set's genre: kept in the note as "#genre:forest"
    private static final String GENRE_NOTE = "#genre:";

    /** The set's genre: the artist's genre on the map, else the genre named next to the set, else the stage's */
    private String genreOf(TimetableSlot slot) {
        String note = slot.getNote();
        if (note != null && note.startsWith(GENRE_NOTE)) {
            String id = note.substring(GENRE_NOTE.length());
            return mapGenres.isGenre(id) ? id : null;
        }
        List<String> artist = mapGenres.genresOfArtist(slot.getArtistName());
        if (!artist.isEmpty()) {
            return artist.get(0);
        }
        List<String> written = mapGenres.genresIn(note);
        if (!written.isEmpty()) {
            return written.get(0);
        }
        // "Main Stage (Forest)": the stage's genre picked in our event form, else a genre word in the stage's name
        Optional<String> picked = mapGenres.genreInBrackets(slot.getStage());
        if (picked.isPresent()) {
            return picked.get();
        }
        List<String> stage = mapGenres.genresIn(slot.getStage());
        return stage.isEmpty() ? null : stage.get(0);
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
                .toList(), slots.stream().map(TimetableParser.Slot::note).toList(), offset, TimetableSlot.AUTO);
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
        // A genre the admin picked for a set is kept with it; without one the set's genre is worked out as usual
        List<String> notes = sets.stream()
                .map(set -> set.genreId() != null && mapGenres.isGenre(set.genreId()) ? GENRE_NOTE + set.genreId() : null)
                .toList();
        replace(eventId, sets, notes, before.utcOffsetMinutes() != null ? before.utcOffsetMinutes() : venueOffset(eventId),
                TimetableSlot.MANUAL);
        log(eventId, before, sets.size() + " sets, set by hand", adminUsername);
        writeLineUp(eventId, sets);
        return get(eventId);
    }

    /**
     * An event added on the site keeps its line-up (the stage cards of its form, "Line-up · crew" on its page) in step
     * with the timetable an admin set: the line-up is written again from the sets, in the stage cards' format
     * (see stageText.js): "Main Stage (Forest):" then "Sat 23:00-02:00 Artist (live)". A Goabase event's line-up is
     * Goabase's and stays.
     */
    private void writeLineUp(long eventId, List<TimetableDto.Set> sets) {
        if (eventId < OurEvent.ID_BASE || sets.isEmpty()) {
            return;
        }
        ourEventRepository.findById(eventId - OurEvent.ID_BASE).ifPresent(event -> {
            event.setLineUp(lineUpOf(sets, event.getLineUp()));
            ourEventRepository.save(event);
            eventService.refreshSoon(eventId);
        });
    }

    static String lineUpOf(List<TimetableDto.Set> sets, String before) {
        Map<String, List<TimetableDto.Set>> byStage = new LinkedHashMap<>();
        for (TimetableDto.Set set : sets) {
            byStage.computeIfAbsent(set.stage() == null ? "" : set.stage().strip(), s -> new ArrayList<>()).add(set);
        }
        boolean headers = byStage.size() > 1 || byStage.keySet().stream().anyMatch(s -> !s.isEmpty());
        List<String> blocks = new ArrayList<>();
        for (Map.Entry<String, List<TimetableDto.Set>> stage : byStage.entrySet()) {
            List<String> lines = new ArrayList<>();
            if (headers) {
                lines.add((stage.getKey().isEmpty() ? "Stage" : stage.getKey().replace(":", " ")) + ":");
            }
            stage.getValue().stream().sorted(Comparator.comparing(TimetableDto.Set::startsAt)).forEach(set -> {
                String artist = set.artist().strip().replaceAll("[\\r\\n:]", " ");
                // A set written as live before stays live
                boolean live = artist.toLowerCase(Locale.ROOT).endsWith("(live)") || wasLive(before, artist);
                String name = live && !artist.toLowerCase(Locale.ROOT).endsWith("(live)") ? artist + " (live)" : artist;
                lines.add(day(set.startsAt()) + " " + time(set.startsAt())
                        + (set.endsAt() == null ? "" : "-" + time(set.endsAt())) + " " + name);
            });
            blocks.add(String.join("\n", lines));
        }
        return String.join("\n\n", blocks);
    }

    private static boolean wasLive(String before, String artist) {
        return before != null && Pattern.compile("(?im)" + Pattern.quote(artist) + "\\s*\\(live\\)\\s*$").matcher(before).find();
    }

    private static String day(LocalDateTime at) {
        return at.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
    }

    private static String time(LocalDateTime at) {
        return String.format("%02d:%02d", at.getHour(), at.getMinute());
    }

    /** Back to the timetable in the line-up: read again within a few minutes */
    @Transactional
    public TimetableDto handBack(long eventId, String adminUsername) {
        TimetableDto before = get(eventId);
        if (!before.manual()) {
            return before;
        }
        slotRepository.deleteByEventId(eventId);
        lineupScanRepository.rereadSoon(List.of(eventId));
        log(eventId, before, "from the line-up", adminUsername);
        return get(eventId);
    }

    // The venue clock's UTC offset, from the start time Goabase gives ("2026-10-24T22:00:00+02:00"); null when unknown
    private Integer venueOffset(long eventId) {
        try {
            return parties.fetchParty(eventId)
                    .map(party -> OffsetDateTime.parse(party.dateStart()).getOffset().getTotalSeconds() / 60)
                    .orElse(null);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private void replace(long eventId, List<TimetableDto.Set> sets, List<String> notes, Integer utcOffsetMinutes, String source) {
        slotRepository.deleteByEventId(eventId);
        slotRepository.flush();
        List<TimetableSlot> rows = new ArrayList<>();
        for (int i = 0; i < sets.size(); i++) {
            TimetableDto.Set set = sets.get(i);
            String stage = set.stage() == null || set.stage().isBlank() ? null : cut(set.stage().strip());
            rows.add(new TimetableSlot(eventId, i, stage, cut(set.artist().strip()), set.startsAt(), set.endsAt(),
                    utcOffsetMinutes, notes.get(i) == null ? null : cut200(notes.get(i)), source));
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

    private static String cut200(String value) {
        return value.length() > 200 ? value.substring(0, 200) : value;
    }

    private static String cut(String value) {
        return value.length() > 100 ? value.substring(0, 100) : value;
    }
}
