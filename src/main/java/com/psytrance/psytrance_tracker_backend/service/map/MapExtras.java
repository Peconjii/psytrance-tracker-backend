package com.psytrance.psytrance_tracker_backend.service.map;

import com.psytrance.psytrance_tracker_backend.model.AdminChange;
import com.psytrance.psytrance_tracker_backend.repository.AdminChangeRepository;
import com.psytrance.psytrance_tracker_backend.util.ArtistNames;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.core.io.ClassPathResource;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Map;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Year;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.psytrance.psytrance_tracker_backend.util.Texts.blankToNull;

/**
 * Labels and festivals an admin adds to the map from the admin panel, so a new one needs no change to labels.json or
 * festivals.json and no deploy. The frontend puts them into the map data before the galaxy is drawn
 * ({@code GET /api/map/extras}); MapLabels and MapFestivals know them too, so their pages, claims and owners work
 * like any other's. A new marker sits near the stars of its genres.
 */
@Service
public class MapExtras {

    private static final int MAX_GENRES = 6;
    // How far from the middle of its genres' stars a new marker sits, so it doesn't cover a star
    private static final int LABEL_DISTANCE = 120;
    private static final int FESTIVAL_DISTANCE = 90;
    // Each new marker turns by the golden angle, so markers added around the same stars don't land on each other
    private static final double GOLDEN_ANGLE = Math.PI * (3 - Math.sqrt(5));
    // How much further out each next marker goes (times the square root of how many came before)
    private static final double SPIRAL_STEP = 28;
    // Room a new marker keeps from every festival, label and star of the map (the same as the frontend's spacing test)
    private static final int MIN_GAP = 80;
    // How many spots further along the spiral a new marker may try before it settles for the first one
    private static final int MAX_TRIES = 200;

    private final JdbcClient jdbc;
    private final MapGenres mapGenres;
    private final MapLabels mapLabels;
    private final MapFestivals mapFestivals;
    private final AdminChangeRepository changeRepository;
    private final Clock clock;
    // Where the festivals and labels of festivals.json and labels.json stand: new markers keep away from them too
    private final List<int[]> bundledSpots;

    public MapExtras(JdbcClient jdbc, MapGenres mapGenres, MapLabels mapLabels, MapFestivals mapFestivals,
                     AdminChangeRepository changeRepository, Clock clock, JsonMapper jsonMapper) {
        this.bundledSpots = bundledSpots(jsonMapper);
        this.jdbc = jdbc;
        this.mapGenres = mapGenres;
        this.mapLabels = mapLabels;
        this.mapFestivals = mapFestivals;
        this.changeRepository = changeRepository;
        this.clock = clock;
    }

    public record Label(String name, String country, Integer since, List<String> genres, int x, int y) {
    }

    public record Festival(String id, String name, String country, Integer since, String description,
                           List<String> genres, int x, int y) {
    }

    public record Extras(List<Label> labels, List<Festival> festivals) {
    }

    // Read once the database is ready, and again after each one added
    @EventListener(ApplicationReadyEvent.class)
    public void load() {
        Extras extras = all();
        mapLabels.useAddedLabels(extras.labels().stream().map(Label::name).collect(Collectors.toSet()));
        mapFestivals.useAddedFestivals(extras.festivals().stream().collect(Collectors.toMap(Festival::id, Festival::name)));
    }

    /** Everything admins added, oldest first */
    public Extras all() {
        List<Label> labels = jdbc.sql("select * from map_labels order by created_at").query(this::label).list();
        List<Festival> festivals = jdbc.sql("select * from map_festivals order by created_at").query(this::festival).list();
        return new Extras(labels, festivals);
    }

    @Transactional
    public Label addLabel(String name, String country, Integer since, List<String> genres, String adminUsername) {
        String clean = name(name);
        if (mapLabels.names().stream().anyMatch(label -> ArtistNames.slug(label).equals(ArtistNames.slug(clean)))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "There is a label called " + clean + " on the map already");
        }
        List<String> ids = genres(genres);
        int[] at = freeSpot(ids, count("map_labels"), LABEL_DISTANCE, 0);
        jdbc.sql("""
                        insert into map_labels (name, country, since, genres, x, y, created_by, created_at)
                        values (:name, :country, :since, :genres, :x, :y, :by, :at)
                        """)
                .param("name", clean).param("country", blankToNull(country)).param("since", since(since))
                .param("genres", String.join(",", ids)).param("x", at[0]).param("y", at[1])
                .param("by", adminUsername).param("at", Timestamp.from(clock.instant())).update();
        changeRepository.save(new AdminChange(adminUsername, AdminChange.MAP_ADDED, "Label: " + clean, "", String.join(", ", ids),
                clock.instant()));
        load();
        return new Label(clean, blankToNull(country), since(since), ids, at[0], at[1]);
    }

    @Transactional
    public Festival addFestival(String name, String country, Integer since, String description, List<String> genres,
                                String adminUsername) {
        String clean = name(name);
        if (mapFestivals.idOf(clean).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "There is a festival called " + clean + " on the map already");
        }
        String id = ArtistNames.slug(clean);
        for (int n = 2; mapFestivals.name(id).isPresent(); n++) {
            id = ArtistNames.slug(clean) + "-" + n;
        }
        List<String> ids = genres(genres);
        // Turned half a step from the labels, so a label and a festival of the same genres don't meet
        int[] at = freeSpot(ids, count("map_festivals"), FESTIVAL_DISTANCE, GOLDEN_ANGLE / 2);
        String text = blankToNull(description);
        if (text != null && text.length() > 1000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The description is 1000 characters at most");
        }
        jdbc.sql("""
                        insert into map_festivals (id, name, country, since, description, genres, x, y, created_by, created_at)
                        values (:id, :name, :country, :since, :description, :genres, :x, :y, :by, :at)
                        """)
                .param("id", id).param("name", clean).param("country", blankToNull(country)).param("since", since(since))
                .param("description", text).param("genres", String.join(",", ids)).param("x", at[0]).param("y", at[1])
                .param("by", adminUsername).param("at", Timestamp.from(clock.instant())).update();
        changeRepository.save(new AdminChange(adminUsername, AdminChange.MAP_ADDED, "Festival: " + clean, "", String.join(", ", ids),
                clock.instant()));
        load();
        return new Festival(id, clean, blankToNull(country), since(since), text, ids, at[0], at[1]);
    }

    /**
     * Takes a label an admin added off the map again (a mistake, a duplicate), with what hangs on it: its owner,
     * followers, page, crew changes and requests. The labels of labels.json can't be taken off here.
     */
    @Transactional
    public void removeLabel(String name, String adminUsername) {
        Label label = all().labels().stream().filter(l -> l.name().equals(name)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No label called " + name + " was added from the panel (the map file's labels stay)"));
        for (String table : List.of("label_owners", "label_follows", "label_crew", "label_requests", "label_profiles")) {
            jdbc.sql("delete from " + table + " where label_name = :name").param("name", name).update();
        }
        jdbc.sql("delete from page_claims where kind = 'LABEL' and target = :name").param("name", name).update();
        jdbc.sql("delete from map_labels where name = :name").param("name", name).update();
        changeRepository.save(new AdminChange(adminUsername, AdminChange.MAP_REMOVED, "Label: " + name,
                String.join(", ", label.genres()), "", clock.instant()));
        load();
    }

    /** The same for a festival an admin added: its owner, page, editions and claims go with it */
    @Transactional
    public void removeFestival(String id, String adminUsername) {
        Festival festival = all().festivals().stream().filter(f -> f.id().equals(id)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No festival " + id + " was added from the panel (the map file's festivals stay)"));
        for (String table : List.of("festival_owners", "festival_profiles", "festival_editions")) {
            jdbc.sql("delete from " + table + " where festival_id = :id").param("id", id).update();
        }
        jdbc.sql("delete from page_claims where kind = 'FESTIVAL' and target = :id").param("id", id).update();
        jdbc.sql("delete from map_festivals where id = :id").param("id", id).update();
        changeRepository.save(new AdminChange(adminUsername, AdminChange.MAP_REMOVED, "Festival: " + festival.name(),
                String.join(", ", festival.genres()), "", clock.instant()));
        load();
    }

    // ---- Helpers ----

    /**
     * The first spot along the marker's spiral that keeps {@link #MIN_GAP} from every festival, label and star on the
     * map, so markers never stand on each other. If the stars are so crowded that none is free, the first spot.
     */
    public int[] freeSpot(List<String> genreIds, int before, int distance, double turn) {
        List<int[]> taken = new ArrayList<>(bundledSpots);
        Extras added = all();
        added.labels().forEach(l -> taken.add(new int[]{l.x(), l.y()}));
        added.festivals().forEach(f -> taken.add(new int[]{f.x(), f.y()}));
        mapGenres.all().stream().filter(g -> g.x() != null && g.y() != null).forEach(g -> taken.add(new int[]{g.x(), g.y()}));
        for (int step = 0; step < MAX_TRIES; step++) {
            int[] at = position(genreIds, before + step, distance, turn);
            if (taken.stream().allMatch(t -> Math.hypot(t[0] - at[0], t[1] - at[1]) >= MIN_GAP)) {
                return at;
            }
        }
        return position(genreIds, before, distance, turn);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Spot(int x, int y) {
    }

    private static List<int[]> bundledSpots(JsonMapper jsonMapper) {
        List<int[]> spots = new ArrayList<>();
        try (InputStream festivals = new ClassPathResource("map/festivals.json").getInputStream();
             InputStream labels = new ClassPathResource("map/labels.json").getInputStream()) {
            List<Spot> fs = jsonMapper.readValue(festivals, jsonMapper.getTypeFactory().constructCollectionType(List.class, Spot.class));
            Map<String, Spot> ls = jsonMapper.readValue(labels,
                    jsonMapper.getTypeFactory().constructMapType(Map.class, String.class, Spot.class));
            fs.forEach(f -> spots.add(new int[]{f.x(), f.y()}));
            ls.values().forEach(l -> spots.add(new int[]{l.x(), l.y()}));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read the map's festivals and labels", e);
        }
        return List.copyOf(spots);
    }

    // Around the middle of the genres' stars, turned by the golden angle for each marker added before
    private int[] position(List<String> genreIds, int before, int distance, double turn) {
        List<MapGenres.Genre> stars = genreIds.stream().map(mapGenres::genre).flatMap(Optional::stream)
                .filter(genre -> genre.x() != null && genre.y() != null).toList();
        double cx = stars.stream().mapToInt(MapGenres.Genre::x).average().orElse(0);
        double cy = stars.stream().mapToInt(MapGenres.Genre::y).average().orElse(0);
        // A sunflower spiral: each marker turns by the golden angle and sits a little further out, so however many
        // are added around the same stars, they never land on each other
        double angle = before * GOLDEN_ANGLE + turn;
        double radius = distance + SPIRAL_STEP * Math.sqrt(before);
        return new int[]{(int) Math.round(cx + Math.cos(angle) * radius), (int) Math.round(cy + Math.sin(angle) * radius)};
    }

    private static String name(String name) {
        String clean = name == null ? "" : name.strip().replaceAll("\\s+", " ");
        if (clean.length() < 2 || clean.length() > 60 || ArtistNames.slug(clean).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The name is 2 to 60 characters");
        }
        return clean;
    }

    private List<String> genres(List<String> genres) {
        List<String> ids = genres == null ? List.of() : List.copyOf(new LinkedHashSet<>(genres));
        if (ids.isEmpty() || ids.size() > MAX_GENRES || !ids.stream().allMatch(mapGenres::isGenre)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Pick 1 to " + MAX_GENRES + " genres of the map: they decide where it sits");
        }
        return ids;
    }

    private Integer since(Integer since) {
        if (since != null && (since < 1960 || since > Year.now(clock).getValue())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The year it started is between 1960 and now");
        }
        return since;
    }

    private int count(String table) {
        return jdbc.sql("select count(*) from " + table).query(Integer.class).single();
    }

    private Label label(ResultSet rs, int row) throws SQLException {
        return new Label(rs.getString("name"), rs.getString("country"), (Integer) rs.getObject("since"),
                Arrays.asList(rs.getString("genres").split(",")), rs.getInt("x"), rs.getInt("y"));
    }

    private Festival festival(ResultSet rs, int row) throws SQLException {
        return new Festival(rs.getString("id"), rs.getString("name"), rs.getString("country"), (Integer) rs.getObject("since"),
                rs.getString("description"), Arrays.asList(rs.getString("genres").split(",")), rs.getInt("x"), rs.getInt("y"));
    }

}
