package com.psytrance.psytrance_tracker_backend.service.map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import tools.jackson.databind.json.JsonMapper;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

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
    void aMergedSpellingPlaysWhatTheArtistPlays() {
        mapGenres.useAliases(Map.of("derango-experience", "derango"));
        assertThat(mapGenres.genresOfArtist("Derango Experience")).containsExactly("forest");
    }

    @Test
    void aGenreNameInBracketsIsTheStagesGenre() {
        // How our event form writes a stage with its genre
        assertThat(mapGenres.genreInBrackets("Main Stage (Morning full-on)")).contains("morning");
        assertThat(mapGenres.genreInBrackets("Chai Shop (Psybient / Psychill)")).contains("psybient");
        assertThat(mapGenres.genreInBrackets("Garden (south african scene)")).contains("sapsy");
        assertThat(mapGenres.genreInBrackets("Main Stage (by night)")).isEmpty();
        assertThat(mapGenres.genreInBrackets("Forest Stage")).isEmpty();
        // A stage of our event form with several genres: the first one for its sets, all of them in order
        assertThat(mapGenres.genreInBrackets("Main Stage (Forest, Goa trance)")).contains("forest");
        assertThat(mapGenres.genresInBrackets("Main Stage (Forest, Goa trance, Forest)")).containsExactly("forest", "goa");
        assertThat(mapGenres.genresInBrackets("Main Stage (by night)")).isEmpty();
    }

    @Test
    void everyGenreWordPointsToAGenreOnTheMap() {
        List<String> ids = mapGenres.all().stream().map(MapGenres.Genre::id).toList();
        assertThat(ids).containsAll(MapGenres.aliasGenreIds());
    }

    @Test
    void anAddedGenreIsAGenreAndItsNameAndWordsAreFoundInStageHeaders() {
        MapGenres genres = new MapGenres(JsonMapper.builder().build());
        genres.useAddedGenres(List.of(new MapGenres.Genre("hypnotic-psy", "Hypnotic psy", "dark", List.of(), 1500, 1500)),
                Map.of("Hypnotic psy", "hypnotic-psy", "hypno", "hypnotic-psy", "forest", "hypnotic-psy"));

        assertThat(genres.isGenre("hypnotic-psy")).isTrue();
        assertThat(genres.genresIn("HYPNOTIC PSY / FOREST stage")).containsExactly("hypnotic-psy", "forest");
        assertThat(genres.genresIn("Hypno floor")).containsExactly("hypnotic-psy");
        // A word a genre of the map file uses keeps pointing there
        assertThat(genres.genresIn("Forest")).containsExactly("forest");
        assertThat(genres.genresInBrackets("Main Stage (Hypnotic psy, Forest)")).containsExactly("hypnotic-psy", "forest");

        // Taken off again
        genres.useAddedGenres(List.of(), Map.of());
        assertThat(genres.isGenre("hypnotic-psy")).isFalse();
        assertThat(genres.genresIn("Hypno floor")).isEmpty();
    }
}
