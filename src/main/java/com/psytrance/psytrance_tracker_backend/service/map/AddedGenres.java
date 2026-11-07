package com.psytrance.psytrance_tracker_backend.service.map;

import com.psytrance.psytrance_tracker_backend.model.AdminChange;
import com.psytrance.psytrance_tracker_backend.repository.AdminChangeRepository;
import com.psytrance.psytrance_tracker_backend.util.ArtistNames;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Year;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Genres (stars) an admin adds to the galaxy from the admin panel, so a style the community asks for (hypnotic psy,
 * slambient) needs no change to genres.json and no deploy. Once added, a genre is like any other: MapGenres knows it
 * (placements, stage genres, words in stage headers), its texts and key tracks are edited in Admin -> Galaxy, and the
 * frontend draws it before the galaxy appears ({@code GET /api/map/extras}). A new star sits near the genres it is
 * linked to, unless the admin gives a spot.
 */
@Service
public class AddedGenres {

    static final Set<String> FAMILIES = Set.of("roots", "goa", "prog", "dark", "chill");
    private static final int MAX_LINKS = 4;
    private static final int MAX_KEYWORDS = 8;
    // How far from the middle of its linked stars a new star sits, turning by the golden angle for each one added
    private static final int DISTANCE = 170;
    private static final double GOLDEN_ANGLE = Math.PI * (3 - Math.sqrt(5));
    // The galaxy's world (types.ts WORLD): a star outside it couldn't be reached
    private static final int WORLD_WIDTH = 3250;
    private static final int WORLD_HEIGHT = 2535;

    private final JdbcClient jdbc;
    private final MapGenres mapGenres;
    private final MapPlacements mapPlacements;
    private final GalaxyContent galaxyContent;
    private final AdminChangeRepository changeRepository;
    private final Clock clock;

    public AddedGenres(JdbcClient jdbc, MapGenres mapGenres, MapPlacements mapPlacements, GalaxyContent galaxyContent,
                       AdminChangeRepository changeRepository, Clock clock) {
        this.jdbc = jdbc;
        this.mapGenres = mapGenres;
        this.mapPlacements = mapPlacements;
        this.galaxyContent = galaxyContent;
        this.changeRepository = changeRepository;
        this.clock = clock;
    }

    /** links: the genres it is drawn connected to, the first the one it grew out of; keywords: more names in stage headers */
    public record Genre(String id, String name, String family, int year, Integer bpmMin, Integer bpmMax,
                        List<String> links, List<String> keywords, int x, int y) {
    }

    /** What the panel sends; x and y may be left out (a new genre goes near its links, a changed one stays put) */
    public record Form(String name, String family, Integer year, Integer bpmMin, Integer bpmMax, List<String> links,
                       List<String> keywords, Integer x, Integer y, String description) {
    }

    @EventListener(ApplicationReadyEvent.class)
    public void load() {
        List<Genre> added = all();
        Map<String, String> aliases = new LinkedHashMap<>();
        for (Genre g : added) {
            aliases.put(g.name(), g.id());
            aliases.put(g.id().replace('-', ' '), g.id());
            g.keywords().forEach(k -> aliases.put(k, g.id()));
        }
        mapGenres.useAddedGenres(added.stream()
                .map(g -> new MapGenres.Genre(g.id(), g.name(), g.family(), List.of(), g.x(), g.y())).toList(), aliases);
        mapPlacements.refresh();
    }

    /** Every added genre, oldest first */
    public List<Genre> all() {
        return jdbc.sql("select * from map_genres order by created_at").query(this::genre).list();
    }

    @Transactional
    public Genre add(Form form, String adminUsername) {
        String name = name(form.name());
        String slug = ArtistNames.slug(name);
        String id = slug.length() > 40 ? slug.substring(0, 40) : slug;
        if (mapGenres.isGenre(id) || mapGenres.all().stream().anyMatch(g -> g.name().equalsIgnoreCase(name))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "There is a genre called " + name + " on the map already");
        }
        if (form.description() == null || form.description().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Write a short description: what it sounds like and where it comes from");
        }
        Checked c = check(form, null);
        int[] at = form.x() != null && form.y() != null ? spot(form.x(), form.y()) : position(c.links(), all().size());
        jdbc.sql("""
                        insert into map_genres (id, name, family, year, bpm_min, bpm_max, links, keywords, x, y, created_by, created_at)
                        values (:id, :name, :family, :year, :bpmMin, :bpmMax, :links, :keywords, :x, :y, :by, :at)
                        """)
                .param("id", id).param("name", name).param("family", c.family()).param("year", c.year())
                .param("bpmMin", form.bpmMin()).param("bpmMax", form.bpmMax())
                .param("links", String.join(",", c.links())).param("keywords", String.join(",", c.keywords()))
                .param("x", at[0]).param("y", at[1]).param("by", adminUsername).param("at", Timestamp.from(clock.instant()))
                .update();
        load();
        // Its texts start here; the key tracks are added next in Admin -> Galaxy, like any genre's
        galaxyContent.save(id, new GalaxyContent.Content(form.description(), null, null, null, c.year(), form.bpmMin(),
                form.bpmMax(), false, List.of()), adminUsername);
        changeRepository.save(new AdminChange(adminUsername, AdminChange.MAP_ADDED, "Genre: " + name, "",
                String.join(", ", c.links()), clock.instant()));
        return find(id).orElseThrow();
    }

    /** Name, scene, year, tempo, links, words and spot of an added genre; the map file's genres don't change here */
    @Transactional
    public Genre update(String id, Form form, String adminUsername) {
        Genre before = find(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "No genre " + id + " was added from the panel (the map file's genres stay)"));
        String name = name(form.name());
        if (!name.equalsIgnoreCase(before.name()) && mapGenres.all().stream().anyMatch(g -> g.name().equalsIgnoreCase(name))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "There is a genre called " + name + " on the map already");
        }
        Checked c = check(form, id);
        int[] at = form.x() != null && form.y() != null ? spot(form.x(), form.y()) : new int[]{before.x(), before.y()};
        jdbc.sql("""
                        update map_genres set name = :name, family = :family, year = :year, bpm_min = :bpmMin, bpm_max = :bpmMax,
                            links = :links, keywords = :keywords, x = :x, y = :y where id = :id
                        """)
                .param("id", id).param("name", name).param("family", c.family()).param("year", c.year())
                .param("bpmMin", form.bpmMin()).param("bpmMax", form.bpmMax())
                .param("links", String.join(",", c.links())).param("keywords", String.join(",", c.keywords()))
                .param("x", at[0]).param("y", at[1]).update();
        changeRepository.save(new AdminChange(adminUsername, AdminChange.MAP_ADDED, "Genre changed: " + name,
                before.name() + " · " + String.join(", ", before.links()) + " · " + before.x() + "," + before.y(),
                name + " · " + String.join(", ", c.links()) + " · " + at[0] + "," + at[1], clock.instant()));
        load();
        return find(id).orElseThrow();
    }

    /**
     * Takes an added genre off the galaxy (a mistake, a duplicate) with its texts and tracks; artists placed in it
     * leave it (an artist with no other genre leaves the map, and can be placed again in Artist genres).
     */
    @Transactional
    public void remove(String id, String adminUsername) {
        Genre genre = find(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "No genre " + id + " was added from the panel (the map file's genres stay)"));
        if (all().stream().anyMatch(g -> !g.id().equals(id) && g.links().contains(id))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Another added genre is linked to " + genre.name() + ": change that one first");
        }
        jdbc.sql("delete from map_artist_genres where genre_id = :id").param("id", id).update();
        jdbc.sql("delete from map_genre_content where genre_id = :id").param("id", id).update();
        jdbc.sql("delete from map_genres where id = :id").param("id", id).update();
        changeRepository.save(new AdminChange(adminUsername, AdminChange.MAP_REMOVED, "Genre: " + genre.name(),
                String.join(", ", genre.links()), "", clock.instant()));
        load();
    }

    public Optional<Genre> find(String id) {
        return jdbc.sql("select * from map_genres where id = :id").param("id", id).query(this::genre).optional();
    }

    // ---- Helpers ----

    private record Checked(String family, int year, List<String> links, List<String> keywords) {
    }

    private Checked check(Form form, String self) {
        if (form.family() == null || !FAMILIES.contains(form.family())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pick the scene (color) it belongs to");
        }
        int now = Year.now(clock).getValue();
        if (form.year() == null || form.year() < 1960 || form.year() > now) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The year it appeared is between 1960 and now");
        }
        if (outOfTempo(form.bpmMin()) || outOfTempo(form.bpmMax())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The tempo is between 60 and 250 BPM");
        }
        if (form.bpmMin() != null && form.bpmMax() != null && form.bpmMin() > form.bpmMax()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The slowest tempo is above the fastest");
        }
        List<String> links = form.links() == null ? List.of() : List.copyOf(new LinkedHashSet<>(form.links()));
        if (links.isEmpty() || links.size() > MAX_LINKS || (self != null && links.contains(self)) || !links.stream().allMatch(mapGenres::isGenre)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Link it to 1 to " + MAX_LINKS + " genres of the map: the first is the one it grew out of");
        }
        List<String> keywords = new ArrayList<>();
        for (String k : form.keywords() == null ? List.<String>of() : form.keywords()) {
            String clean = k == null ? "" : k.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
            if (clean.isEmpty()) continue;
            if (clean.length() < 3 || clean.length() > 30 || clean.contains(",")) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Each stage word is 3 to 30 characters, without commas");
            }
            if (!keywords.contains(clean)) keywords.add(clean);
        }
        if (keywords.size() > MAX_KEYWORDS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At most " + MAX_KEYWORDS + " stage words");
        }
        return new Checked(form.family(), form.year(), links, keywords);
    }

    private static boolean outOfTempo(Integer bpm) {
        return bpm != null && (bpm < 60 || bpm > 250);
    }

    private static String name(String name) {
        String clean = name == null ? "" : name.strip().replaceAll("\\s+", " ");
        if (clean.length() < 3 || clean.length() > 40 || ArtistNames.slug(clean).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The name is 3 to 40 characters");
        }
        return clean;
    }

    private static int[] spot(int x, int y) {
        if (x < 0 || x > WORLD_WIDTH || y < 0 || y > WORLD_HEIGHT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "The spot is inside the galaxy: x 0 to " + WORLD_WIDTH + ", y 0 to " + WORLD_HEIGHT);
        }
        return new int[]{x, y};
    }

    // Around the middle of the linked stars (the first counts double: it grew out of it), turned for each one added
    private int[] position(List<String> links, int before) {
        double cx = 0;
        double cy = 0;
        double weight = 0;
        for (int i = 0; i < links.size(); i++) {
            MapGenres.Genre g = mapGenres.genre(links.get(i)).orElse(null);
            if (g == null || g.x() == null || g.y() == null) continue;
            double w = i == 0 ? 2 : 1;
            cx += g.x() * w;
            cy += g.y() * w;
            weight += w;
        }
        if (weight == 0) return new int[]{WORLD_WIDTH / 2, WORLD_HEIGHT / 2};
        double angle = before * GOLDEN_ANGLE + 0.6;
        int x = (int) Math.round(cx / weight + Math.cos(angle) * DISTANCE);
        int y = (int) Math.round(cy / weight + Math.sin(angle) * DISTANCE);
        return new int[]{Math.max(60, Math.min(WORLD_WIDTH - 60, x)), Math.max(60, Math.min(WORLD_HEIGHT - 60, y))};
    }

    private Genre genre(ResultSet rs, int row) throws SQLException {
        String keywords = rs.getString("keywords");
        return new Genre(rs.getString("id"), rs.getString("name"), rs.getString("family"), rs.getInt("year"),
                (Integer) rs.getObject("bpm_min"), (Integer) rs.getObject("bpm_max"),
                Arrays.asList(rs.getString("links").split(",")),
                keywords == null || keywords.isBlank() ? List.of() : Arrays.asList(keywords.split(",")),
                rs.getInt("x"), rs.getInt("y"));
    }
}
