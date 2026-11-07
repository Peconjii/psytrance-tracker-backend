package com.psytrance.psytrance_tracker_backend.service.events;

import com.psytrance.psytrance_tracker_backend.dto.TimetableDto;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** A timetable set by hand writes the line-up of an event added on the site, in the stage cards' format */
class TimetableLineUpTest {

    private static TimetableDto.Set set(String stage, String artist, String start, String end) {
        return new TimetableDto.Set(stage, artist, null, LocalDateTime.parse(start), end == null ? null : LocalDateTime.parse(end));
    }

    @Test
    void oneStageWithoutANameIsJustTheSetsInTimeOrderAndLiveStaysLive() {
        String before = "Sat 23:00-00:30 Exelog (live)\nSun 06:00-10:00 AUM SHANTI (live)";
        String lineUp = EventTimetables.lineUpOf(List.of(
                set(null, "Senchuk", "2026-11-15T02:00", "2026-11-15T04:00"),
                set(null, "Vladimir Trbulin", "2026-11-14T23:00", "2026-11-15T02:00"),
                set(null, "AUM SHANTI", "2026-11-15T06:00", "2026-11-15T08:00"),
                set(null, "Exelog", "2026-11-15T08:00", null)), before);

        assertThat(lineUp).isEqualTo("""
                Sat 23:00-02:00 Vladimir Trbulin
                Sun 02:00-04:00 Senchuk
                Sun 06:00-08:00 AUM SHANTI (live)
                Sun 08:00 Exelog (live)""");
    }

    @Test
    void stagesGetTheirHeaders() {
        String lineUp = EventTimetables.lineUpOf(List.of(
                set("Main Stage (Forest)", "Ajja", "2026-11-13T23:00", "2026-11-14T01:00"),
                set("Chill (Psybient / Psychill)", "Ishq", "2026-11-14T10:00", null)), null);

        assertThat(lineUp).isEqualTo("""
                Main Stage (Forest):
                Fri 23:00-01:00 Ajja

                Chill (Psybient / Psychill):
                Sat 10:00 Ishq""");
    }
}
