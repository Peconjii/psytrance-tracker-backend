package com.psytrance.psytrance_tracker_backend.service.map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.psytrance.psytrance_tracker_backend.model.AdminChange;
import com.psytrance.psytrance_tracker_backend.repository.AdminChangeRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * What the galaxy says about each genre: the description, how it sounds, its year and tempo, and the key tracks.
 * genres.json only fills the table the first time; from then on admins and editors fix it in Admin -> Galaxy, the
 * map reads it from the API, and every change is logged in admin_changes as the whole content before and after,
 * so it can be undone.
 */
@Service
public class GalaxyContent {

    private static final String RESOURCE = "map/genres.json";
    private static final TypeReference<List<Track>> TRACKS = new TypeReference<>() {
    };

    /** A key track. {@code drop}: the second the drop comes, so the quiz can start just before it; null if unknown */
    public record Track(String artist, String title, Integer year, String youtube, Boolean famous, Boolean verified,
                       Integer drop) {
    }

    // Booleans as objects: a form that leaves one out means false (see clean), not a refused request
    /** {@code verified}: someone checked the texts (the tracks are checked one by one) */
    public record Content(String description, String bass, String kick, String atmosphere, Integer year, Integer bpmMin,
                          Integer bpmMax, Boolean verified, List<Track> tracks) {
    }

    private final JdbcClient jdbc;
    private final JsonMapper jsonMapper;
    private final MapGenres mapGenres;
    private final AdminChangeRepository changeRepository;
    private final Clock clock;

    public GalaxyContent(JdbcClient jdbc, JsonMapper jsonMapper, MapGenres mapGenres, AdminChangeRepository changeRepository,
                         Clock clock) {
        this.jdbc = jdbc;
        this.jsonMapper = jsonMapper;
        this.mapGenres = mapGenres;
        this.changeRepository = changeRepository;
        this.clock = clock;
    }

    // First start: copy the texts and tracks of genres.json into the table; from then on the table is the truth
    @PostConstruct
    void load() {
        if (jdbc.sql("select count(*) from map_genre_content").query(Long.class).single() > 0) {
            return;
        }
        for (FileGenre genre : readFile()) {
            Map<String, String> production = genre.production() == null ? Map.of() : genre.production();
            List<Integer> bpm = genre.bpm() == null ? List.of() : genre.bpm();
            List<Track> tracks = genre.tracks() == null ? List.of() : genre.tracks().stream()
                    .map(t -> new Track(t.artist(), t.title(), t.year(), t.youtube(), Boolean.TRUE.equals(t.famous()), false, null))
                    .toList();
            insert(genre.id(), new Content(Objects.requireNonNullElse(genre.description(), ""), production.get("bass"),
                    production.get("kick"), production.get("atmosphere"), genre.year(), bpm.isEmpty() ? null : bpm.get(0),
                    bpm.size() < 2 ? null : bpm.get(1), false, tracks), null);
            // nothing in genres.json is marked as checked: the community said much of it is wrong
        }
    }

    /** {@code genreId -> content}, for the map */
    public Map<String, Content> all() {
        Map<String, Content> byGenre = new LinkedHashMap<>();
        jdbc.sql("select * from map_genre_content order by genre_id")
                .query((rs, n) -> Map.entry(rs.getString("genre_id"), new Content(rs.getString("description"),
                        rs.getString("bass"), rs.getString("kick"), rs.getString("atmosphere"), (Integer) rs.getObject("year"),
                        (Integer) rs.getObject("bpm_min"), (Integer) rs.getObject("bpm_max"), rs.getString("verified_by") != null,
                        readTracks(rs.getString("tracks")))))
                .list()
                .forEach(entry -> byGenre.put(entry.getKey(), entry.getValue()));
        return byGenre;
    }

    public Content get(String genreId) {
        return find(genreId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown genre: " + genreId));
    }

    private Optional<Content> find(String genreId) {
        return Optional.ofNullable(all().get(genreId));
    }

    /** Replaces a genre's content; logged with the content before, so it can be undone */
    @Transactional
    public Content save(String genreId, Content content, String username) {
        return save(genreId, content, username, null);
    }

    private Content save(String genreId, Content content, String username, Long undoOf) {
        if (!mapGenres.isGenre(genreId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown genre: " + genreId);
        }
        Content clean = clean(content);
        Optional<Content> before = find(genreId);
        if (before.isPresent() && before.get().equals(clean)) {
            return clean;
        }
        // Who checked the texts, and when, stays while they stay checked; turning "verified" on records the editor
        Optional<Verified> checked = clean.verified() ? verified(genreId) : Optional.empty();
        jdbc.sql("delete from map_genre_content where genre_id = ?").param(genreId).update();
        insert(genreId, clean, checked.orElse(new Verified(username, clock.instant())));
        jdbc.sql("update map_genre_content set updated_by = ?, updated_at = ? where genre_id = ?")
                .params(username, java.sql.Timestamp.from(clock.instant()), genreId).update();
        changeRepository.save(new AdminChange(username, AdminChange.GENRE_CONTENT, genreId,
                before.map(this::json).orElse(null), json(clean), clock.instant(), undoOf));
        return clean;
    }

    /** Puts back a genre's content from before a change, unless the genre was changed again since */
    @Transactional
    public void undo(AdminChange change, String username) {
        Content now = get(change.getTarget());
        if (!json(now).equals(change.getAfterValue())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    change.getTarget() + " was changed again since. Undo the newer change first.");
        }
        if (change.getBeforeValue() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This change can't be undone here");
        }
        save(change.getTarget(), jsonMapper.readValue(change.getBeforeValue(), Content.class), username, change.getId());
    }

    private record Verified(String by, Instant at) {
    }

    private Optional<Verified> verified(String genreId) {
        return jdbc.sql("select verified_by, verified_at from map_genre_content where genre_id = ? and verified_by is not null")
                .param(genreId)
                .query((rs, n) -> new Verified(rs.getString(1), rs.getTimestamp(2).toInstant()))
                .optional();
    }

    private void insert(String genreId, Content content, Verified verified) {
        jdbc.sql("""
                        insert into map_genre_content (genre_id, description, bass, kick, atmosphere, year, bpm_min, bpm_max,
                            tracks, verified_by, verified_at)
                        values (:id, :description, :bass, :kick, :atmosphere, :year, :bpmMin, :bpmMax, :tracks, :verifiedBy, :verifiedAt)
                        """)
                .param("id", genreId)
                .param("description", content.description())
                .param("bass", content.bass())
                .param("kick", content.kick())
                .param("atmosphere", content.atmosphere())
                .param("year", content.year())
                .param("bpmMin", content.bpmMin())
                .param("bpmMax", content.bpmMax())
                .param("tracks", jsonMapper.writeValueAsString(content.tracks()))
                .param("verifiedBy", content.verified() ? verified.by() : null)
                .param("verifiedAt", content.verified() ? java.sql.Timestamp.from(verified.at()) : null)
                .update();
    }

    // Trimmed texts, empty ones as null, and only tracks with an artist, a title and a YouTube id
    private static Content clean(Content c) {
        if (c.description() == null || c.description().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A genre needs a description");
        }
        if (c.bpmMin() != null && c.bpmMax() != null && c.bpmMin() > c.bpmMax()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The slowest tempo is above the fastest");
        }
        List<Track> tracks = new ArrayList<>();
        for (Track t : c.tracks() == null ? List.<Track>of() : c.tracks()) {
            // A track without a video is fine (the map links a YouTube search for it), one with a wrong link is not
            if (blank(t.artist()) || blank(t.title())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Every track needs an artist and a title");
            }
            if (t.drop() != null && t.drop() < 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The drop can't be before the track starts");
            }
            tracks.add(new Track(t.artist().strip(), t.title().strip(), t.year(), blank(t.youtube()) ? null : youtubeId(t.youtube()),
                    Boolean.TRUE.equals(t.famous()), Boolean.TRUE.equals(t.verified()), t.drop()));
        }
        return new Content(c.description().strip(), text(c.bass()), text(c.kick()), text(c.atmosphere()), c.year(),
                c.bpmMin(), c.bpmMax(), Boolean.TRUE.equals(c.verified()), List.copyOf(tracks));
    }

    // A whole YouTube link ("https://www.youtube.com/watch?v=iIsVzSMRw8E", "https://youtu.be/iIsVzSMRw8E") or the id
    static String youtubeId(String value) {
        String v = value.strip();
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(?:v=|youtu\\.be/|/embed/|/shorts/)([A-Za-z0-9_-]{11})").matcher(v);
        if (m.find()) {
            return m.group(1);
        }
        if (!v.matches("[A-Za-z0-9_-]{11}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Not a YouTube link: " + v);
        }
        return v;
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    private static String text(String s) {
        return blank(s) ? null : s.strip();
    }

    private String json(Content content) {
        return jsonMapper.writeValueAsString(content);
    }

    private List<Track> readTracks(String json) {
        return jsonMapper.readValue(json, TRACKS);
    }

    private List<FileGenre> readFile() {
        try (InputStream in = new ClassPathResource(RESOURCE).getInputStream()) {
            return jsonMapper.readValue(in, FileGenres.class).genres();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + RESOURCE, e);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record FileGenres(List<FileGenre> genres) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record FileGenre(String id, String description, Integer year, List<Integer> bpm, Map<String, String> production,
                     List<FileTrack> tracks) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record FileTrack(String artist, String title, Integer year, String youtube, Boolean famous) {
    }
}
