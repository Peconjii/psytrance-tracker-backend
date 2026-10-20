package com.psytrance.psytrance_tracker_backend.service;

import com.psytrance.psytrance_tracker_backend.dto.GenreShare;
import com.psytrance.psytrance_tracker_backend.dto.GenreShare.Source;
import com.psytrance.psytrance_tracker_backend.util.LineUpParser.Section;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Works out which Map of Psy genres an event plays, from the most reliable evidence available:
 * <ol>
 *   <li>artists placed in a genre on the map,</li>
 *   <li>for other artists, the genres named in their stage header ("DARKPSY / FOREST STAGE"),</li>
 *   <li>only when the line-up says nothing: the genres the organizer picked on Goabase.</li>
 * </ol>
 * Every artist counts once, split between their genres. An event with no evidence gets no genres:
 * the map shows it in the harbour for unknown genres.
 */
@Component
public class EventGenres {

    // An organizer who ticks more genres than this ticked everything, which says nothing about the event
    static final int MAX_KEYWORD_GENRES = 4;

    private final MapGenres mapGenres;

    public EventGenres(MapGenres mapGenres) {
        this.mapGenres = mapGenres;
    }

    /** The event's genres, biggest share first. Empty when nothing points to a map genre. */
    public List<GenreShare> compute(List<Section> lineUp, List<String> keywords) {
        Map<String, Double> weights = new LinkedHashMap<>();
        Map<String, Source> sources = new LinkedHashMap<>();

        for (Section section : lineUp) {
            List<String> stageGenres = mapGenres.genresIn(section.header());
            if (section.artists().isEmpty()) {
                // A stage the organizer named but whose artists we couldn't read still tells us something
                add(weights, sources, stageGenres, Source.STAGE);
            }
            for (String artist : section.artists()) {
                List<String> artistGenres = mapGenres.genresOfArtist(artist);
                if (!artistGenres.isEmpty()) {
                    add(weights, sources, artistGenres, Source.ARTIST);
                } else {
                    add(weights, sources, stageGenres, Source.STAGE);
                }
            }
        }

        if (weights.isEmpty()) {
            Set<String> picked = new LinkedHashSet<>();
            for (String keyword : keywords) {
                picked.addAll(mapGenres.genresIn(keyword));
            }
            if (picked.size() <= MAX_KEYWORD_GENRES) {
                add(weights, sources, List.copyOf(picked), Source.KEYWORD);
            }
        }

        double total = weights.values().stream().mapToDouble(Double::doubleValue).sum();
        return weights.entrySet().stream()
                .map(e -> new GenreShare(e.getKey(), e.getValue() / total, sources.get(e.getKey())))
                .sorted(Comparator.comparingDouble(GenreShare::share).reversed())
                .toList();
    }

    // One unit of evidence (an artist, a stage, the organizer's pick), split evenly between its genres
    private static void add(Map<String, Double> weights, Map<String, Source> sources, List<String> genres, Source source) {
        for (String genre : genres) {
            weights.merge(genre, 1.0 / genres.size(), Double::sum);
            // Sources are declared most reliable first, so the lower one is the better evidence
            sources.merge(genre, source, (a, b) -> a.compareTo(b) <= 0 ? a : b);
        }
    }
}
