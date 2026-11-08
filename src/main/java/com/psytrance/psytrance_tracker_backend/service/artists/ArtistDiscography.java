package com.psytrance.psytrance_tracker_backend.service.artists;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.psytrance.psytrance_tracker_backend.model.AdminChange;
import com.psytrance.psytrance_tracker_backend.repository.AdminChangeRepository;
import com.psytrance.psytrance_tracker_backend.service.map.MapFestivals;
import com.psytrance.psytrance_tracker_backend.service.pages.FestivalPages;
import com.psytrance.psytrance_tracker_backend.util.ArtistNames;
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
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Year;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What used to be bundled with the map and fixed only with a deploy, now in the database for the people it's about:
 * <ul>
 *   <li>an artist's country, first year and albums (map/artists.json), edited by the artist or an admin;</li>
 *   <li>festival sets (map/appearances.json), copied into the artist's "Festivals played" list (artist_festivals),
 *       so the artist edits one list; a festival's owner (or an admin) edits the sets played at their festival.</li>
 * </ul>
 * The bundled files only fill the tables the first time (data_seeds); from then on the tables are the truth.
 */
@Service
public class ArtistDiscography {

    static final int MAX_ALBUMS = 40;
    static final int MAX_SETS = 200;
    private static final Pattern YEAR = Pattern.compile("\\b(19[6-9]\\d|20\\d\\d)\\b");
    private static final Pattern YOUTUBE = Pattern.compile("(?:youtube\\.com/watch\\?v=|youtu\\.be/)([\\w-]{11})");

    private final JdbcClient jdbc;
    private final JsonMapper jsonMapper;
    private final MapFestivals mapFestivals;
    private final FestivalPages festivalPages;
    private final ArtistEditing artistEditing;
    private final AdminChangeRepository changeRepository;
    private final Clock clock;

    public ArtistDiscography(JdbcClient jdbc, JsonMapper jsonMapper, MapFestivals mapFestivals, FestivalPages festivalPages,
                             ArtistEditing artistEditing, AdminChangeRepository changeRepository, Clock clock) {
        this.jdbc = jdbc;
        this.jsonMapper = jsonMapper;
        this.mapFestivals = mapFestivals;
        this.festivalPages = festivalPages;
        this.artistEditing = artistEditing;
        this.changeRepository = changeRepository;
        this.clock = clock;
    }

    public record Album(String title, Integer year) {
    }

    /** country, since: where the project is from and the year it started (null when not known) */
    public record Discography(String country, Integer since, List<Album> albums) {
    }

    /** A set an artist played at a festival station; url: the recording (YouTube) or the line-up, if any */
    public record Set(String artistSlug, String artist, String festivalId, String festival, Integer year, String url) {
    }

    /** One set as a festival's owner writes it: who played, which year, a link to the recording */
    public record FestivalSet(String artist, Integer year, String url) {
    }

    // ---- First start: copy the bundled files ----

    @JsonIgnoreProperties(ignoreUnknown = true)
    record FileArtist(String country, Integer since, List<Album> albums) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record FileSet(String festival, String video, String title) {
    }

    @PostConstruct
    @Transactional
    void seed() {
        if (!seeded("artists.json")) {
            Map<String, FileArtist> artists = read("map/artists.json", new TypeReference<>() { });
            artists.forEach((name, a) -> {
                String slug = ArtistNames.slug(name);
                if (slug.isEmpty() || jdbc.sql("select count(*) from artist_discography where artist_slug = ?").param(slug)
                        .query(Integer.class).single() > 0) {
                    return;
                }
                jdbc.sql("insert into artist_discography (artist_slug, country, since) values (?, ?, ?)")
                        .params(slug, a.country(), a.since()).update();
                List<Album> albums = a.albums() == null ? List.of() : a.albums();
                for (int i = 0; i < albums.size(); i++) {
                    jdbc.sql("insert into artist_albums (artist_slug, position, title, year) values (?, ?, ?, ?)")
                            .params(slug, i, albums.get(i).title(), albums.get(i).year()).update();
                }
            });
            done("artists.json");
        }
        if (!seeded("appearances.json")) {
            Map<String, List<FileSet>> sets = read("map/appearances.json", new TypeReference<>() { });
            sets.forEach((name, list) -> {
                String slug = ArtistNames.slug(name);
                if (slug.isEmpty()) return;
                for (FileSet set : list) {
                    String festival = mapFestivals.name(set.festival()).orElse(null);
                    if (festival == null) continue;
                    // The artist may have written this festival already: their own line stays
                    boolean has = jdbc.sql("select name from artist_festivals where artist_slug = ?").param(slug)
                            .query(String.class).list().stream().anyMatch(n -> mapFestivals.idOf(n).filter(set.festival()::equals).isPresent());
                    if (has) continue;
                    String url = set.video() == null ? null : "https://www.youtube.com/watch?v=" + set.video();
                    insertSet(slug, festival, yearIn(set.title()), url);
                }
            });
            done("appearances.json");
        }
    }

    // ---- Albums ----

    /** {@code artist slug -> discography}, for the map and the artist pages */
    public Map<String, Discography> all() {
        Map<String, Discography> bySlug = new LinkedHashMap<>();
        Map<String, List<Album>> albums = albumsBySlug();
        jdbc.sql("select artist_slug, country, since from artist_discography order by artist_slug")
                .query((rs, n) -> Map.entry(rs.getString(1), new Discography(rs.getString(2), (Integer) rs.getObject(3),
                        albums.getOrDefault(rs.getString(1), List.of()))))
                .list().forEach(e -> bySlug.put(e.getKey(), e.getValue()));
        albums.forEach((slug, list) -> bySlug.putIfAbsent(slug, new Discography(null, null, list)));
        return bySlug;
    }

    public Discography of(String slug) {
        return Optional.ofNullable(all().get(ArtistNames.slug(slug))).orElse(new Discography(null, null, List.of()));
    }

    /** The artist (or an admin) writes their country, first year and albums; logged in the history */
    @Transactional
    public Discography save(String slug, Discography d, String username) {
        String artistName = artistEditing.requireEditorName(slug, username);
        String key = ArtistNames.slug(slug);
        List<Album> albums = d.albums() == null ? List.of() : d.albums();
        if (albums.size() > MAX_ALBUMS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At most " + MAX_ALBUMS + " albums");
        }
        int now = Year.now(clock).getValue();
        for (Album a : albums) {
            if (a.title() == null || a.title().isBlank() || a.title().strip().length() > 200) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Every album needs a title (200 characters at most)");
            }
            if (a.year() != null && (a.year() < 1960 || a.year() > now + 1)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "An album's year is between 1960 and next year");
            }
        }
        if (d.since() != null && (d.since() < 1960 || d.since() > now)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The year it started is between 1960 and now");
        }
        String country = d.country() == null || d.country().isBlank() ? null : d.country().strip();
        if (country != null && country.length() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The country is 100 characters at most");
        }
        jdbc.sql("delete from artist_discography where artist_slug = ?").param(key).update();
        jdbc.sql("insert into artist_discography (artist_slug, country, since, updated_by, updated_at) values (?, ?, ?, ?, ?)")
                .params(key, country, d.since(), username, Timestamp.from(clock.instant())).update();
        jdbc.sql("delete from artist_albums where artist_slug = ?").param(key).update();
        for (int i = 0; i < albums.size(); i++) {
            jdbc.sql("insert into artist_albums (artist_slug, position, title, year) values (?, ?, ?, ?)")
                    .params(key, i, albums.get(i).title().strip(), albums.get(i).year()).update();
        }
        changeRepository.save(new AdminChange(username, AdminChange.ARTIST_ALBUMS, artistName, null,
                summary(albums.stream().map(a -> a.year() == null ? a.title() : a.title() + " " + a.year()).toList()),
                clock.instant()));
        return of(key);
    }

    // ---- Festival sets ----

    /** Every set at a festival station of the map, for the map, the festival and label pages */
    public List<Set> sets() {
        Map<String, String> names = artistNames();
        List<Set> sets = new ArrayList<>();
        jdbc.sql("select artist_slug, name, year, url from artist_festivals order by artist_slug, position")
                .query((rs, n) -> new Object[]{rs.getString(1), rs.getString(2), rs.getObject(3), rs.getString(4)})
                .list().forEach(row -> {
                    String slug = (String) row[0];
                    String name = (String) row[1];
                    mapFestivals.idOf(name).ifPresent(id -> sets.add(new Set(slug, names.getOrDefault(slug, slug), id,
                            mapFestivals.name(id).orElse(name), (Integer) row[2], (String) row[3])));
                });
        return sets;
    }

    public List<FestivalSet> setsAt(String festivalId) {
        return sets().stream().filter(s -> s.festivalId().equals(festivalId))
                .map(s -> new FestivalSet(s.artist(), s.year(), s.url())).toList();
    }

    /**
     * The festival's owner (or an admin) writes the sets played there: the festival's lines in every artist's
     * "Festivals played" are replaced by these. An artist's other festivals stay as they are.
     */
    @Transactional
    public List<FestivalSet> saveSetsAt(String festivalId, List<FestivalSet> sets, String username) {
        String festival = mapFestivals.name(festivalId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No festival called " + festivalId));
        if (username == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Log in first");
        }
        if (!festivalPages.canEdit(festivalId, username)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the festival and admins can edit its sets");
        }
        List<FestivalSet> clean = sets == null ? List.of() : sets;
        if (clean.size() > MAX_SETS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At most " + MAX_SETS + " sets");
        }
        int now = Year.now(clock).getValue();
        for (FestivalSet s : clean) {
            if (s.artist() == null || ArtistNames.slug(s.artist()).isEmpty() || s.artist().strip().length() > 100) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Every set needs the artist's name");
            }
            if (s.year() != null && (s.year() < 1960 || s.year() > now)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A set's year is between 1960 and now");
            }
            if (s.url() != null && !s.url().isBlank() && (!s.url().strip().startsWith("https://") || s.url().strip().length() > 300)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A link starts with https:// (300 characters at most)");
            }
        }
        // Out: this festival's lines, whichever spelling of its name they use
        List<Object[]> rows = jdbc.sql("select id, name from artist_festivals")
                .query((rs, n) -> new Object[]{rs.getLong(1), rs.getString(2)}).list();
        for (Object[] row : rows) {
            if (mapFestivals.idOf((String) row[1]).filter(festivalId::equals).isPresent()) {
                jdbc.sql("delete from artist_festivals where id = ?").param(row[0]).update();
            }
        }
        for (FestivalSet s : clean) {
            String url = s.url() == null || s.url().isBlank() ? null : s.url().strip();
            insertSet(ArtistNames.slug(s.artist()), festival, s.year(), url);
            givePage(s.artist().strip());
        }
        changeRepository.save(new AdminChange(username, AdminChange.ARTIST_FESTIVALS, "Festival: " + festival, null,
                summary(clean.stream().map(s -> s.year() == null ? s.artist() : s.artist() + " " + s.year()).toList()),
                clock.instant()));
        return setsAt(festivalId);
    }

    // ---- Helpers ----

    // An artist who played a set here gets an artist page, if they have none yet (and the name isn't merged into another)
    private void givePage(String name) {
        String slug = ArtistNames.slug(name);
        jdbc.sql("""
                        insert into artists (slug, name)
                        select :slug, :name
                        where not exists (select 1 from artists where slug = :slug)
                          and not exists (select 1 from artist_aliases where alias_slug = :slug)
                        """)
                .param("slug", slug).param("name", name).update();
    }

    private void insertSet(String slug, String festival, Integer year, String url) {
        Integer next = jdbc.sql("select coalesce(max(position) + 1, 0) from artist_festivals where artist_slug = ?").param(slug)
                .query(Integer.class).single();
        jdbc.sql("insert into artist_festivals (artist_slug, position, name, year, url) values (?, ?, ?, ?, ?)")
                .params(slug, next, festival, year, url).update();
    }

    // The names the line-ups (artists) and the map (placements) write, by slug
    private Map<String, String> artistNames() {
        Map<String, String> names = new HashMap<>();
        jdbc.sql("select artist_name from map_artist_genres").query(String.class).list()
                .forEach(n -> names.putIfAbsent(ArtistNames.slug(n), n));
        jdbc.sql("select slug, name from artists").query((rs, n) -> Map.entry(rs.getString(1), rs.getString(2))).list()
                .forEach(e -> names.putIfAbsent(e.getKey(), e.getValue()));
        return names;
    }

    private Map<String, List<Album>> albumsBySlug() {
        Map<String, List<Album>> albums = new LinkedHashMap<>();
        jdbc.sql("select artist_slug, title, year from artist_albums order by artist_slug, position")
                .query((rs, n) -> Map.entry(rs.getString(1), new Album(rs.getString(2), (Integer) rs.getObject(3)))).list()
                .forEach(e -> albums.computeIfAbsent(e.getKey(), k -> new ArrayList<>()).add(e.getValue()));
        return albums;
    }

    static Integer yearIn(String text) {
        if (text == null) return null;
        Matcher m = YEAR.matcher(text);
        return m.find() ? Integer.valueOf(m.group(1)) : null;
    }

    /** The YouTube video id of a link, if it is one */
    static String youtubeId(String url) {
        if (url == null) return null;
        Matcher m = YOUTUBE.matcher(url);
        return m.find() ? m.group(1) : null;
    }

    private boolean seeded(String name) {
        return jdbc.sql("select count(*) from data_seeds where name = ?").param(name).query(Integer.class).single() > 0;
    }

    private void done(String name) {
        jdbc.sql("insert into data_seeds (name, done_at) values (?, ?)").params(name, Timestamp.from(clock.instant())).update();
    }

    private <T> T read(String resource, TypeReference<T> type) {
        try (InputStream in = new ClassPathResource(resource).getInputStream()) {
            return jsonMapper.readValue(in, type);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + resource, e);
        }
    }

    private static String summary(List<String> items) {
        String text = String.join(", ", items);
        return text.length() > 900 ? text.substring(0, 900) + "…" : text;
    }
}
