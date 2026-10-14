package com.psytrance.psytrance_tracker_backend.service;

import com.psytrance.psytrance_tracker_backend.client.GoabaseClient;
import com.psytrance.psytrance_tracker_backend.client.GoabasePartyDetails;
import com.psytrance.psytrance_tracker_backend.dto.EventDto;
import com.psytrance.psytrance_tracker_backend.dto.LineUpLine;
import com.psytrance.psytrance_tracker_backend.exception.GoabaseUnavailableException;
import com.psytrance.psytrance_tracker_backend.model.LineupScan;
import com.psytrance.psytrance_tracker_backend.repository.LineupScanRepository;
import com.psytrance.psytrance_tracker_backend.util.LineUpParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Builds the artist index. Goabase only has line-ups on its single-event endpoint, so finding
 * where an artist plays means reading every event's line-up. Doing that on request would be
 * hundreds of Goabase calls, so this reads a small batch every few minutes in the background
 * (see BackgroundJobs) and stores the result. Events never read come first, then the ones read
 * longest ago, because organizers keep adding artists until the event starts.
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
    private final Clock clock;

    public ArtistIndexer(EventService eventService,
                         GoabaseClient goabaseClient,
                         LineupScanRepository lineupScanRepository,
                         ArtistService artistService,
                         Clock clock) {
        this.eventService = eventService;
        this.goabaseClient = goabaseClient;
        this.lineupScanRepository = lineupScanRepository;
        this.artistService = artistService;
        this.clock = clock;
    }

    /** Reads the next batch of line-ups. Returns how many events were read. */
    public int indexNextBatch() {
        Instant now = clock.instant();
        List<Long> upcomingIds = eventService.findUpcoming().stream().map(EventDto::id).toList();
        Map<Long, Instant> lastRead = lineupScanRepository.findAllById(upcomingIds).stream()
                .collect(Collectors.toMap(LineupScan::getEventId, LineupScan::getScannedAt));

        // Stable sort: events never read keep their date order, so the soonest are read first
        List<Long> due = upcomingIds.stream()
                .filter(id -> !lastRead.containsKey(id) || lastRead.get(id).plus(REREAD_AFTER).isBefore(now))
                .sorted(Comparator.comparing((Long id) -> lastRead.getOrDefault(id, Instant.MIN)))
                .limit(BATCH_SIZE)
                .toList();

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
            List<String> artists = party
                    .map(details -> LineUpParser.parse(details.textLineUp()).stream()
                            .map(LineUpLine::artist)
                            .filter(Objects::nonNull)
                            .toList())
                    .orElse(List.of());
            artistService.saveLineUp(eventId, artists, now);
            read++;
        }
        return read;
    }
}
