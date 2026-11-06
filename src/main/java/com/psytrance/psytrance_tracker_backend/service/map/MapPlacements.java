package com.psytrance.psytrance_tracker_backend.service.map;

import com.psytrance.psytrance_tracker_backend.service.artists.ArtistCleanup;
import com.psytrance.psytrance_tracker_backend.service.events.EventGenreAdmin;

import com.psytrance.psytrance_tracker_backend.dto.AdminArtistDto;
import com.psytrance.psytrance_tracker_backend.dto.AdminChangeDto;
import com.psytrance.psytrance_tracker_backend.model.AdminChange;
import com.psytrance.psytrance_tracker_backend.model.Artist;
import com.psytrance.psytrance_tracker_backend.model.MapArtistGenre;
import com.psytrance.psytrance_tracker_backend.repository.AdminChangeRepository;
import com.psytrance.psytrance_tracker_backend.repository.ArtistEventRepository;
import com.psytrance.psytrance_tracker_backend.repository.ArtistRepository;
import com.psytrance.psytrance_tracker_backend.repository.LineupScanRepository;
import com.psytrance.psytrance_tracker_backend.repository.MapArtistGenreRepository;
import com.psytrance.psytrance_tracker_backend.util.ArtistNames;
import jakarta.annotation.PostConstruct;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.Comparator;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Which artists orbit which genre on the map. The placements live in the database, so admins can move an
 * artist from the admin panel when feedback says they're in the wrong genre; genres.json only fills the table
 * the first time. Every change is logged in admin_changes and applies at once: the map reads the placements
 * from the API, and events with that artist are queued to have their genres worked out again.
 */
@Service
public class MapPlacements {

    private static final int MAX_SEARCH_RESULTS = 30;

    private final MapArtistGenreRepository placementRepository;
    private final AdminChangeRepository changeRepository;
    private final ArtistRepository artistRepository;
    private final ArtistEventRepository artistEventRepository;
    private final LineupScanRepository lineupScanRepository;
    private final MapGenres mapGenres;
    private final EventGenreAdmin eventGenreAdmin;
    private final ArtistCleanup artistCleanup;
    private final GalaxyContent galaxyContent;
    private final StationDetails stationDetails;
    private final Clock clock;

    public MapPlacements(MapArtistGenreRepository placementRepository,
                         AdminChangeRepository changeRepository,
                         ArtistRepository artistRepository,
                         ArtistEventRepository artistEventRepository,
                         LineupScanRepository lineupScanRepository,
                         MapGenres mapGenres,
                         EventGenreAdmin eventGenreAdmin,
                         ArtistCleanup artistCleanup,
                         GalaxyContent galaxyContent,
                         Clock clock, StationDetails stationDetails) {
        this.stationDetails = stationDetails;
        this.placementRepository = placementRepository;
        this.changeRepository = changeRepository;
        this.artistRepository = artistRepository;
        this.artistEventRepository = artistEventRepository;
        this.lineupScanRepository = lineupScanRepository;
        this.mapGenres = mapGenres;
        this.eventGenreAdmin = eventGenreAdmin;
        this.artistCleanup = artistCleanup;
        this.galaxyContent = galaxyContent;
        this.clock = clock;
    }

    // First start: copy the placements of genres.json into the table; from then on the table is the truth
    @PostConstruct
    void load() {
        if (placementRepository.count() == 0) {
            List<MapArtistGenre> seed = new ArrayList<>();
            Set<String> withMain = new HashSet<>();
            for (MapGenres.Genre genre : mapGenres.all()) {
                for (String artist : new LinkedHashSet<>(genre.artists())) {
                    MapArtistGenre row = new MapArtistGenre(artist, genre.id());
                    row.setMain(withMain.add(artist));
                    seed.add(row);
                }
            }
            placementRepository.saveAll(seed);
        }
        mapGenres.usePlacements(placements());
    }

    /** {@code genreId -> artist names}, in the order satellites orbit each star */
    public Map<String, List<String>> placements() {
        Map<String, List<String>> byGenre = new LinkedHashMap<>();
        for (MapArtistGenre row : placementRepository.findAllByOrderByIdAsc()) {
            byGenre.computeIfAbsent(row.getGenreId(), id -> new ArrayList<>()).add(row.getArtistName());
        }
        return byGenre;
    }

    /** {@code artist name -> main genre}: the one star each artist orbits on the galaxy */
    public Map<String, String> mainGenres() {
        Map<String, String> main = new LinkedHashMap<>();
        for (MapArtistGenre row : placementRepository.findAllByOrderByIdAsc()) {
            if (row.isMain()) main.put(row.getArtistName(), row.getGenreId());
        }
        return main;
    }

    // An artist's rows with the main genre first, as the panel and the history show them
    private List<MapArtistGenre> rowsOf(String slug) {
        return placementRepository.findAllByOrderByIdAsc().stream()
                .filter(row -> ArtistNames.slug(row.getArtistName()).equals(slug))
                .sorted(Comparator.comparing((MapArtistGenre row) -> !row.isMain()))
                .toList();
    }

    /**
     * Artists for the admin panel: those on the map and those found in line-ups whose name contains the
     * search (ignoring case, accents and symbols), with the genres each one is placed in.
     */
    public List<AdminArtistDto> search(String search) {
        String part = ArtistNames.slug(search);
        Map<String, AdminArtistDto> bySlug = new LinkedHashMap<>();
        for (MapArtistGenre row : placementRepository.findAllByOrderByIdAsc().stream()
                .sorted(Comparator.comparing((MapArtistGenre r) -> !r.isMain())).toList()) {
            String slug = ArtistNames.slug(row.getArtistName());
            if (slug.contains(part)) {
                bySlug.computeIfAbsent(slug, s -> new AdminArtistDto(row.getArtistName(), s, new ArrayList<>(), true))
                        .genres().add(row.getGenreId());
            }
        }
        for (Artist artist : artistRepository.findTop30BySlugContainingOrderByNameAsc(part)) {
            bySlug.putIfAbsent(artist.getSlug(), new AdminArtistDto(artist.getName(), artist.getSlug(), List.of(), false));
        }
        return bySlug.values().stream()
                .sorted((a, b) -> a.name().compareToIgnoreCase(b.name()))
                .limit(MAX_SEARCH_RESULTS)
                .toList();
    }

    /** The artist's genres, main one first; empty when they are not on the map */
    public List<String> genresOf(String name) {
        return rowsOf(ArtistNames.slug(name)).stream().map(MapArtistGenre::getGenreId).toList();
    }

    /**
     * Places an artist in exactly these genres (none takes them off the map); the first is their main genre, the
     * one star they orbit. The name of an artist already on
     * the map stays as the map writes it. Returns the artist as the panel shows it.
     */
    @Transactional
    public AdminArtistDto setGenres(String name, List<String> genreIds, String adminUsername) {
        return setGenres(name, genreIds, adminUsername, null);
    }

    private AdminArtistDto setGenres(String name, List<String> genreIds, String adminUsername, Long undoOf) {
        String slug = ArtistNames.slug(name);
        if (slug.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Artist name is empty");
        }
        List<String> wanted = List.copyOf(new LinkedHashSet<>(genreIds));
        for (String genreId : wanted) {
            if (!mapGenres.isGenre(genreId)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown genre: " + genreId);
            }
        }

        List<MapArtistGenre> current = rowsOf(slug);
        String mapName = current.isEmpty() ? name.strip() : current.get(0).getArtistName();
        List<String> before = current.stream().map(MapArtistGenre::getGenreId).toList();

        // Keep the rows of genres that stay, so the artist keeps their place in those orbits
        List<MapArtistGenre> removed = current.stream().filter(row -> !wanted.contains(row.getGenreId())).toList();
        placementRepository.deleteAll(removed);
        placementRepository.flush();
        List<MapArtistGenre> added = wanted.stream()
                .filter(genreId -> !before.contains(genreId))
                .map(genreId -> new MapArtistGenre(mapName, genreId))
                .toList();
        placementRepository.saveAll(added);
        // The first wanted genre is the main one
        boolean mainChanged = false;
        if (!wanted.isEmpty()) {
            List<MapArtistGenre> rows = new ArrayList<>(current.stream().filter(row -> wanted.contains(row.getGenreId())).toList());
            rows.addAll(added);
            for (MapArtistGenre row : rows) {
                boolean main = row.getGenreId().equals(wanted.get(0));
                if (row.isMain() != main) {
                    row.setMain(main);
                    mainChanged = true;
                }
            }
            placementRepository.saveAll(rows);
        }

        if (!removed.isEmpty() || !added.isEmpty() || (mainChanged && !before.isEmpty() && !before.get(0).equals(wanted.get(0)))) {
            changeRepository.save(new AdminChange(adminUsername, AdminChange.ARTIST_GENRES, mapName,
                    String.join(", ", before), String.join(", ", wanted), clock.instant(), undoOf));
            mapGenres.usePlacements(placements());
            rereadEventsWith(slug);
        }
        return new AdminArtistDto(mapName, slug, wanted, !wanted.isEmpty());
    }

    /** The admin history, newest first, each change with the change that undid it (if any) */
    public List<AdminChangeDto> history() {
        List<AdminChange> changes = changeRepository.findTop200ByOrderByCreatedAtDescIdDesc();
        Map<Long, Long> undoneBy = new HashMap<>();
        for (AdminChange change : changes) {
            if (change.getUndoOf() != null) {
                undoneBy.put(change.getUndoOf(), change.getId());
            }
        }
        return changes.stream().map(change -> AdminChangeDto.from(change, undoneBy.get(change.getId()))).toList();
    }

    /**
     * Puts the genres an artist had before a change back, as a new change of its own. Refused when the change
     * was undone already, or when the artist was changed again since: then the newer change must be undone first,
     * so an undo never silently throws away a later fix.
     */
    @Transactional
    public AdminChangeDto undo(long changeId, String adminUsername) {
        AdminChange change = changeRepository.findById(changeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Change not found"));
        if (AdminChange.STATION_DETAILS.equals(change.getKind())) {
            if (changeRepository.existsByUndoOf(changeId)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "This change was undone already");
            }
            stationDetails.undo(change, adminUsername);
            return AdminChangeDto.from(change, changeRepository.findFirstByUndoOf(changeId).map(AdminChange::getId).orElse(null));
        }
        if (AdminChange.GENRE_CONTENT.equals(change.getKind())) {
            if (changeRepository.existsByUndoOf(changeId)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "This change was undone already");
            }
            galaxyContent.undo(change, adminUsername);
            return AdminChangeDto.from(change, changeRepository.findFirstByUndoOf(changeId).map(AdminChange::getId).orElse(null));
        }
        boolean event = AdminChange.EVENT_GENRES.equals(change.getKind());
        boolean hidden = AdminChange.ARTIST_HIDDEN.equals(change.getKind());
        if (!event && !hidden && !AdminChange.ARTIST_GENRES.equals(change.getKind())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This change can't be undone here");
        }
        if (changeRepository.existsByUndoOf(changeId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This change was undone already");
        }
        String now = event ? eventGenreAdmin.manualValue(EventGenreAdmin.eventIdOf(change.getTarget()))
                : hidden ? artistCleanup.hiddenValue(change.getTarget())
                : artistGenres(change.getTarget());
        if (!now.equals(nullToEmpty(change.getAfterValue()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    change.getTarget() + " was changed again since. Undo the newer change first.");
        }
        List<String> before = nullToEmpty(change.getBeforeValue()).isEmpty()
                ? List.of()
                : List.of(change.getBeforeValue().split(",\\s*"));
        if (hidden) {
            artistCleanup.setHidden(change.getTarget(), ArtistCleanup.HIDDEN.equals(change.getBeforeValue()), adminUsername, changeId);
        } else if (event) {
            eventGenreAdmin.setGenres(EventGenreAdmin.eventIdOf(change.getTarget()), before, adminUsername, changeId);
        } else {
            setGenres(change.getTarget(), before, adminUsername, changeId);
        }
        // Not looked up in history(): that only holds the newest changes, and this one can be older
        return AdminChangeDto.from(change, changeRepository.findFirstByUndoOf(changeId).map(AdminChange::getId).orElse(null));
    }

    // The genres an artist is placed in now, as the history writes them: "forest, twilight"
    private String artistGenres(String name) {
        return rowsOf(ArtistNames.slug(name)).stream()
                .map(MapArtistGenre::getGenreId)
                .collect(Collectors.joining(", "));
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    // The artist's events get their genres worked out again: marked as read long ago, the artist index
    // reads them first in its next batches
    private void rereadEventsWith(String slug) {
        artistRepository.findBySlug(slug).ifPresent(artist -> {
            List<Long> eventIds = artistEventRepository.findEventIdsByArtistId(artist.getId());
            lineupScanRepository.rereadSoon(eventIds);
        });
    }
}
