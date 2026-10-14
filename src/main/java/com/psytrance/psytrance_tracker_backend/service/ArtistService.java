package com.psytrance.psytrance_tracker_backend.service;

import com.psytrance.psytrance_tracker_backend.dto.ArtistDto;
import com.psytrance.psytrance_tracker_backend.dto.ArtistSummary;
import com.psytrance.psytrance_tracker_backend.dto.EventDto;
import com.psytrance.psytrance_tracker_backend.model.Artist;
import com.psytrance.psytrance_tracker_backend.model.ArtistEvent;
import com.psytrance.psytrance_tracker_backend.model.LineupScan;
import com.psytrance.psytrance_tracker_backend.repository.ArtistEventRepository;
import com.psytrance.psytrance_tracker_backend.repository.ArtistRepository;
import com.psytrance.psytrance_tracker_backend.repository.LineupScanRepository;
import com.psytrance.psytrance_tracker_backend.util.ArtistNames;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Artist pages, built from the line-ups that {@link ArtistIndexer} reads from Goabase. */
@Service
public class ArtistService {

    private static final int MAX_RESULTS = 50;

    private final ArtistRepository artistRepository;
    private final ArtistEventRepository artistEventRepository;
    private final LineupScanRepository lineupScanRepository;
    private final EventService eventService;

    public ArtistService(ArtistRepository artistRepository,
                         ArtistEventRepository artistEventRepository,
                         LineupScanRepository lineupScanRepository,
                         EventService eventService) {
        this.artistRepository = artistRepository;
        this.artistEventRepository = artistEventRepository;
        this.lineupScanRepository = lineupScanRepository;
        this.eventService = eventService;
    }

    /**
     * Artists with upcoming events, busiest first. The search ignores case, accents and symbols,
     * so "ace vent" finds "ACE VENTURA" and "element" finds "Ëlement".
     */
    public List<ArtistSummary> findArtists(String search) {
        List<Long> upcomingIds = eventService.findUpcoming().stream().map(EventDto::id).toList();
        if (upcomingIds.isEmpty()) {
            return List.of();
        }
        return artistRepository.findPlayingAt(upcomingIds, ArtistNames.slug(search), PageRequest.of(0, MAX_RESULTS));
    }

    /** The artist and their upcoming events, soonest first. Events that already ended aren't listed. */
    public ArtistDto findBySlug(String slug) {
        Artist artist = artistRepository.findBySlug(slug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Artist not found"));
        Set<Long> eventIds = new HashSet<>(artistEventRepository.findEventIdsByArtistId(artist.getId()));
        List<EventDto> upcoming = eventService.findUpcoming().stream()
                .filter(event -> eventIds.contains(event.id()))
                .toList();
        return new ArtistDto(artist.getSlug(), artist.getName(), upcoming);
    }

    /**
     * Stores who plays at an event, replacing what was stored before (line-ups change as
     * organizers announce more artists). New artists are created; known ones are matched by slug.
     */
    @Transactional
    public void saveLineUp(long eventId, List<String> artistNames, Instant scannedAt) {
        artistEventRepository.deleteByEventId(eventId);

        // Organizers sometimes list an artist twice (two sets, two stages): keep one, best spelling
        Map<String, String> namesBySlug = new LinkedHashMap<>();
        for (String name : artistNames) {
            String slug = ArtistNames.slug(name);
            if (!slug.isEmpty()) {
                namesBySlug.merge(slug, name, ArtistNames::preferredName);
            }
        }

        if (!namesBySlug.isEmpty()) {
            Map<String, Artist> known = artistRepository.findBySlugIn(namesBySlug.keySet()).stream()
                    .collect(Collectors.toMap(Artist::getSlug, Function.identity()));
            namesBySlug.forEach((slug, name) -> {
                Artist artist = known.get(slug);
                if (artist == null) {
                    artist = artistRepository.save(new Artist(slug, name));
                } else {
                    // Saved at the end of the transaction, because the entity is managed
                    artist.setName(ArtistNames.preferredName(artist.getName(), name));
                }
                artistEventRepository.save(new ArtistEvent(artist, eventId));
            });
        }

        lineupScanRepository.save(new LineupScan(eventId, scannedAt));
    }
}
