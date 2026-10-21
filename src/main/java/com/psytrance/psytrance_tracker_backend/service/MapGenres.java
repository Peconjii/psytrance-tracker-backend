package com.psytrance.psytrance_tracker_backend.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.psytrance.psytrance_tracker_backend.util.ArtistNames;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * The genres of Map of Psy ({@code map/genres.json}, copied from the map-of-psy repo) and two ways to
 * recognise them in Goabase data: the artists placed in each genre, and the words organizers use
 * for genres in stage headers ("DARKPSY / FOREST STAGE") and Goabase keywords ("Fullon", "Hi-Tech").
 */
@Component
public class MapGenres {

    private static final String RESOURCE = "map/genres.json";

    // Phrase -> genre id, checked longest phrase first so "dark prog" wins over "dark" and "prog".
    // Words that don't point to one genre ("psytrance", "psychedelic", "techno", "trance") are left out.
    private static final Map<String, String> ALIASES = Map.ofEntries(
            Map.entry("goa", "goa"), Map.entry("goatrance", "goa"), Map.entry("goa trance", "goa"),
            Map.entry("oldschool", "oldschool"), Map.entry("old school", "oldschool"),
            Map.entry("neo goa", "neogoa"), Map.entry("neogoa", "neogoa"),
            Map.entry("fullon", "fullon"), Map.entry("full on", "fullon"),
            Map.entry("morning full on", "morning"), Map.entry("morning fullon", "morning"),
            Map.entry("morning trance", "morning"),
            Map.entry("night full on", "night"), Map.entry("night fullon", "night"),
            Map.entry("progressive", "prog"), Map.entry("progressive trance", "prog"),
            Map.entry("progressive psy", "prog"), Map.entry("progressive psytrance", "prog"), Map.entry("prog", "prog"),
            Map.entry("offbeat", "offbeat"), Map.entry("offbeat prog", "offbeat"),
            Map.entry("zenonesque", "zenon"),
            Map.entry("psytechno", "psytechno"), Map.entry("psy techno", "psytechno"),
            Map.entry("dark prog", "darkprog"), Map.entry("dark progressive", "darkprog"), Map.entry("darkprog", "darkprog"),
            Map.entry("twilight", "twilight"),
            Map.entry("dark", "dark"), Map.entry("darkpsy", "dark"), Map.entry("dark psy", "dark"),
            Map.entry("forest", "forest"),
            Map.entry("hitech", "hitech"), Map.entry("hi tech", "hitech"),
            Map.entry("psycore", "psycore"),
            Map.entry("suomi", "suomi"), Map.entry("suomisaundi", "suomi"),
            Map.entry("chill", "psybient"), Map.entry("chillout", "psybient"), Map.entry("chill out", "psybient"),
            Map.entry("psychill", "psybient"), Map.entry("psybient", "psybient"), Map.entry("ambient", "psybient"),
            Map.entry("downtempo", "psybient"), Map.entry("downbeat", "psybient"),
            Map.entry("psydub", "psydub"), Map.entry("psy dub", "psydub"),
            Map.entry("psybreaks", "psybreaks"), Map.entry("psy breaks", "psybreaks"));

    private static final List<String> PHRASES_LONGEST_FIRST = ALIASES.keySet().stream()
            .sorted(Comparator.comparingInt(String::length).reversed())
            .toList();

    // Everything that isn't a letter or digit becomes a space: "Hi-Tech" -> "hi tech", "GOA//PSY" -> "goa psy"
    private static final Pattern SEPARATORS = Pattern.compile("[^\\p{L}\\p{N}]+");

    private final List<Genre> genres;
    // Replaced as a whole when admins move artists (see MapPlacements); volatile so the artist index's
    // background thread sees the new placements right away
    private volatile Map<String, List<String>> genreIdsByArtistSlug;
    // Merged spellings ("ajja-experience" -> "ajja"), see ArtistCleanup
    private volatile Map<String, String> artistSlugByAlias = Map.of();

    @Autowired
    public MapGenres(JsonMapper jsonMapper) {
        this(read(jsonMapper));
    }

    // For tests: a small hand-made map
    MapGenres(List<Genre> genres) {
        this.genres = List.copyOf(genres);
        Map<String, List<String>> artistsByGenre = new HashMap<>();
        for (Genre genre : genres) {
            artistsByGenre.put(genre.id(), genre.artists());
        }
        usePlacements(artistsByGenre);
    }

    /** The genres of the map as genres.json has them; their artist lists are the starting placements only. */
    public List<Genre> all() {
        return genres;
    }

    /** Places artists in genres ({@code genreId -> artist names}) instead of the placements used so far. */
    public void usePlacements(Map<String, List<String>> artistsByGenre) {
        Map<String, List<String>> byArtist = new HashMap<>();
        // In the map's genre order, so an artist's first genre is the same everywhere
        for (Genre genre : genres) {
            for (String artist : artistsByGenre.getOrDefault(genre.id(), List.of())) {
                byArtist.computeIfAbsent(ArtistNames.slug(artist), slug -> new ArrayList<>()).add(genre.id());
            }
        }
        this.genreIdsByArtistSlug = byArtist;
    }

    public boolean isGenre(String genreId) {
        return genres.stream().anyMatch(genre -> genre.id().equals(genreId));
    }

    /** Spellings admins merged into another artist ({@code alias slug -> artist slug}), instead of the ones used so far. */
    public void useAliases(Map<String, String> artistSlugByAlias) {
        this.artistSlugByAlias = Map.copyOf(artistSlugByAlias);
    }

    /**
     * The genres an artist is placed in on the map, matched by slug, also when the line-up writes a spelling that was
     * merged into a map artist; empty when the artist isn't on the map.
     */
    public List<String> genresOfArtist(String artistName) {
        String slug = ArtistNames.slug(artistName);
        List<String> genreIds = genreIdsByArtistSlug.get(slug);
        if (genreIds == null && artistSlugByAlias.containsKey(slug)) {
            genreIds = genreIdsByArtistSlug.get(artistSlugByAlias.get(slug));
        }
        return genreIds == null ? List.of() : genreIds;
    }

    /**
     * Genre ids named in a piece of text, in the order they appear: "DARKPSY / FOREST STAGE" -> [dark, forest].
     * Only whole words count, so "Goatee" or "Darkness" don't match.
     */
    public List<String> genresIn(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        // Padded with spaces so every phrase can be searched as " phrase "
        StringBuilder rest = new StringBuilder(" " + SEPARATORS.matcher(text.toLowerCase(Locale.ROOT)).replaceAll(" ").trim() + " ");
        Map<Integer, String> found = new TreeMap<>();
        for (String phrase : PHRASES_LONGEST_FIRST) {
            String padded = " " + phrase + " ";
            int at;
            while ((at = rest.indexOf(padded)) >= 0) {
                found.put(at, ALIASES.get(phrase));
                // Blank it out so a shorter phrase can't match the same words again ("dark prog" then "prog")
                rest.replace(at + 1, at + padded.length() - 1, " ".repeat(phrase.length()));
            }
        }
        return List.copyOf(new LinkedHashSet<>(found.values()));
    }

    private static List<Genre> read(JsonMapper jsonMapper) {
        try (InputStream in = new ClassPathResource(RESOURCE).getInputStream()) {
            return Collections.unmodifiableList(jsonMapper.readValue(in, MapFile.class).genres());
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + RESOURCE, e);
        }
    }

    /** One star system of the map. Only the fields the backend uses are read. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Genre(String id, String name, String family, List<String> artists) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record MapFile(List<Genre> genres) {
    }

    static Set<String> aliasGenreIds() {
        return Set.copyOf(ALIASES.values());
    }
}
