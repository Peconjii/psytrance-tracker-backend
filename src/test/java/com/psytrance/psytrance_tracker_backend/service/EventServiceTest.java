package com.psytrance.psytrance_tracker_backend.service;

import com.psytrance.psytrance_tracker_backend.client.GoabaseClient;
import com.psytrance.psytrance_tracker_backend.client.GoabaseParty;
import com.psytrance.psytrance_tracker_backend.dto.EventDto;
import com.psytrance.psytrance_tracker_backend.dto.EventSearch;
import com.psytrance.psytrance_tracker_backend.dto.EventTimeline;
import com.psytrance.psytrance_tracker_backend.dto.PageResponse;
import com.psytrance.psytrance_tracker_backend.exception.EventNotFoundException;
import com.psytrance.psytrance_tracker_backend.exception.GoabaseUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EventServiceTest {

    // Wednesday 2026-09-23, noon UTC
    private static final Instant WEDNESDAY = Instant.parse("2026-09-23T12:00:00Z");
    private static final EventSearch NO_FILTERS = new EventSearch(null, null, null, EventTimeline.ALL);

    private GoabaseClient goabaseClient;
    private MutableClock clock;
    private EventService eventService;

    @BeforeEach
    void setUp() {
        goabaseClient = mock(GoabaseClient.class);
        clock = new MutableClock(WEDNESDAY);
        eventService = new EventService(goabaseClient, clock, Duration.ofMinutes(10));
    }

    @Test
    void returnsRequestedPageAndTellsWhetherMoreExist() {
        List<GoabaseParty> thirtyParties = IntStream.rangeClosed(1, 30)
                .mapToObj(i -> party(i, "Party " + i, "2026-10-%02d".formatted(i), null))
                .toList();
        when(goabaseClient.fetchAllParties()).thenReturn(thirtyParties);

        PageResponse<EventDto> first = eventService.findEvents(NO_FILTERS, 0, 24);
        PageResponse<EventDto> second = eventService.findEvents(NO_FILTERS, 1, 24);

        assertThat(first.content()).hasSize(24);
        assertThat(first.hasNext()).isTrue();
        assertThat(first.totalElements()).isEqualTo(30);
        assertThat(first.totalPages()).isEqualTo(2);
        assertThat(second.content()).hasSize(6);
        assertThat(second.hasNext()).isFalse();
    }

    @Test
    void pagePastTheEndIsEmptyInsteadOfAnError() {
        when(goabaseClient.fetchAllParties()).thenReturn(List.of(party(1, "Only", "2026-10-01", null)));

        PageResponse<EventDto> page = eventService.findEvents(NO_FILTERS, 5, 24);

        assertThat(page.content()).isEmpty();
        assertThat(page.hasNext()).isFalse();
    }

    @Test
    void sortsEventsByStartDate() {
        when(goabaseClient.fetchAllParties()).thenReturn(List.of(
                party(1, "Later", "2026-12-01", null),
                party(2, "Sooner", "2026-10-01", null)));

        List<EventDto> events = eventService.findEvents(NO_FILTERS, 0, 24).content();

        assertThat(events).extracting(EventDto::nameParty).containsExactly("Sooner", "Later");
    }

    @Test
    void searchMatchesNameOrTownIgnoringCase() {
        when(goabaseClient.fetchAllParties()).thenReturn(List.of(
                party(1, "Boom Festival", "2026-10-01", null),
                partyInTown(2, "Forest Gathering", "Boomtown"),
                party(3, "Ozora", "2026-10-01", null)));

        List<EventDto> events = eventService.findEvents(new EventSearch("BOOM", null, null, EventTimeline.ALL), 0, 24).content();

        assertThat(events).extracting(EventDto::id).containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    void upcomingIncludesEventsThatAreHappeningRightNow() {
        when(goabaseClient.fetchAllParties()).thenReturn(List.of(
                party(1, "Ended last week", "2026-09-10", "2026-09-13"),
                party(2, "Started Monday, ends Sunday", "2026-09-21", "2026-09-27"),
                party(3, "Next month", "2026-10-10", null)));

        List<EventDto> upcoming = eventService.findEvents(new EventSearch(null, null, null, EventTimeline.UPCOMING), 0, 24).content();
        List<EventDto> past = eventService.findEvents(new EventSearch(null, null, null, EventTimeline.PAST), 0, 24).content();

        assertThat(upcoming).extracting(EventDto::id).containsExactly(2L, 3L);
        assertThat(past).extracting(EventDto::id).containsExactly(1L);
    }

    @Test
    void thisWeekendOnASaturdayMeansTheCurrentWeekendNotNextOne() {
        clock.setInstant(Instant.parse("2026-09-26T12:00:00Z")); // Saturday
        when(goabaseClient.fetchAllParties()).thenReturn(List.of(
                party(1, "This weekend", "2026-09-25", "2026-09-27"),
                party(2, "Next weekend", "2026-10-02", "2026-10-04")));

        List<EventDto> events = eventService.findEvents(new EventSearch(null, null, null, EventTimeline.THIS_WEEKEND), 0, 24).content();

        assertThat(events).extracting(EventDto::id).containsExactly(1L);
    }

    @Test
    void asksGoabaseOnlyOnceWhileCacheIsFresh() {
        when(goabaseClient.fetchAllParties()).thenReturn(List.of(party(1, "Party", "2026-10-01", null)));

        eventService.findEvents(NO_FILTERS, 0, 24);
        clock.advance(Duration.ofMinutes(9));
        eventService.findEvents(NO_FILTERS, 0, 24);

        verify(goabaseClient, times(1)).fetchAllParties();
    }

    @Test
    void refreshesAfterCacheExpires() {
        when(goabaseClient.fetchAllParties()).thenReturn(List.of(party(1, "Party", "2026-10-01", null)));

        eventService.findEvents(NO_FILTERS, 0, 24);
        clock.advance(Duration.ofMinutes(11));
        eventService.findEvents(NO_FILTERS, 0, 24);

        verify(goabaseClient, times(2)).fetchAllParties();
    }

    @Test
    void servesOldEventsWhenGoabaseIsDownDuringRefresh() {
        when(goabaseClient.fetchAllParties())
                .thenReturn(List.of(party(1, "Cached party", "2026-10-01", null)))
                .thenThrow(new GoabaseUnavailableException("down", null));

        eventService.findEvents(NO_FILTERS, 0, 24);
        clock.advance(Duration.ofMinutes(11));
        List<EventDto> events = eventService.findEvents(NO_FILTERS, 0, 24).content();

        assertThat(events).extracting(EventDto::nameParty).containsExactly("Cached party");
    }

    @Test
    void failsWhenGoabaseIsDownAndNothingIsCached() {
        when(goabaseClient.fetchAllParties()).thenThrow(new GoabaseUnavailableException("down", null));

        assertThatThrownBy(() -> eventService.findEvents(NO_FILTERS, 0, 24))
                .isInstanceOf(GoabaseUnavailableException.class);
    }

    @Test
    void mapOnlyGetsEventsWithCoordinates() {
        when(goabaseClient.fetchAllParties()).thenReturn(List.of(
                party(1, "Has coordinates", "2026-10-01", null),
                new GoabaseParty(2L, "No coordinates", "2026-10-01T22:00:00+02:00", null,
                        "Festival", "Scheduled", "Serbia", "Novi Sad", null, null, null, null)));

        assertThat(eventService.findEventsWithCoordinates()).extracting(EventDto::id).containsExactly(1L);
    }

    @Test
    void findByIdLooksUpGoabaseDirectlyWhenEventIsNotInTheList() {
        when(goabaseClient.fetchAllParties()).thenReturn(List.of());
        when(goabaseClient.fetchParty(99)).thenReturn(Optional.of(party(99, "Old favorite", "2025-08-01", null)));

        assertThat(eventService.findById(99).nameParty()).isEqualTo("Old favorite");
    }

    @Test
    void findByIdThrowsWhenEventDoesNotExist() {
        when(goabaseClient.fetchAllParties()).thenReturn(List.of());
        when(goabaseClient.fetchParty(404)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> eventService.findById(404)).isInstanceOf(EventNotFoundException.class);
    }

    private static GoabaseParty party(long id, String name, String startDate, String endDate) {
        return new GoabaseParty(id, name, startDate + "T22:00:00+02:00",
                endDate == null ? null : endDate + "T12:00:00+02:00",
                "Festival", "Scheduled", "Serbia", "Novi Sad", 45.25, 19.84, null, null);
    }

    private static GoabaseParty partyInTown(long id, String name, String town) {
        return new GoabaseParty(id, name, "2026-10-01T22:00:00+02:00", null,
                "Festival", "Scheduled", "Serbia", town, 45.25, 19.84, null, null);
    }

    /** A clock the test can move forward, to check what happens when the cache gets old. */
    private static class MutableClock extends Clock {
        private Instant instant;

        MutableClock(Instant instant) {
            this.instant = instant;
        }

        void setInstant(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public Instant instant() {
            return instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }
}
