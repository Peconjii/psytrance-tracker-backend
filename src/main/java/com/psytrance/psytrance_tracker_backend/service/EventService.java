package com.psytrance.psytrance_tracker_backend.service;

import com.psytrance.psytrance_tracker_backend.client.GoabaseClient;
import com.psytrance.psytrance_tracker_backend.dto.EventDto;
import com.psytrance.psytrance_tracker_backend.dto.EventSearch;
import com.psytrance.psytrance_tracker_backend.dto.PageResponse;
import com.psytrance.psytrance_tracker_backend.exception.EventNotFoundException;
import com.psytrance.psytrance_tracker_backend.exception.GoabaseUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Serves events to the API. Goabase is only asked for the full list once per
 * {@code goabase.cache-ttl}; searching, filtering and paging happen here in memory.
 */
@Service
public class EventService {

    private static final Logger log = LoggerFactory.getLogger(EventService.class);

    private static final Comparator<EventDto> BY_START_DATE = Comparator
            .comparing(EventDto::dateStart, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(EventDto::id);

    private final GoabaseClient goabaseClient;
    private final Clock clock;
    private final Duration cacheTtl;

    // volatile: a request thread must see the new list as soon as another thread stores it
    private volatile CachedEvents cache;

    public EventService(GoabaseClient goabaseClient,
                        Clock clock,
                        @Value("${goabase.cache-ttl}") Duration cacheTtl) {
        this.goabaseClient = goabaseClient;
        this.clock = clock;
        this.cacheTtl = cacheTtl;
    }

    public PageResponse<EventDto> findEvents(EventSearch search, int page, int size) {
        LocalDate today = LocalDate.now(clock);
        List<EventDto> matching = allEvents().stream()
                .filter(event -> matches(event, search, today))
                .toList();
        return PageResponse.of(matching, page, size);
    }

    public List<EventDto> findEventsWithCoordinates() {
        return allEvents().stream()
                .filter(EventDto::hasCoordinates)
                .toList();
    }

    public EventDto findById(long id) {
        // Favorites can point at events that already dropped off the Goabase list, so fall back to a direct lookup
        return allEvents().stream()
                .filter(event -> Long.valueOf(id).equals(event.id()))
                .findFirst()
                .or(() -> goabaseClient.fetchParty(id).map(EventDto::from))
                .orElseThrow(() -> new EventNotFoundException(id));
    }

    private List<EventDto> allEvents() {
        CachedEvents current = cache;
        if (current != null && current.isFresh(clock.instant(), cacheTtl)) {
            return current.events();
        }
        synchronized (this) {
            // Another request may have refreshed the cache while this one waited for the lock
            current = cache;
            if (current != null && current.isFresh(clock.instant(), cacheTtl)) {
                return current.events();
            }
            try {
                List<EventDto> events = goabaseClient.fetchAllParties().stream()
                        .map(EventDto::from)
                        .sorted(BY_START_DATE)
                        .toList();
                cache = new CachedEvents(events, clock.instant());
                return events;
            } catch (GoabaseUnavailableException e) {
                if (current == null) {
                    throw e;
                }
                // Slightly old events are better than an error page
                log.warn("Goabase refresh failed, serving cached events from {}", current.fetchedAt(), e);
                return current.events();
            }
        }
    }

    private static boolean matches(EventDto event, EventSearch search, LocalDate today) {
        return matchesSearch(event, search.search())
                && containsIgnoreCase(event.nameCountry(), search.country())
                && matchesGenre(event, search.genre())
                && matchesTimeline(event, search, today);
    }

    private static boolean matchesSearch(EventDto event, String term) {
        return isBlank(term)
                || containsIgnoreCase(event.nameParty(), term)
                || containsIgnoreCase(event.nameTown(), term);
    }

    // Goabase has no genre field, so a genre matches the event type or the party name
    private static boolean matchesGenre(EventDto event, String genre) {
        return isBlank(genre)
                || genre.equalsIgnoreCase("All")
                || containsIgnoreCase(event.nameType(), genre)
                || containsIgnoreCase(event.nameParty(), genre);
    }

    private static boolean matchesTimeline(EventDto event, EventSearch search, LocalDate today) {
        return switch (search.timeline()) {
            case ALL -> true;
            case UPCOMING -> event.lastDay() != null && !event.lastDay().isBefore(today);
            case PAST -> event.lastDay() != null && event.lastDay().isBefore(today);
            case THIS_WEEKEND -> overlapsWeekend(event, today);
        };
    }

    private static boolean overlapsWeekend(EventDto event, LocalDate today) {
        if (event.dateStart() == null) {
            return false;
        }
        // On Saturday or Sunday "this weekend" is the one we're in, not next week's
        boolean isWeekend = today.getDayOfWeek() == DayOfWeek.SATURDAY || today.getDayOfWeek() == DayOfWeek.SUNDAY;
        LocalDate friday = isWeekend
                ? today.with(TemporalAdjusters.previous(DayOfWeek.FRIDAY))
                : today.with(TemporalAdjusters.nextOrSame(DayOfWeek.FRIDAY));
        LocalDate sunday = friday.plusDays(2);
        return !event.dateStart().isAfter(sunday) && !event.lastDay().isBefore(friday);
    }

    private static boolean containsIgnoreCase(String value, String term) {
        if (isBlank(term)) {
            return true;
        }
        return value != null && value.toLowerCase(Locale.ROOT).contains(term.trim().toLowerCase(Locale.ROOT));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record CachedEvents(List<EventDto> events, Instant fetchedAt) {

        boolean isFresh(Instant now, Duration ttl) {
            return fetchedAt.plus(ttl).isAfter(now);
        }
    }
}
