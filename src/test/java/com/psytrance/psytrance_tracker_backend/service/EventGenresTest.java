package com.psytrance.psytrance_tracker_backend.service;

import com.psytrance.psytrance_tracker_backend.dto.GenreShare;
import com.psytrance.psytrance_tracker_backend.dto.GenreShare.Source;
import com.psytrance.psytrance_tracker_backend.util.LineUpParser.Section;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class EventGenresTest {

    private final EventGenres eventGenres = new EventGenres(new MapGenres(List.of(
            new MapGenres.Genre("forest", "Forest", "dark", List.of("Derango", "Megalopsy")),
            new MapGenres.Genre("night", "Night full-on", "goa", List.of("Talamasca", "Rezonant")),
            new MapGenres.Genre("fullon", "Full-on", "goa", List.of("Talamasca")))));

    @Test
    void eachArtistCountsOnceSplitBetweenTheirGenres() {
        List<GenreShare> genres = eventGenres.compute(List.of(
                new Section(null, List.of("Derango", "Megalopsy", "Talamasca"))), List.of());

        // Derango and Megalopsy: 2 for forest; Talamasca: half night, half full-on
        assertThat(genres).extracting(GenreShare::genreId).containsExactly("forest", "night", "fullon");
        assertThat(genres.get(0).share()).isCloseTo(2 / 3.0, within(1e-9));
        assertThat(genres.get(1).share()).isCloseTo(1 / 6.0, within(1e-9));
        assertThat(genres).allSatisfy(genre -> assertThat(genre.source()).isEqualTo(Source.ARTIST));
    }

    @Test
    void artistsNotOnTheMapTakeTheirStageGenre() {
        List<GenreShare> genres = eventGenres.compute(List.of(
                new Section("DARKPSY / FOREST STAGE", List.of("Derango", "Unknown One", "Unknown Two"))), List.of());

        // Derango: 1 forest (artist). The two unknown artists: 1 each, split between dark and forest (stage)
        assertThat(genres).containsExactly(
                new GenreShare("forest", 2 / 3.0, Source.ARTIST),
                new GenreShare("dark", 1 / 3.0, Source.STAGE));
    }

    @Test
    void aNamedStageWithoutReadableArtistsStillCounts() {
        assertThat(eventGenres.compute(List.of(new Section("CHILL OUT", List.of())), List.of()))
                .containsExactly(new GenreShare("psybient", 1.0, Source.STAGE));
    }

    @Test
    void goabaseKeywordsAreOnlyUsedWhenTheLineUpSaysNothing() {
        List<Section> unknownArtists = List.of(new Section(null, List.of("Unknown One")));

        assertThat(eventGenres.compute(unknownArtists, List.of("Forest", "Psytrance", "Twilight")))
                .containsExactly(
                        new GenreShare("forest", 0.5, Source.KEYWORD),
                        new GenreShare("twilight", 0.5, Source.KEYWORD));
        assertThat(eventGenres.compute(List.of(new Section(null, List.of("Derango"))), List.of("Twilight")))
                .containsExactly(new GenreShare("forest", 1.0, Source.ARTIST));
    }

    @Test
    void anOrganizerWhoTickedEverythingSaysNothing() {
        List<String> everything = List.of("Dark Prog", "Darkpsy", "Forest", "Fullon", "Hi-Tech", "Progressive",
                "Psycore", "Psytrance", "Twilight");

        assertThat(eventGenres.compute(List.of(), everything)).isEmpty();
    }

    @Test
    void noEvidenceMeansNoGenres() {
        assertThat(eventGenres.compute(List.of(), List.of())).isEmpty();
    }
}
