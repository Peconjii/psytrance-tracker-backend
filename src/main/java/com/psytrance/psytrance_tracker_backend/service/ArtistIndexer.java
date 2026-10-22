package com.psytrance.psytrance_tracker_backend.service;

import com.psytrance.psytrance_tracker_backend.client.GoabaseClient;
import com.psytrance.psytrance_tracker_backend.client.GoabasePartyDetails;
import com.psytrance.psytrance_tracker_backend.dto.EventDto;
import com.psytrance.psytrance_tracker_backend.exception.GoabaseUnavailableException;
import com.psytrance.psytrance_tracker_backend.model.LineupScan;
import com.psytrance.psytrance_tracker_backend.repository.LineupScanRepository;
import com.psytrance.psytrance_tracker_backend.util.LineUpParser;
import com.psytrance.psytrance_tracker_backend.util.TimetableParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Builds the artist index. Goabase only has line-ups on its single-event endpoint, so finding
 * where an artist plays means reading every event's line-up. Doing that on request would be
 * hundreds of Goabase calls, so this reads a small batch every few minutes in the background
 * (see BackgroundJobs) and stores the result. Events never read come first, then the ones read
 * longest ago, because organizers keep adding artists until the event starts.
 * While reading a line-up it also works out the event's Map of Psy genres (see {@link EventGenres}).
 */
@Component
public class ArtistIndexer {

    private static final Logger log = LoggerFactory.getLogger(ArtistIndexer.class);

    // Small enough to stay polite to Goabase, big enough to read a few hundred events within an hour
    static final int BATCH_SIZE = 25;
    static final Duration REREAD_AFTER = Duration.ofDays(2);

    private final EventService eventService;
    private final GoabaseClient goabaseClient;
    private final LineupScanRepository lineupScanRepository;
    private final ArtistService artistService;
    private final EventGenres eventGenres;
    private final MapService mapService;
    private final EventTimetables eventTimetables;
    private final Organizers organizers;
    private final Clock clock;

    public ArtistIndexer(EventService eventService,
                         GoabaseClient goabaseClient,
                         LineupScanRepository lineupScanRepository,
                         ArtistService artistService,
                         EventGenres eventGenres,
                         MapService mapService,
                         EventTimetables eventTimetables,
                         Organizers organizers,
                         Clock clock) {
        this.eventService = eventService;
        this.goabaseClient = goabaseClient;
        this.lineupScanRepository = lineupScanRepository;
        this.artistService = artistService;
        this.eventGenres = eventGenres;
        this.mapService = mapService;
        this.eventTimetables = eventTimetables;
        this.organizers = organizers;
        this.clock = clock;
    }

    /** Reads the next batch of line-ups. Returns how many events were read. */
    public int indexNextBatch() {
        Instant now = clock.instant();
        List<EventDto> upcoming = eventService.findUpcoming();
        List<Long> upcomingIds = upcoming.stream().map(EventDto::id).toList();
        Map<Long, Instant> lastRead = lineupScanRepository.findAllById(upcomingIds).stream()
                .collect(Collectors.toMap(LineupScan::getEventId, LineupScan::getScannedAt));
        // Goabase doesn't list events by date, so the date is the tie-break: when many line-ups are due at once
        // (never read, or all marked for re-reading), the soonest events are read first
        Map<Long, LocalDate> startById = new HashMap<>();
        Map<Long, EventDto> byId = new HashMap<>();
        upcoming.forEach(event -> {
            startById.putIfAbsent(event.id(), event.dateStart());
            byId.putIfAbsent(event.id(), event);
        });

        List<Long> due = upcomingIds.stream()
                .filter(id -> !lastRead.containsKey(id) || lastRead.get(id).plus(REREAD_AFTER).isBefore(now))
                .sorted(Comparator.comparing((Long id) -> lastRead.getOrDefault(id, Instant.MIN))
                        .thenComparing(startById::get, Comparator.nullsLast(Comparator.naturalOrder())))
                .limit(BATCH_SIZE)
                .toList();

        // Every upcoming event is remembered by name, dates and place, so it stays listed as a past gig on the
        // pages of the artists who played it after Goabase drops it
        artistService.saveMissingSummaries(upcoming);

        int read = 0;
        for (Long eventId : due) {
            Optional<GoabasePartyDetails> party;
            try {
                party = goabaseClient.fetchParty(eventId);
            } catch (GoabaseUnavailableException e) {
                // No point asking about the rest of the batch now; the next run carries on
                log.warn("Goabase is unavailable, artist index stopped after {} events", read, e);
                break;
            }
            // An event Goabase no longer knows is stored with no artists, so it isn't asked about again soon
            List<LineUpParser.Section> lineUp = party
                    .map(details -> LineUpParser.sections(details.textLineUp()))
                    .orElse(List.of());
            List<String> keywords = party.isPresent() ? keywords(eventId) : List.of();
            mapService.saveGenres(eventId, eventGenres.compute(lineUp, keywords));
            party.ifPresent(details -> eventTimetables.saveParsed(eventId, timetable(details)));
            List<String> artists = lineUp.stream().flatMap(section -> section.artists().stream()).toList();
            // Saved last, because it also marks the event as read
            Set<String> live = party.map(details -> LineUpParser.liveArtistSlugs(details.textLineUp())).orElse(Set.of());
            artistService.saveLineUp(eventId, artists, live, now);
            // Name, dates, place and organizer as they are now (the organizer may have renamed or moved it)
            String organizer = party.map(details -> organizers.record(details.nameOrganizer(), details.urlOrganizer())).orElse(null);
            artistService.saveSummary(byId.get(eventId), organizer);
            read++;
        }
        return read;
    }

    // Set times written in the line-up, on the event's own clock (Goabase gives the start with its UTC offset)
    private static List<TimetableParser.Slot> timetable(GoabasePartyDetails details) {
        try {
            OffsetDateTime start = OffsetDateTime.parse(details.dateStart());
            return TimetableParser.parse(details.textLineUp(), start.toLocalDate(), start.getHour(), start.getOffset());
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    // Keywords only add to the genres, so the line-up is still stored when they can't be read
    private List<String> keywords(long eventId) {
        try {
            return goabaseClient.fetchKeywords(eventId);
        } catch (GoabaseUnavailableException e) {
            log.warn("Could not read the Goabase keywords of event {}", eventId, e);
            return List.of();
        }
    }
}
