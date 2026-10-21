package com.psytrance.psytrance_tracker_backend.service;

import com.psytrance.psytrance_tracker_backend.model.AdminChange;
import com.psytrance.psytrance_tracker_backend.model.Artist;
import com.psytrance.psytrance_tracker_backend.model.ArtistAlias;
import com.psytrance.psytrance_tracker_backend.model.ArtistBlock;
import com.psytrance.psytrance_tracker_backend.model.ArtistEvent;
import com.psytrance.psytrance_tracker_backend.repository.AdminChangeRepository;
import com.psytrance.psytrance_tracker_backend.repository.ArtistAliasRepository;
import com.psytrance.psytrance_tracker_backend.repository.ArtistBlockRepository;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Cleaning up the artists found in line-ups: words that aren't artists ("Lounge", "Coming Soon") are hidden and
 * skipped from then on, and two spellings of one artist ("AJJA", "Ajja Experience") are merged into one, so later
 * line-ups with either spelling count for the same artist. Both are logged in admin_changes.
 */
@Service
public class ArtistCleanup {

    static final String HIDDEN = "not an artist";
    static final String SHOWN = "artist";

    private final ArtistRepository artistRepository;
    private final ArtistEventRepository artistEventRepository;
    private final ArtistBlockRepository blockRepository;
    private final ArtistAliasRepository aliasRepository;
    private final MapArtistGenreRepository placementRepository;
    private final LineupScanRepository lineupScanRepository;
    private final AdminChangeRepository changeRepository;
    private final MapGenres mapGenres;
    private final Clock clock;

    public ArtistCleanup(ArtistRepository artistRepository, ArtistEventRepository artistEventRepository,
                         ArtistBlockRepository blockRepository, ArtistAliasRepository aliasRepository,
                         MapArtistGenreRepository placementRepository, LineupScanRepository lineupScanRepository,
                         AdminChangeRepository changeRepository, MapGenres mapGenres, Clock clock) {
        this.artistRepository = artistRepository;
        this.artistEventRepository = artistEventRepository;
        this.blockRepository = blockRepository;
        this.aliasRepository = aliasRepository;
        this.placementRepository = placementRepository;
        this.lineupScanRepository = lineupScanRepository;
        this.changeRepository = changeRepository;
        this.mapGenres = mapGenres;
        this.clock = clock;
    }

    // Event genres count a merged spelling as the artist it was merged into ("Ajja Experience" plays what Ajja plays)
    @PostConstruct
    void useAliases() {
        List<ArtistAlias> aliases = aliasRepository.findAll();
        Map<Long, String> slugById = artistRepository.findAllById(aliases.stream().map(ArtistAlias::getArtistId).toList())
                .stream().collect(Collectors.toMap(Artist::getId, Artist::getSlug));
        mapGenres.useAliases(aliases.stream()
                .filter(alias -> slugById.containsKey(alias.getArtistId()))
                .collect(Collectors.toMap(ArtistAlias::getAliasSlug, alias -> slugById.get(alias.getArtistId()))));
    }

    @Transactional
    public void setHidden(String name, boolean hidden, String adminUsername) {
        setHidden(name, hidden, adminUsername, null);
    }

    /**
     * Hidden: the word is taken out of every line-up and skipped when line-ups are read again. Shown again: it comes
     * back as line-ups are read again (each within two days). Artists placed on the map can't be hidden.
     */
    @Transactional
    void setHidden(String name, boolean hidden, String adminUsername, Long undoOf) {
        String slug = ArtistNames.slug(name);
        if (slug.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Artist name is empty");
        }
        if (blockRepository.existsById(slug) == hidden) {
            return;
        }
        if (hidden) {
            if (onMap(slug)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, name.strip() + " is on the map. Take them off the map first.");
            }
            String shownName = artistRepository.findBySlug(slug).map(Artist::getName).orElse(name.strip());
            blockRepository.save(new ArtistBlock(slug, shownName));
            artistRepository.findBySlug(slug).ifPresent(this::remove);
            log(AdminChange.ARTIST_HIDDEN, shownName, SHOWN, HIDDEN, adminUsername, undoOf);
        } else {
            String shownName = blockRepository.findById(slug).map(ArtistBlock::getName).orElse(name.strip());
            blockRepository.deleteById(slug);
            log(AdminChange.ARTIST_HIDDEN, shownName, HIDDEN, SHOWN, adminUsername, undoOf);
        }
    }

    /** Whether the word is hidden now, as the history writes it */
    String hiddenValue(String name) {
        return blockRepository.existsById(ArtistNames.slug(name)) ? HIDDEN : SHOWN;
    }

    /**
     * Merges the artist {@code from} into {@code into}: their events become {@code into}'s, {@code from} is gone, and
     * later line-ups that write {@code from} count for {@code into}. Merges can't be undone, so both must be artists
     * from the line-ups, and {@code from} must not be on the map.
     */
    @Transactional
    public void merge(String fromName, String intoName, String adminUsername) {
        String fromSlug = ArtistNames.slug(fromName);
        String intoSlug = ArtistNames.slug(intoName);
        if (fromSlug.isEmpty() || intoSlug.isEmpty() || fromSlug.equals(intoSlug)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pick two different artists");
        }
        Artist from = artistRepository.findBySlug(fromSlug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No artist called " + fromName.strip()));
        Artist into = artistRepository.findBySlug(intoSlug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No artist called " + intoName.strip()));
        if (onMap(fromSlug)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    from.getName() + " is on the map. Merge the other way round, or take them off the map first.");
        }

        Set<Long> intoEvents = new HashSet<>(artistEventRepository.findEventIdsByArtistId(into.getId()));
        List<Long> fromEvents = artistEventRepository.findEventIdsByArtistId(from.getId());
        for (Long eventId : fromEvents) {
            if (intoEvents.add(eventId)) {
                artistEventRepository.save(new ArtistEvent(into, eventId));
            }
        }
        for (ArtistAlias alias : aliasRepository.findByArtistId(from.getId())) {
            alias.setArtistId(into.getId());
            aliasRepository.save(alias);
        }
        artistEventRepository.deleteByArtistId(from.getId());
        artistRepository.delete(from);
        artistRepository.flush();
        aliasRepository.save(new ArtistAlias(fromSlug, into.getId()));
        useAliases();
        lineupScanRepository.rereadSoon(fromEvents);
        log(AdminChange.ARTIST_MERGED, from.getName(), from.getName(), into.getName(), adminUsername, null);
    }

    private boolean onMap(String slug) {
        return placementRepository.findAllByOrderByIdAsc().stream()
                .anyMatch(row -> ArtistNames.slug(row.getArtistName()).equals(slug));
    }

    // Takes the artist out of every line-up; its events get their genres worked out again soon
    private void remove(Artist artist) {
        List<Long> eventIds = artistEventRepository.findEventIdsByArtistId(artist.getId());
        artistEventRepository.deleteByArtistId(artist.getId());
        aliasRepository.deleteAll(aliasRepository.findByArtistId(artist.getId()));
        artistRepository.delete(artist);
        useAliases();
        lineupScanRepository.rereadSoon(eventIds);
    }

    private void log(String kind, String target, String before, String after, String adminUsername, Long undoOf) {
        changeRepository.save(new AdminChange(adminUsername, kind, target, before, after, clock.instant(), undoOf));
    }
}
