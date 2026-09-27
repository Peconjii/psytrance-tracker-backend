package com.psytrance.psytrance_tracker_backend.service;

import com.psytrance.psytrance_tracker_backend.client.YouTubeClient;
import com.psytrance.psytrance_tracker_backend.dto.AftermovieDto;
import com.psytrance.psytrance_tracker_backend.dto.EventDetailsDto;
import com.psytrance.psytrance_tracker_backend.exception.YouTubeUnavailableException;
import com.psytrance.psytrance_tracker_backend.model.AftermovieLookup;
import com.psytrance.psytrance_tracker_backend.repository.AftermovieLookupRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Finds a festival's own aftermovie on YouTube. Only festivals get one, and a video only counts
 * when its title has the festival's name and the word "aftermovie" - a random video that merely
 * mentions the festival is worse than no video.
 */
@Service
public class AftermovieService {

    private static final Logger log = LoggerFactory.getLogger(AftermovieService.class);

    // Aftermovies come out a few times a year, so a monthly look is plenty
    private static final Duration RECHECK_AFTER = Duration.ofDays(30);

    // Where the festival name ends: "Transition Experience 2026 - OA Psytrance Festival", "UP 2026 | Glamping"
    private static final Pattern SUBTITLE = Pattern.compile("\\s[-–—]\\s|[|–—/@(\\[]");
    private static final Pattern YEAR = Pattern.compile("\\b(19|20)\\d{2}\\b");
    private static final Pattern AFTERMOVIE = Pattern.compile("after\\s*-?\\s*movie");
    // Words too common to tell festivals apart
    private static final Set<String> FILLER_WORDS = Set.of(
            "festival", "fest", "open", "air", "oa", "psytrance", "psy", "trance", "the", "of", "and", "x", "edition");

    private final EventService eventService;
    private final YouTubeClient youTubeClient;
    private final AftermovieLookupRepository lookups;
    private final Clock clock;

    public AftermovieService(EventService eventService,
                             YouTubeClient youTubeClient,
                             AftermovieLookupRepository lookups,
                             Clock clock) {
        this.eventService = eventService;
        this.youTubeClient = youTubeClient;
        this.lookups = lookups;
        this.clock = clock;
    }

    public Optional<AftermovieDto> findForEvent(long eventId) {
        if (!youTubeClient.isEnabled()) {
            return Optional.empty();
        }
        EventDetailsDto event = eventService.findDetails(eventId);
        if (!isFestival(event)) {
            return Optional.empty();
        }
        String festivalName = festivalName(event.nameParty());
        List<String> nameWords = significantWords(festivalName);
        if (nameWords.isEmpty()) {
            return Optional.empty();
        }
        String key = String.join(" ", nameWords);

        Instant now = clock.instant();
        Optional<AftermovieLookup> cached = lookups.findById(key);
        if (cached.isPresent() && cached.get().getCheckedAt().plus(RECHECK_AFTER).isAfter(now)) {
            return toDto(cached.get());
        }

        try {
            AftermovieLookup lookup = youTubeClient.searchVideos(festivalName + " aftermovie").stream()
                    .filter(video -> isAftermovieOf(video.title(), nameWords))
                    .findFirst()
                    .map(video -> new AftermovieLookup(key, video.videoId(), video.title(), now))
                    .orElseGet(() -> new AftermovieLookup(key, null, null, now));
            save(lookup);
            return toDto(lookup);
        } catch (YouTubeUnavailableException e) {
            // Not stored, so the next visit tries again; an older result is still better than nothing
            log.warn("YouTube search for '{}' failed", festivalName, e);
            return cached.flatMap(AftermovieService::toDto);
        }
    }

    // Goabase marks some festivals only in the name: "Transition Experience 2026 - OA Psytrance Festival" is "Open Air"
    static boolean isFestival(EventDetailsDto event) {
        return "Festival".equalsIgnoreCase(event.nameType())
                || normalize(event.nameParty()).contains("festival");
    }

    /** "Transition Experience 2026 - OA Psytrance Festival" → "Transition Experience" */
    static String festivalName(String nameParty) {
        String name = nameParty;
        var subtitle = SUBTITLE.matcher(name);
        if (subtitle.find() && subtitle.start() > 0) {
            name = name.substring(0, subtitle.start());
        }
        return YEAR.matcher(name).replaceAll("").replaceAll("\\s+", " ").strip();
    }

    static List<String> significantWords(String festivalName) {
        return Arrays.stream(normalize(festivalName).split("[^a-z0-9]+"))
                // One-letter words would match inside almost any title
                .filter(word -> word.length() > 1 && !FILLER_WORDS.contains(word))
                .toList();
    }

    static boolean isAftermovieOf(String videoTitle, List<String> nameWords) {
        String title = normalize(videoTitle);
        return AFTERMOVIE.matcher(title).find() && nameWords.stream().allMatch(title::contains);
    }

    // Lower case without accents, so "ESPAÇO" matches "espaco"
    private static String normalize(String value) {
        return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
    }

    private void save(AftermovieLookup lookup) {
        try {
            lookups.save(lookup);
        } catch (DataIntegrityViolationException e) {
            // Two visitors searched for the same festival at once; the other one's result is just as good
            log.debug("Aftermovie lookup for '{}' was stored by another request", lookup.getFestivalKey());
        }
    }

    private static Optional<AftermovieDto> toDto(AftermovieLookup lookup) {
        return lookup.found()
                ? Optional.of(new AftermovieDto(lookup.getVideoId(), lookup.getTitle()))
                : Optional.empty();
    }
}
