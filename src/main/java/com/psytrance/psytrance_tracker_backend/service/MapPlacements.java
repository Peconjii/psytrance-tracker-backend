package com.psytrance.psytrance_tracker_backend.service;

import com.psytrance.psytrance_tracker_backend.dto.AdminArtistDto;
import com.psytrance.psytrance_tracker_backend.model.AdminChange;
import com.psytrance.psytrance_tracker_backend.model.Artist;
import com.psytrance.psytrance_tracker_backend.model.LineupScan;
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
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

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
    private final Clock clock;

    public MapPlacements(MapArtistGenreRepository placementRepository,
                         AdminChangeRepository changeRepository,
                         ArtistRepository artistRepository,
                         ArtistEventRepository artistEventRepository,
                         LineupScanRepository lineupScanRepository,
                         MapGenres mapGenres,
                         Clock clock) {
        this.placementRepository = placementRepository;
        this.changeRepository = changeRepository;
        this.artistRepository = artistRepository;
        this.artistEventRepository = artistEventRepository;
        this.lineupScanRepository = lineupScanRepository;
        this.mapGenres = mapGenres;
        this.clock = clock;
    }

    // First start: copy the placements of genres.json into the table; from then on the table is the truth
    @PostConstruct
    void load() {
        if (placementRepository.count() == 0) {
            List<MapArtistGenre> seed = new ArrayList<>();
            for (MapGenres.Genre genre : mapGenres.all()) {
                for (String artist : new LinkedHashSet<>(genre.artists())) {
                    seed.add(new MapArtistGenre(artist, genre.id()));
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

    /**
     * Artists for the admin panel: those on the map and those found in line-ups whose name contains the
     * search (ignoring case, accents and symbols), with the genres each one is placed in.
     */
    public List<AdminArtistDto> search(String search) {
        String part = ArtistNames.slug(search);
        Map<String, AdminArtistDto> bySlug = new LinkedHashMap<>();
        for (MapArtistGenre row : placementRepository.findAllByOrderByIdAsc()) {
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

    /**
     * Places an artist in exactly these genres (none takes them off the map). The name of an artist already on
     * the map stays as the map writes it. Returns the artist as the panel shows it.
     */
    @Transactional
    public AdminArtistDto setGenres(String name, List<String> genreIds, String adminUsername) {
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

        List<MapArtistGenre> current = placementRepository.findAllByOrderByIdAsc().stream()
                .filter(row -> ArtistNames.slug(row.getArtistName()).equals(slug))
                .toList();
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

        if (!removed.isEmpty() || !added.isEmpty()) {
            changeRepository.save(new AdminChange(adminUsername, AdminChange.ARTIST_GENRES, mapName,
                    String.join(", ", before), String.join(", ", wanted), clock.instant()));
            mapGenres.usePlacements(placements());
            rereadEventsWith(slug);
        }
        return new AdminArtistDto(mapName, slug, wanted, !wanted.isEmpty());
    }

    // The artist's events get their genres worked out again: marked as read long ago, the artist index
    // reads them first in its next batches
    private void rereadEventsWith(String slug) {
        artistRepository.findBySlug(slug).ifPresent(artist -> {
            List<Long> eventIds = artistEventRepository.findEventIdsByArtistId(artist.getId());
            List<LineupScan> scans = lineupScanRepository.findAllById(eventIds);
            scans.forEach(scan -> scan.setScannedAt(Instant.EPOCH));
            lineupScanRepository.saveAll(scans);
        });
    }
}
