package com.psytrance.psytrance_tracker_backend.service;

import com.psytrance.psytrance_tracker_backend.client.GoabaseClient;
import com.psytrance.psytrance_tracker_backend.client.GoabaseParty;
import com.psytrance.psytrance_tracker_backend.client.GoabasePartyDetails;
import com.psytrance.psytrance_tracker_backend.dto.EventDto;
import com.psytrance.psytrance_tracker_backend.dto.GenreShare;
import com.psytrance.psytrance_tracker_backend.dto.GenreShare.Source;
import com.psytrance.psytrance_tracker_backend.exception.GoabaseUnavailableException;
import com.psytrance.psytrance_tracker_backend.model.LineupScan;
import com.psytrance.psytrance_tracker_backend.repository.LineupScanRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ArtistIndexerTest {

    private static final Instant NOW = Instant.parse("2026-09-23T12:00:00Z");

    private EventService eventService;
    private GoabaseClient goabaseClient;
    private LineupScanRepository lineupScanRepository;
    private ArtistService artistService;
    private MapService mapService;
    private ArtistIndexer indexer;

    @BeforeEach
    void setUp() {
        eventService = mock(EventService.class);
        goabaseClient = mock(GoabaseClient.class);
        lineupScanRepository = mock(LineupScanRepository.class);
        artistService = mock(ArtistService.class);
        mapService = mock(MapService.class);
        // A tiny map: one forest star with one artist
        MapGenres mapGenres = new MapGenres(List.of(new MapGenres.Genre("forest", "Forest", "dark", List.of("Derango"))));
        indexer = new ArtistIndexer(eventService, goabaseClient, lineupScanRepository, artistService,
                new EventGenres(mapGenres), mapService, mock(EventTimetables.class), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void storesTheArtistsFoundInTheLineUp() {
        when(eventService.findUpcoming()).thenReturn(List.of(event(1)));
        when(goabaseClient.fetchParty(1)).thenReturn(Optional.of(withLineUp(1, "LINE UP:\r\nAce Ventura (live)\r\nTBA")));

        assertThat(indexer.indexNextBatch()).isEqualTo(1);

        // "(live)" next to the name: a live gig
        verify(artistService).saveLineUp(1, List.of("Ace Ventura"), Set.of("ace-ventura"), NOW);
    }

    @Test
    void eventsNeverReadComeFirstThenTheOldestReads() {
        when(eventService.findUpcoming()).thenReturn(List.of(event(1), event(2), event(3)));
        when(lineupScanRepository.findAllById(List.of(1L, 2L, 3L))).thenReturn(List.of(
                new LineupScan(1L, NOW.minus(Duration.ofDays(3))),
                // Read an hour ago: not again yet
                new LineupScan(2L, NOW.minus(Duration.ofHours(1)))));
        when(goabaseClient.fetchParty(anyLong())).thenReturn(Optional.empty());

        assertThat(indexer.indexNextBatch()).isEqualTo(2);

        InOrder order = inOrder(goabaseClient);
        order.verify(goabaseClient).fetchParty(3);
        order.verify(goabaseClient).fetchParty(1);
        verify(goabaseClient, never()).fetchParty(2);
    }

    @Test
    void lineUpsDueAtOnceAreReadSoonestEventFirst() {
        // Goabase lists events in its own order; all three were marked for re-reading at the same time
        when(eventService.findUpcoming()).thenReturn(List.of(event(1, "2026-12-01"), event(2, "2026-10-01"), event(3, "2026-11-01")));
        when(lineupScanRepository.findAllById(List.of(1L, 2L, 3L))).thenReturn(List.of(
                new LineupScan(1L, Instant.EPOCH), new LineupScan(2L, Instant.EPOCH), new LineupScan(3L, Instant.EPOCH)));
        when(goabaseClient.fetchParty(anyLong())).thenReturn(Optional.empty());

        indexer.indexNextBatch();

        InOrder order = inOrder(goabaseClient);
        order.verify(goabaseClient).fetchParty(2);
        order.verify(goabaseClient).fetchParty(3);
        order.verify(goabaseClient).fetchParty(1);
    }

    @Test
    void readsAtMostOneBatchPerRun() {
        when(eventService.findUpcoming()).thenReturn(LongStream.rangeClosed(1, 100).mapToObj(ArtistIndexerTest::event).toList());
        when(goabaseClient.fetchParty(anyLong())).thenReturn(Optional.empty());

        assertThat(indexer.indexNextBatch()).isEqualTo(ArtistIndexer.BATCH_SIZE);
        verify(goabaseClient, times(ArtistIndexer.BATCH_SIZE)).fetchParty(anyLong());
    }

    @Test
    void stopsWhenGoabaseIsDownAndKeepsWhatWasRead() {
        when(eventService.findUpcoming()).thenReturn(List.of(event(1), event(2), event(3)));
        when(goabaseClient.fetchParty(1)).thenReturn(Optional.of(withLineUp(1, "Astrix")));
        when(goabaseClient.fetchParty(2)).thenThrow(new GoabaseUnavailableException("down", null));

        assertThat(indexer.indexNextBatch()).isEqualTo(1);

        verify(artistService).saveLineUp(1, List.of("Astrix"), Set.of(), NOW);
        verify(artistService, never()).saveLineUp(eq(2L), any(), any(), any());
        verify(goabaseClient, never()).fetchParty(3);
    }

    @Test
    void storesTheEventsMapGenresWithTheLineUp() {
        when(eventService.findUpcoming()).thenReturn(List.of(event(1)));
        when(goabaseClient.fetchParty(1)).thenReturn(Optional.of(withLineUp(1, "Derango\r\nSomeone Unknown")));

        indexer.indexNextBatch();

        verify(mapService).saveGenres(1, List.of(new GenreShare("forest", 1.0, Source.ARTIST)));
        verify(artistService).saveLineUp(1, List.of("Derango", "Someone Unknown"), Set.of(), NOW);
    }

    @Test
    void usesGoabaseKeywordsWhenTheLineUpSaysNothing() {
        when(eventService.findUpcoming()).thenReturn(List.of(event(1)));
        when(goabaseClient.fetchParty(1)).thenReturn(Optional.of(withLineUp(1, "Someone Unknown")));
        when(goabaseClient.fetchKeywords(1)).thenReturn(List.of("Forest", "Psytrance"));

        indexer.indexNextBatch();

        verify(mapService).saveGenres(1, List.of(new GenreShare("forest", 1.0, Source.KEYWORD)));
    }

    @Test
    void keywordsThatCantBeReadDontStopTheLineUp() {
        when(eventService.findUpcoming()).thenReturn(List.of(event(1)));
        when(goabaseClient.fetchParty(1)).thenReturn(Optional.of(withLineUp(1, "Astrix")));
        when(goabaseClient.fetchKeywords(1)).thenThrow(new GoabaseUnavailableException("down", null));

        assertThat(indexer.indexNextBatch()).isEqualTo(1);

        verify(mapService).saveGenres(1, List.of());
        verify(artistService).saveLineUp(1, List.of("Astrix"), Set.of(), NOW);
    }

    private static EventDto event(long id) {
        return event(id, "2026-10-01");
    }

    private static EventDto event(long id, String day) {
        return EventDto.from(new GoabaseParty(id, "Event " + id, day + "T22:00:00+02:00", null,
                "Festival", "Scheduled", "Serbia", "Novi Sad", 45.25, 19.84, null, null));
    }

    private static GoabasePartyDetails withLineUp(long id, String lineUp) {
        return new GoabasePartyDetails(id, "Event " + id, "2026-10-01T22:00:00+02:00", null, "Festival",
                "Scheduled", "Serbia", "Novi Sad", 45.25, 19.84, null, null, null, lineUp, "", "", "", null, null);
    }
}
