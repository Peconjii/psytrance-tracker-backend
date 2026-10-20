package com.psytrance.psytrance_tracker_backend.util;

import com.psytrance.psytrance_tracker_backend.dto.LineUpLine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// Examples are taken from real Goabase line-ups
class LineUpParserTest {

    @ParameterizedTest(name = "\"{0}\" -> {1}")
    @CsvSource(delimiter = '|', value = {
            "DJ LUCAS (Tip Records)                 | DJ LUCAS",
            "ETNICA - MAURIZZIO BEGOTTI DJ Set (Etnicanet) | ETNICA",
            "INDRA – Ecstatic Dance                 | INDRA",
            "KEZO MOON— Ovnimoon Records            | KEZO MOON",
            "→ Mak Eye   (Vanadinit Records)        | Mak Eye",
            "🕉️ Cotrax                             | Cotrax",
            "Earth M 🇸🇪                            | Earth M",
            "A-MUSH - live                          | A-MUSH",
            "THE HORRIDS (TBC)                      | THE HORRIDS",
            "SUN-WU-KONG                            | SUN-WU-KONG",
            "ALMA DEYA [LIVE]                       | ALMA DEYA",
            "BACK TO MARS [BR]/[NL]                 | BACK TO MARS",
            // A genre word next to a real name is still an artist
            "DARK CHOCOLATE [ES]                    | DARK CHOCOLATE",
    })
    void findsTheArtistName(String line, String artist) {
        assertThat(LineUpParser.findArtist(line)).isEqualTo(artist);
    }

    @ParameterizedTest(name = "\"{0}\" has no artist")
    @CsvSource(delimiter = '|', value = {
            "🔥 PARADIGMA 2026 – LINE UP",
            "'🔮 MAIN FLOOR | GOA // PSYTRANCE'",
            "🎧 Featuring:",
            "Special guest :",
            "SPECIAL GUEST",
            "+ TBA",
            "💿 TBA",
            "... and more tba",
            "(Psygnosis Records)",
            "https://soundcloud.com/liquid-soul",
            "Visuals by Defekt Visuals",
            "Leave your gear behind – we provide everything for your stay.",
            "ESPAÇO NATIVOS: GLAMPING & READY-TO-CAMP",
            "Darkpsy",
            "Darkpsy/Forest",
            "CHILL OUT",
            "🌀 Dark Prog 🌀",
            "Artwork by Joe Sanchez",
            "Flyer Design by Joshuadesigns",
            "Dekoration & Mapping",
            "About event",
            "b2b",
    })
    void skipsLinesThatAreNotArtists(String line) {
        assertThat(LineUpParser.findArtist(line)).isNull();
    }

    @Test
    void keepsEveryLineAndSingleBlankLinesBetweenGroups() {
        List<LineUpLine> lines = LineUpParser.parse("Earth M 🇸🇪\r\n(Psygnosis Records)\r\n\r\n\r\nApnea\r\n\r\n");

        assertThat(lines).containsExactly(
                new LineUpLine("Earth M 🇸🇪", "Earth M"),
                new LineUpLine("(Psygnosis Records)", null),
                new LineUpLine("", null),
                new LineUpLine("Apnea", "Apnea"));
    }

    @Test
    void htmlEscapedCharactersAreShownAsTheyWereTyped() {
        assertThat(LineUpParser.parse("Remaza &gt; TrancerSpacy\r\nDrum &amp; Bass Floor"))
                .extracting(LineUpLine::text)
                .containsExactly("Remaza > TrancerSpacy", "Drum & Bass Floor");
    }

    @Test
    void noLineUpIsAnEmptyList() {
        assertThat(LineUpParser.parse(null)).isEmpty();
        assertThat(LineUpParser.sections(null)).isEmpty();
    }

    @Test
    void splitsTheLineUpAtItsStageHeaders() {
        List<LineUpParser.Section> sections = LineUpParser.sections("""
                Opening Act
                🔮 MAIN FLOOR | GOA // PSYTRANCE

                DJ LUCAS (Tip Records)
                + TBA
                Darkpsy/Forest
                Earth M 🇸🇪
                (Psygnosis Records)
                Apnea
                """);

        assertThat(sections).containsExactly(
                new LineUpParser.Section(null, List.of("Opening Act")),
                new LineUpParser.Section("🔮 MAIN FLOOR | GOA // PSYTRANCE", List.of("DJ LUCAS")),
                new LineUpParser.Section("Darkpsy/Forest", List.of("Earth M", "Apnea")));
    }
}
