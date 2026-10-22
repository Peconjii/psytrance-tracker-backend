package com.psytrance.psytrance_tracker_backend.service;

import com.psytrance.psytrance_tracker_backend.dto.EventDto;
import com.psytrance.psytrance_tracker_backend.dto.OrganizerDto;
import com.psytrance.psytrance_tracker_backend.dto.PastGig;
import com.psytrance.psytrance_tracker_backend.model.EventSummary;
import com.psytrance.psytrance_tracker_backend.model.Organizer;
import com.psytrance.psytrance_tracker_backend.repository.EventSummaryRepository;
import com.psytrance.psytrance_tracker_backend.repository.OrganizerRepository;
import com.psytrance.psytrance_tracker_backend.util.ArtistNames;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Organizers of events: who Goabase says runs each event (the ship's captain), kept when the artist index reads the
 * event, plus names admins moved out of the artists. Their page lists their upcoming and past events.
 */
@Service
public class Organizers {

    private static final int MAX_RESULTS = 50;
    private static final int MAX_PAST_EVENTS = 50;

    private final OrganizerRepository organizerRepository;
    private final EventSummaryRepository summaryRepository;
    private final EventService eventService;
    private final Clock clock;

    public Organizers(OrganizerRepository organizerRepository, EventSummaryRepository summaryRepository,
                      EventService eventService, Clock clock) {
        this.organizerRepository = organizerRepository;
        this.summaryRepository = summaryRepository;
        this.eventService = eventService;
        this.clock = clock;
    }

    /**
     * Keeps the organizer Goabase gives for an event and returns their slug; null when there is no usable name.
     * A newer address from Goabase replaces the old one.
     */
    public String record(String name, String url) {
        String clean = name == null ? "" : name.strip();
        String slug = ArtistNames.slug(clean);
        if (slug.isEmpty() || clean.length() > 200) {
            return null;
        }
        String cleanUrl = url == null || url.isBlank() || url.length() > 500 ? null : url.strip();
        organizerRepository.findById(slug).ifPresentOrElse(
                organizer -> {
                    if (cleanUrl != null && !cleanUrl.equals(organizer.getUrl())) {
                        organizer.setUrl(cleanUrl);
                        organizerRepository.save(organizer);
                    }
                },
                () -> organizerRepository.save(new Organizer(slug, clean, cleanUrl, clock.instant())));
        return slug;
    }

    /** Organizers with upcoming events whose name contains the search ("" lists all), busiest first */
    public List<OrganizerDto.Summary> find(String search) {
        List<Long> upcomingIds = eventService.findUpcoming().stream().map(EventDto::id).toList();
        Map<String, Long> counts = summaryRepository.findAllById(upcomingIds).stream()
                .filter(summary -> summary.getOrganizerSlug() != null)
                .collect(Collectors.groupingBy(EventSummary::getOrganizerSlug, Collectors.counting()));
        String part = ArtistNames.slug(search);
        return organizerRepository.findAllById(counts.keySet()).stream()
                .filter(organizer -> organizer.getSlug().contains(part))
                .map(organizer -> new OrganizerDto.Summary(organizer.getSlug(), organizer.getName(), counts.get(organizer.getSlug())))
                .sorted(Comparator.comparingLong(OrganizerDto.Summary::upcomingEvents).reversed()
                        .thenComparing(OrganizerDto.Summary::name, String.CASE_INSENSITIVE_ORDER))
                .limit(MAX_RESULTS)
                .toList();
    }

    /** The organizer's page: their upcoming events soonest first, and the ones they ran before, latest first */
    public OrganizerDto get(String slug) {
        Organizer organizer = organizerRepository.findById(ArtistNames.slug(slug))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Organizer not found"));
        List<EventSummary> theirs = summaryRepository.findByOrganizerSlug(organizer.getSlug());
        Set<Long> ids = theirs.stream().map(EventSummary::getEventId).collect(Collectors.toSet());
        List<EventDto> upcoming = eventService.findUpcoming().stream().filter(event -> ids.contains(event.id())).toList();
        LocalDate today = LocalDate.now(clock);
        List<PastGig> past = theirs.stream()
                .filter(summary -> summary.getLastDay() != null && summary.getLastDay().isBefore(today))
                .sorted(Comparator.comparing(EventSummary::getDateStart, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(MAX_PAST_EVENTS)
                .map(summary -> new PastGig(summary.getEventId(), summary.getName(), summary.getDateStart(),
                        summary.getTown(), summary.getCountry()))
                .toList();
        OrganizerDto.Profile profile = OrganizerDto.Profile.of(organizer);
        return new OrganizerDto(organizer.getSlug(), organizer.getName(), organizer.getUrl(),
                isEmpty(profile) ? null : profile, upcoming, past);
    }

    private static boolean isEmpty(OrganizerDto.Profile profile) {
        return profile.photoUrl() == null && profile.bio() == null && profile.instagramUrl() == null && profile.websiteUrl() == null;
    }

    /** The organizers with these slugs, by slug (for linking event pages to organizer pages) */
    public Map<String, Organizer> bySlug(Set<String> slugs) {
        return organizerRepository.findAllById(slugs).stream().collect(Collectors.toMap(Organizer::getSlug, Function.identity()));
    }
}
