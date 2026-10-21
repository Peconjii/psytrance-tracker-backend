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

    // Lines from real Goabase line-ups that looked like artist names before
    @ParameterizedTest(name = "\"{0}\" is not an artist")
    @CsvSource(delimiter = '|', value = {
            "Location", "Lounge", "Coming Soon", "Details comming soon", "DJs", "DJ Set", "Artists", "Camping",
            "Food & Bar", "Free Entry", "FREE RAVE", "Children's area", "CH", "ーLive",
            "20359 Hamburg", "Oberfeldstrasse 12A", "'C/ Fermín Caballero, 69'", "'Club Schlaflos, Aarau'",
            "19. Dezember 2026", "28 NOVEMBRE 2026", "30 OCT", "31.10.2026 · PARKHOUSE GRAZ",
            "15min Pause", "Fahrt dauert 6 Min", "1 hour from Dublin", "700 Meter Fussweg", "2 DANCE FLOORS",
            "4 Rooms", "10 KW FUNCTION ONE", "Ab 18 Jahren", "Freitag Start 22 UHR",
            "facebook.com/rene.ragas", "COSMIC CIRCUS presents", "Braingames Records", "Aajaadee Rec",
            "Earlybird 10", "Abendkasse vorhanden", "Come early. Stay late", "ATTENTION! Limited capacity",
            // Blank spacers that count as letters: a Hangul filler (seen in 11 real line-ups), a blank Braille pattern
            "ㅤ", "⠀⠀",
    })
    void skipsPartyDetailsThatLookLikeNames(String line) {
        assertThat(LineUpParser.findArtist(line)).isNull();
    }

    @ParameterizedTest(name = "\"{0}\" is still an artist")
    @CsvSource({"1200 Micrograms", "Dr.Psylocibine", "B. RYDER", "7Eleven", "4MOTION", "Dž & Mž", "Live Earth", "ATMOS", "M!GuZ & BeXXn"})
    void artistsThatLookLikeNoiseAreKept(String line) {
        assertThat(LineUpParser.findArtist(line)).isEqualTo(line);
    }

    @Test
    void styledLettersBecomePlainOnes() {
        assertThat(LineUpParser.findArtist("𝗔𝗚𝗡𝗘𝗧𝗢𝗡")).isEqualTo("AGNETON");
    }

    @Test
    void nothingUnderAVenueOrTicketsHeaderIsAnArtistUntilTheNextStage() {
        List<LineUpParser.Section> sections = LineUpParser.sections("""
                Location:
                Club Schlaflos
                Oberfeldstrasse 12A
                8302 Kloten

                MAIN FLOOR
                1200 Micrograms
                Astrix

                Tickets
                Abendkasse 25
                """);

        assertThat(sections).flatExtracting(LineUpParser.Section::artists).containsExactly("1200 Micrograms", "Astrix");
    }

    @Test
    void aFourDigitPostcodeAfterAStreetIsNotAnArtist() {
        assertThat(LineUpParser.parse("Oberfeldstrasse 12A\n8302 Kloten\nAstrix"))
                .extracting(LineUpLine::artist)
                .containsExactly(null, null, "Astrix");
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
