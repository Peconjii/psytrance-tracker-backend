package com.psytrance.psytrance_tracker_backend.service.map;

import com.psytrance.psytrance_tracker_backend.dto.EventDto;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/** What counts as a festival on the map: kind Festival and three days or more */
class FestivalStationsTest {

    private static EventDto event(String type, LocalDate start, LocalDate end) {
        return new EventDto(1L, "Jungle Festival 2027", "Novi Sad", "Serbia", type, null, start, end, null, null, null, null);
    }

    @Test
    void aFestivalOfThreeDaysOrMoreCounts() {
        LocalDate fri = LocalDate.of(2027, 7, 2);
        assertThat(FestivalStations.isFestival(event("Festival", fri, fri.plusDays(2)))).isTrue();   // Fri to Sun
        assertThat(FestivalStations.isFestival(event("festival", fri, fri.plusDays(6)))).isTrue();
    }

    @Test
    void aShortOneOrAnotherKindDoesNot() {
        LocalDate sat = LocalDate.of(2027, 7, 3);
        // A night party called a festival: Saturday night to Sunday noon
        assertThat(FestivalStations.isFestival(event("Festival", sat, sat.plusDays(1)))).isFalse();
        assertThat(FestivalStations.isFestival(event("Festival", sat, null))).isFalse();
        assertThat(FestivalStations.isFestival(event("Open Air", sat, sat.plusDays(3)))).isFalse();
        assertThat(FestivalStations.isFestival(event(null, sat, sat.plusDays(3)))).isFalse();
    }
}
