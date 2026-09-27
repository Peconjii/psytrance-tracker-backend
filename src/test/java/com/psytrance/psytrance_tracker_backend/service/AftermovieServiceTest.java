package com.psytrance.psytrance_tracker_backend.service;

import com.psytrance.psytrance_tracker_backend.client.YouTubeClient;
import com.psytrance.psytrance_tracker_backend.client.YouTubeClient.YouTubeVideo;
import com.psytrance.psytrance_tracker_backend.dto.AftermovieDto;
import com.psytrance.psytrance_tracker_backend.dto.EventDetailsDto;
import com.psytrance.psytrance_tracker_backend.exception.YouTubeUnavailableException;
import com.psytrance.psytrance_tracker_backend.model.AftermovieLookup;
import com.psytrance.psytrance_tracker_backend.repository.AftermovieLookupRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AftermovieServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-23T12:00:00Z");

    private EventService eventService;
    private YouTubeClient youTubeClient;
    private AftermovieLookupRepository lookups;
    private AftermovieService aftermovieService;

    @BeforeEach
    void setUp() {
        eventService = mock(EventService.class);
        youTubeClient = mock(YouTubeClient.class);
        lookups = mock(AftermovieLookupRepository.class);
        when(youTubeClient.isEnabled()).thenReturn(true);
        when(lookups.findById(anyString())).thenReturn(Optional.empty());
        aftermovieService = new AftermovieService(eventService, youTubeClient, lookups, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @ParameterizedTest(name = "\"{0}\" -> \"{1}\"")
    @CsvSource(delimiter = '|', value = {
            "Transition Experience 2026 - OA Psytrance Festival | Transition Experience",
            "'Universo Paralello 2026 | Espaco Nativos Glamping' | Universo Paralello",
            "Green Gathering–XIV 14th Edition – Gathering of Tribes | Green Gathering",
            "PARADIGMA FESTIVAL                                  | PARADIGMA FESTIVAL",
    })
    void festivalNameDropsYearAndSubtitle(String nameParty, String expected) {
        assertThat(AftermovieService.festivalName(nameParty)).isEqualTo(expected);
    }

    @Test
    void onlyTheFestivalsOwnAftermovieCounts() {
        List<String> words = AftermovieService.significantWords("Universo Paralello");

        assertThat(AftermovieService.isAftermovieOf("Universo Parallelo 2024 - Official Aftermovie", words)).isFalse();
        assertThat(AftermovieService.isAftermovieOf("UNIVERSO PARALELLO 2025 | After Movie", words)).isTrue();
        assertThat(AftermovieService.isAftermovieOf("Universo Paralello - Full Set Ace Ventura", words)).isFalse();
    }

    @Test
    void picksTheFirstMatchingVideoAndStoresIt() {
        when(eventService.findDetails(1)).thenReturn(event("Ozora Festival 2026", "Festival"));
        when(youTubeClient.searchVideos("Ozora Festival aftermovie")).thenReturn(List.of(
                new YouTubeVideo("aaa", "Top 10 festivals in Europe"),
                new YouTubeVideo("bbb", "O.Z.O.R.A. Festival 2025 aftermovie"),
                new YouTubeVideo("ccc", "Ozora Festival 2025 Official Aftermovie")));

        Optional<AftermovieDto> aftermovie = aftermovieService.findForEvent(1);

        assertThat(aftermovie).map(AftermovieDto::videoId).contains("ccc");
        verify(lookups).save(any(AftermovieLookup.class));
    }

    @Test
    void usesTheStoredResultWithoutAskingYouTube() {
        when(eventService.findDetails(1)).thenReturn(event("Ozora Festival 2026", "Festival"));
        when(lookups.findById("ozora")).thenReturn(Optional.of(
                new AftermovieLookup("ozora", "ccc", "Ozora Aftermovie", NOW.minus(Duration.ofDays(3)))));

        assertThat(aftermovieService.findForEvent(1)).map(AftermovieDto::videoId).contains("ccc");
        verify(youTubeClient, never()).searchVideos(anyString());
    }

    @Test
    void clubNightsGetNoAftermovie() {
        when(eventService.findDetails(1)).thenReturn(event("Friday Goa Night", "Club"));

        assertThat(aftermovieService.findForEvent(1)).isEmpty();
        verify(youTubeClient, never()).searchVideos(anyString());
    }

    @Test
    void youTubeFailureIsNotStoredSoTheNextVisitTriesAgain() {
        when(eventService.findDetails(1)).thenReturn(event("Ozora Festival 2026", "Festival"));
        when(youTubeClient.searchVideos(anyString())).thenThrow(new YouTubeUnavailableException("quota", null));

        assertThat(aftermovieService.findForEvent(1)).isEmpty();
        verify(lookups, never()).save(any());
    }

    private static EventDetailsDto event(String name, String type) {
        return new EventDetailsDto(1L, name, "Town", "Hungary", type, "Scheduled", null, null,
                null, null, null, null, null, List.of(), null, null, null, null, null);
    }
}
