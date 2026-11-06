package com.psytrance.psytrance_tracker_backend.service.site;

import com.psytrance.psytrance_tracker_backend.model.Artist;
import com.psytrance.psytrance_tracker_backend.repository.ArtistRepository;
import com.psytrance.psytrance_tracker_backend.service.map.GalaxyContent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.text.Normalizer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.IntPredicate;
import java.util.regex.Pattern;

/**
 * Lists that make keeping the site in order quicker, for Admin -> Upkeep (events without a genre are the harbour
 * queue in Admin -> Events):
 * <ul>
 * <li>key tracks whose YouTube video is gone (deleted, private or not allowed to play on other sites), checked once
 * a day by BackgroundJobs, so a dead track is found before someone reports it;</li>
 * <li>artists from the line-ups that are probably the same person written two ways ("Astrix" and "Astrix (IL)").</li>
 * </ul>
 */
@Service
public class Upkeep {

    private static final Logger log = LoggerFactory.getLogger(Upkeep.class);
    private static final Duration CHECK_EVERY = Duration.ofHours(24);

    /** A key track whose video doesn't play: {@code problem} is "removed" (404) or "not playable here" (401/403) */
    public record DeadTrack(String genreId, String artist, String title, String youtube, String problem) {
    }

    public record DeadTracks(Instant checkedAt, int checked, List<DeadTrack> tracks) {
    }

    /** Artists that are probably one: their names differ only in brackets, "DJ", "live" or punctuation */
    public record DuplicateArtists(String key, List<String> names) {
    }

    private final GalaxyContent galaxyContent;
    private final ArtistRepository artistRepository;
    private final Clock clock;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL).build();

    // The last check, kept in memory: after a restart the next timer run checks again
    private volatile DeadTracks deadTracks;

    public Upkeep(GalaxyContent galaxyContent, ArtistRepository artistRepository, Clock clock) {
        this.galaxyContent = galaxyContent;
        this.artistRepository = artistRepository;
        this.clock = clock;
    }

    // ---------- Dead YouTube links ----------

    /** The last check, null before the first */
    public DeadTracks deadTracks() {
        return deadTracks;
    }

    /** For the hourly timer: checks again when the last check is a day old (or there is none yet) */
    public void checkIfDue() {
        DeadTracks last = deadTracks;
        if (last == null || last.checkedAt().plus(CHECK_EVERY).isBefore(clock.instant())) {
            checkTracks();
        }
    }

    /** Asks YouTube about every key track's video, one after another (a few hundred, about a minute) */
    public synchronized DeadTracks checkTracks() {
        return checkTracks(this::status);
    }

    // statusOf: the answer of YouTube's oEmbed for a video id; separate so tests don't go online
    synchronized DeadTracks checkTracks(java.util.function.ToIntFunction<String> statusOf) {
        List<DeadTrack> dead = new ArrayList<>();
        int checked = 0;
        for (Map.Entry<String, GalaxyContent.Content> genre : galaxyContent.all().entrySet()) {
            for (GalaxyContent.Track track : genre.getValue().tracks()) {
                if (track.youtube() == null || track.youtube().isBlank()) {
                    continue;
                }
                checked++;
                int status = statusOf.applyAsInt(track.youtube());
                String problem = status == 404 || status == 400 ? "removed"
                        : status == 401 || status == 403 ? "not playable here" : null;
                if (problem != null) {
                    dead.add(new DeadTrack(genre.getKey(), track.artist(), track.title(), track.youtube(), problem));
                }
            }
        }
        deadTracks = new DeadTracks(clock.instant(), checked, dead);
        log.info("Checked {} key track videos, {} don't play", checked, dead.size());
        return deadTracks;
    }

    // 200 plays; 404 or 400 gone; 401/403 private or not allowed on other sites; 0 when YouTube didn't answer (not
    // counted as dead, the next check tries again)
    private int status(String videoId) {
        try {
            Thread.sleep(150);   // gently, one at a time
            HttpRequest request = HttpRequest.newBuilder(URI.create("https://www.youtube.com/oembed?format=json&url="
                            + "https%3A%2F%2Fwww.youtube.com%2Fwatch%3Fv%3D" + videoId))
                    .timeout(Duration.ofSeconds(15)).GET().build();
            return http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return 0;
        } catch (Exception e) {
            return 0;
        }
    }

    // ---------- Probable duplicate artists ----------

    private static final Pattern BRACKETS = Pattern.compile("[(\\[{][^)\\]}]*[)\\]}]");
    private static final Pattern DJ_LIVE = Pattern.compile("^(dj|the)\\s+|\\s+(live|dj set|liveset|live set)$");
    private static final Pattern ACCENTS = Pattern.compile("\\p{M}");

    /** Groups of artist names that are probably the same artist, the most names first */
    public List<DuplicateArtists> duplicateArtists() {
        Map<String, List<String>> byKey = new LinkedHashMap<>();
        for (Artist artist : artistRepository.findAll()) {
            String key = key(artist.getName());
            if (key.length() >= 3) {
                byKey.computeIfAbsent(key, k -> new ArrayList<>()).add(artist.getName());
            }
        }
        return byKey.entrySet().stream()
                .filter(entry -> entry.getValue().size() > 1)
                .map(entry -> new DuplicateArtists(entry.getKey(),
                        entry.getValue().stream().sorted(String.CASE_INSENSITIVE_ORDER).toList()))
                .sorted(Comparator.comparing((DuplicateArtists d) -> -d.names().size()).thenComparing(DuplicateArtists::key))
                .toList();
    }

    /** "DJ Astrix (IL)" and "astrix live" both become "astrix" */
    static String key(String name) {
        String plain = ACCENTS.matcher(Normalizer.normalize(name == null ? "" : name, Normalizer.Form.NFD)).replaceAll("")
                .toLowerCase(Locale.ROOT);
        plain = BRACKETS.matcher(plain).replaceAll(" ").replaceAll("\\s+", " ").strip();
        plain = DJ_LIVE.matcher(plain).replaceAll("").strip();
        IntPredicate keep = Character::isLetterOrDigit;
        return plain.codePoints().filter(keep)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append).toString();
    }
}
