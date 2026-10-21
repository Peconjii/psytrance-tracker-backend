package com.psytrance.psytrance_tracker_backend.util;

import com.psytrance.psytrance_tracker_backend.util.TimetableParser.Slot;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// Examples are taken from real Goabase line-ups
class TimetableParserTest {

    private static final ZoneOffset CET = ZoneOffset.ofHours(2);
    private static final LocalDate FRIDAY = LocalDate.of(2026, 10, 23);

    private static OffsetDateTime at(int day, int hour, int minute) {
        return OffsetDateTime.of(2026, 10, day, hour, minute, 0, 0, CET);
    }

    // Without the notes after the names, which one test checks on its own
    private static List<Slot> parse(String lineUp) {
        return TimetableParser.parse(lineUp, FRIDAY, 22, CET).stream()
                .map(s -> new Slot(s.stage(), s.artist(), s.startsAt(), s.endsAt()))
                .toList();
    }

    @Test
    void keepsWhatTheLineSaysAfterTheName() {
        assertThat(TimetableParser.parse("""
                22:00 - 00:00 | Naraka • Twilight/Forest
                02:30 TRISTAN - Nano Rec - UK
                05:00 Plain Name
                """, FRIDAY, 22, CET)).extracting(Slot::note)
                .containsExactly("• Twilight/Forest", "- Nano Rec - UK", null);
    }

    @Test
    void namesAreUnescapedAndLoseALeadingLive() {
        // As Goabase sends them: "&amp;" escaped, and "Live" written before the act (line-up 117775)
        assertThat(parse("""
                23:00 Tom &amp; Jerry
                02:15 – 04:15 Live M3dra
                """)).extracting(Slot::artist)
                .containsExactly("Tom & Jerry", "M3dra");
    }

    @Test
    void startTimesOnlyAndSetsAfterMidnightAreTheNextDay() {
        assertThat(parse("""
                20:00 DOORS
                22:00 Gandalf - IONO Music - Berlin
                02:30 TRISTAN - Nano Rec - UK
                05:30 Future Moon - Digital OM - München
                """)).containsExactly(
                new Slot(null, "Gandalf", at(23, 22, 0), null),
                new Slot(null, "TRISTAN", at(24, 2, 30), null),
                new Slot(null, "Future Moon", at(24, 5, 30), null));
    }

    @Test
    void startAndEndInManyStyles() {
        assertThat(parse("""
                23:00–00:30 Uhr — Zortarf
                00:30–02:00 Uhr — Gorjie
                22:00 - 00:00 | Naraka • Twilight/Forest
                02:15 – 04:15 Live M3dra
                22:30 -  24:00 Dj Nous
                """)).containsExactly(
                new Slot(null, "Zortarf", at(23, 23, 0), at(24, 0, 30)),
                new Slot(null, "Gorjie", at(24, 0, 30), at(24, 2, 0)),
                // Earlier than the set before it by more than six hours: a new night on the same timetable
                new Slot(null, "Naraka", at(24, 22, 0), at(25, 0, 0)),
                new Slot(null, "M3dra", at(25, 2, 15), at(25, 4, 15)),
                new Slot(null, "Dj Nous", at(25, 22, 30), at(26, 0, 0)));
    }

    @Test
    void twelveHourClockAndHandles() {
        assertThat(parse("""
                7:00 PM – 8:00 PM — ACiDMAN 🇹🇭 @acidman420i
                11:00 PM – 12:30 AM — GOA GUMMY 🇹🇭 @604gummy
                """)).containsExactly(
                new Slot(null, "ACiDMAN", at(23, 19, 0), at(23, 20, 0)),
                new Slot(null, "GOA GUMMY", at(23, 23, 0), at(24, 0, 30)));
    }

    @Test
    void stageHeadersAndDateLinesApplyToTheTimesBelow() {
        assertThat(parse("""
                MAIN STAGE
                23:00 Pure Harry
                03:00 Otacon
                CHILL FLOOR
                00:00 Der Würfler
                24.10.2026 · Day 2
                21:00 Ajja
                """)).containsExactly(
                new Slot("MAIN STAGE", "Pure Harry", at(23, 23, 0), null),
                new Slot("MAIN STAGE", "Otacon", at(24, 3, 0), null),
                new Slot("CHILL FLOOR", "Der Würfler", at(24, 0, 0), null),
                new Slot("CHILL FLOOR", "Ajja", at(24, 21, 0), null));
    }

    @Test
    void timesWithoutAnActAreLeftOut() {
        assertThat(parse("""
                18:30-19:30
                21:00 - 22:00 | Intro
                06:00 – ??:??
                Earth M
                """)).isEmpty();
    }

    @Test
    void theLineUpStillFindsArtistsBehindSetTimes() {
        assertThat(LineUpParser.findArtist("21:00 - 22:00 | Naraka • Twilight/Forest")).isEqualTo("Naraka");
        assertThat(LineUpParser.findArtist("23:00–00:30 Uhr — Zortarf")).isEqualTo("Zortarf");
        assertThat(LineUpParser.findArtist("20:00 DOORS")).isNull();
        assertThat(LineUpParser.findArtist("18:30-19:30")).isNull();
    }
}
