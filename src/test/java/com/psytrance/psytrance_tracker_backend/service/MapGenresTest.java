package com.psytrance.psytrance_tracker_backend.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import tools.jackson.databind.json.JsonMapper;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MapGenresTest {

    // The real map file, so a renamed genre or artist in genres.json breaks this test instead of the map
    private final MapGenres mapGenres = new MapGenres(JsonMapper.builder().build());

    // Stage headers from real Goabase line-ups and Goabase keywords
    @ParameterizedTest(name = "\"{0}\" -> {1}")
    @CsvSource(delimiter = '|', value = {
            "'🔮 MAIN FLOOR | GOA // PSYTRANCE'     | goa",
            "Darkpsy/Forest                         | dark,forest",
            "🌀 Dark Prog 🌀                        | darkprog",
            "CHILL OUT                              | psybient",
            "Hi-Tech                                | hitech",
            "Fullon                                 | fullon",
            "Morning Trance                         | morning",
            "Progressive Trance                     | prog",
            "TWILIGHT & FOREST STAGE                | twilight,forest",
            "Night Full-On                          | night",
    })
    void recognisesGenreWords(String text, String genres) {
        assertThat(mapGenres.genresIn(text)).containsExactlyElementsOf(Arrays.asList(genres.split(",")));
    }

    @ParameterizedTest(name = "\"{0}\" names no genre")
    @CsvSource({"Psytrance", "Psychedelic", "Techno", "MAIN FLOOR", "Darkness Falls", "Goatee", "''"})
    void wordsThatDontPointToOneGenreAreIgnored(String text) {
        assertThat(mapGenres.genresIn(text)).isEmpty();
    }

    @Test
    void findsMapArtistsHoweverTheyAreWritten() {
        assertThat(mapGenres.genresOfArtist("DERANGO")).containsExactly("forest");
        assertThat(mapGenres.genresOfArtist("Talamasca")).containsExactlyInAnyOrder("fullon", "night");
        assertThat(mapGenres.genresOfArtist("Nobody On The Map")).isEmpty();
    }

    @Test
    void everyGenreWordPointsToAGenreOnTheMap() {
        List<String> ids = mapGenres.all().stream().map(MapGenres.Genre::id).toList();
        assertThat(ids).containsAll(MapGenres.aliasGenreIds());
    }
}
