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
    void noLineUpIsAnEmptyList() {
        assertThat(LineUpParser.parse(null)).isEmpty();
    }
}
