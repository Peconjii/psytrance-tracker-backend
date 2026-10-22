package com.psytrance.psytrance_tracker_backend.service;

import com.psytrance.psytrance_tracker_backend.dto.ArtistDto;
import com.psytrance.psytrance_tracker_backend.dto.ArtistProfileDto;
import com.psytrance.psytrance_tracker_backend.dto.ArtistSummary;
import com.psytrance.psytrance_tracker_backend.dto.EventDto;
import com.psytrance.psytrance_tracker_backend.dto.PastGig;
import com.psytrance.psytrance_tracker_backend.model.Artist;
import com.psytrance.psytrance_tracker_backend.model.ArtistAlias;
import com.psytrance.psytrance_tracker_backend.model.ArtistBlock;
import com.psytrance.psytrance_tracker_backend.model.ArtistEvent;
import com.psytrance.psytrance_tracker_backend.model.EventSummary;
import com.psytrance.psytrance_tracker_backend.model.LineupScan;
import com.psytrance.psytrance_tracker_backend.repository.ArtistAliasRepository;
import com.psytrance.psytrance_tracker_backend.repository.ArtistBlockRepository;
import com.psytrance.psytrance_tracker_backend.repository.ArtistEventRepository;
import com.psytrance.psytrance_tracker_backend.repository.ArtistFollowRepository;
import com.psytrance.psytrance_tracker_backend.repository.ArtistProfileRepository;
import com.psytrance.psytrance_tracker_backend.repository.EventSummaryRepository;
import com.psytrance.psytrance_tracker_backend.repository.ArtistRepository;
import com.psytrance.psytrance_tracker_backend.repository.LineupScanRepository;
import com.psytrance.psytrance_tracker_backend.util.ArtistNames;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
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
    /** Past gigs listed on an artist's page, latest first */
    static final int MAX_PAST_GIGS = 50;

    private final ArtistRepository artistRepository;
    private final ArtistEventRepository artistEventRepository;
    private final LineupScanRepository lineupScanRepository;
    private final ArtistBlockRepository artistBlockRepository;
    private final ArtistAliasRepository artistAliasRepository;
    private final EventService eventService;
    private final ArtistProfileRepository profileRepository;
    private final EventSummaryRepository summaryRepository;
    private final ArtistFollowRepository followRepository;
    private final Clock clock;

    public ArtistService(ArtistRepository artistRepository,
                         ArtistEventRepository artistEventRepository,
                         LineupScanRepository lineupScanRepository,
                         ArtistBlockRepository artistBlockRepository,
                         ArtistAliasRepository artistAliasRepository,
                         EventService eventService,
                         ArtistProfileRepository profileRepository,
                         EventSummaryRepository summaryRepository,
                         ArtistFollowRepository followRepository,
                         Clock clock) {
        this.artistRepository = artistRepository;
        this.artistEventRepository = artistEventRepository;
        this.lineupScanRepository = lineupScanRepository;
        this.artistBlockRepository = artistBlockRepository;
        this.artistAliasRepository = artistAliasRepository;
        this.eventService = eventService;
        this.profileRepository = profileRepository;
        this.summaryRepository = summaryRepository;
        this.followRepository = followRepository;
        this.clock = clock;
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

    /**
     * The artist's page: their upcoming events (soonest first), profile, past gigs (latest first) and follower count.
     * Past gigs are the events whose line-up had them and whose summary was kept (see {@link #saveSummary}).
     */
    public ArtistDto findBySlug(String slug) {
        Artist artist = artistRepository.findBySlug(slug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Artist not found"));
        Set<Long> eventIds = new HashSet<>(artistEventRepository.findEventIdsByArtistId(artist.getId()));
        List<EventDto> upcoming = eventService.findUpcoming().stream()
                .filter(event -> eventIds.contains(event.id()))
                .toList();
        LocalDate today = LocalDate.now(clock);
        List<PastGig> past = summaryRepository.findAllById(eventIds).stream()
                .filter(summary -> summary.getLastDay() != null && summary.getLastDay().isBefore(today))
                .sorted(Comparator.comparing(EventSummary::getDateStart, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(MAX_PAST_GIGS)
                .map(summary -> new PastGig(summary.getEventId(), summary.getName(), summary.getDateStart(),
                        summary.getTown(), summary.getCountry()))
                .toList();
        ArtistProfileDto profile = profileRepository.findById(artist.getSlug()).map(ArtistProfileDto::from).orElse(null);
        return new ArtistDto(artist.getSlug(), artist.getName(), upcoming, profile, past,
                followRepository.countByArtistSlug(artist.getSlug()));
    }

    /** Keeps the event's name, dates and place, so it can be listed as a past gig once Goabase drops it */
    public void saveSummary(EventDto event) {
        summaryRepository.save(summaryOf(event));
    }

    /** Keeps a summary of each of these events that has none yet (one query, then only the new ones are written) */
    public void saveMissingSummaries(List<EventDto> events) {
        Set<Long> known = summaryRepository.findAllById(events.stream().map(EventDto::id).toList()).stream()
                .map(EventSummary::getEventId)
                .collect(Collectors.toSet());
        summaryRepository.saveAll(events.stream()
                .filter(event -> known.add(event.id()))
                .map(ArtistService::summaryOf)
                .toList());
    }

    private static EventSummary summaryOf(EventDto event) {
        return new EventSummary(event.id(), event.nameParty(), event.dateStart(), event.dateEnd(),
                event.nameTown(), event.nameCountry());
    }

    /**
     * Stores who plays at an event, replacing what was stored before (line-ups change as
     * organizers announce more artists). New artists are created; known ones are matched by slug.
     */
    @Transactional
    public void saveLineUp(long eventId, List<String> artistNames, Instant scannedAt) {
        artistEventRepository.deleteByEventId(eventId);

        // Organizers sometimes list an artist twice (two sets, two stages): keep one, best spelling.
        // Words admins marked as "not an artist" are skipped, and merged spellings count for their artist.
        Set<String> blocked = artistBlockRepository.findAllById(artistNames.stream().map(ArtistNames::slug).toList()).stream()
                .map(ArtistBlock::getSlug).collect(Collectors.toSet());
        Map<String, Long> aliases = artistAliasRepository.findAllById(artistNames.stream().map(ArtistNames::slug).toList()).stream()
                .collect(Collectors.toMap(ArtistAlias::getAliasSlug, ArtistAlias::getArtistId));
        Map<Long, Artist> aliasTargets = artistRepository.findAllById(aliases.values()).stream()
                .collect(Collectors.toMap(Artist::getId, Function.identity()));
        Map<String, String> namesBySlug = new LinkedHashMap<>();
        for (String name : artistNames) {
            String slug = ArtistNames.slug(name);
            if (slug.isEmpty() || blocked.contains(slug)) {
                continue;
            }
            Artist target = aliases.containsKey(slug) ? aliasTargets.get(aliases.get(slug)) : null;
            if (target != null) {
                namesBySlug.putIfAbsent(target.getSlug(), target.getName());
            } else {
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
