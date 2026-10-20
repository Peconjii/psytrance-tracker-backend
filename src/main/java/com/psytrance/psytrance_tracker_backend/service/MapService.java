package com.psytrance.psytrance_tracker_backend.service;

import com.psytrance.psytrance_tracker_backend.dto.EventDto;
import com.psytrance.psytrance_tracker_backend.dto.GenreShare;
import com.psytrance.psytrance_tracker_backend.dto.MapEventDto;
import com.psytrance.psytrance_tracker_backend.dto.MapPeriod;
import com.psytrance.psytrance_tracker_backend.model.EventGenre;
import com.psytrance.psytrance_tracker_backend.model.LineupScan;
import com.psytrance.psytrance_tracker_backend.repository.EventGenreRepository;
import com.psytrance.psytrance_tracker_backend.repository.LineupScanRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Events for the galaxy map (Map of Psy), with the genres worked out by {@link EventGenres}. */
@Service
public class MapService {

    private final EventService eventService;
    private final EventGenreRepository eventGenreRepository;
    private final LineupScanRepository lineupScanRepository;
    private final Clock clock;

    public MapService(EventService eventService,
                      EventGenreRepository eventGenreRepository,
                      LineupScanRepository lineupScanRepository,
                      Clock clock) {
        this.eventService = eventService;
        this.eventGenreRepository = eventGenreRepository;
        this.lineupScanRepository = lineupScanRepository;
        this.clock = clock;
    }

    /** Events running at some point from today until the end of the period, soonest first. */
    public List<MapEventDto> findEvents(MapPeriod period) {
        LocalDate lastDay = LocalDate.now(clock).plusDays(period.days() - 1L);
        List<EventDto> events = eventService.findUpcoming().stream()
                .filter(event -> event.dateStart() != null && !event.dateStart().isAfter(lastDay))
                .toList();
        if (events.isEmpty()) {
            return List.of();
        }
        List<Long> ids = events.stream().map(EventDto::id).toList();
        Map<Long, List<GenreShare>> genres = findGenres(ids);
        Set<Long> read = lineupScanRepository.findAllById(ids).stream()
                .map(LineupScan::getEventId)
                .collect(Collectors.toSet());
        return events.stream()
                .map(event -> MapEventDto.from(event, read.contains(event.id()), genres.getOrDefault(event.id(), List.of())))
                .toList();
    }

    /**
     * The genres of each event, biggest share first, for a page of the event list. Events with no
     * known genre (or a line-up not read yet) are left out of the map.
     */
    public Map<Long, List<GenreShare>> findGenres(Collection<Long> eventIds) {
        if (eventIds.isEmpty()) {
            return Map.of();
        }
        return eventGenreRepository.findByEventIdIn(eventIds).stream()
                .collect(Collectors.groupingBy(EventGenre::getEventId, Collectors.mapping(EventGenre::toShare,
                        Collectors.collectingAndThen(Collectors.toList(), list -> list.stream()
                                .sorted(Comparator.comparingDouble(GenreShare::share).reversed())
                                .toList()))));
    }

    /** Replaces the stored genres of an event (line-ups change as organizers announce more artists). */
    @Transactional
    public void saveGenres(long eventId, List<GenreShare> genres) {
        eventGenreRepository.deleteByEventId(eventId);
        // The delete must reach the database before the inserts, or the unique (event, genre) constraint fails
        eventGenreRepository.flush();
        eventGenreRepository.saveAll(genres.stream().map(genre -> new EventGenre(eventId, genre)).toList());
    }
}
