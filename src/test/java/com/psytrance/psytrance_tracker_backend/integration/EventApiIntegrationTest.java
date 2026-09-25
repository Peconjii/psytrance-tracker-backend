package com.psytrance.psytrance_tracker_backend.integration;

import com.psytrance.psytrance_tracker_backend.client.GoabaseParty;
import com.psytrance.psytrance_tracker_backend.exception.GoabaseUnavailableException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class EventApiIntegrationTest extends IntegrationTest {

    @Test
    void eventsArePublicAndPaged() throws Exception {
        when(goabaseClient.fetchAllParties()).thenReturn(IntStream.rangeClosed(1, 30)
                .mapToObj(i -> party(i, "Party " + i, "Serbia"))
                .toList());

        mockMvc.perform(get("/api/events").param("page", "1").param("size", "24"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(6))
                .andExpect(jsonPath("$.totalElements").value(30))
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    @Test
    void filtersByCountryIgnoringCase() throws Exception {
        when(goabaseClient.fetchAllParties()).thenReturn(List.of(
                party(1, "Ozora", "Hungary"),
                party(2, "Boom", "Portugal")));

        mockMvc.perform(get("/api/events").param("country", "portugal"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].nameParty").value("Boom"))
                .andExpect(jsonPath("$.content[0].dateStart").value("2026-10-01"));
    }

    @Test
    void invalidParametersAreRejected() throws Exception {
        mockMvc.perform(get("/api/events").param("size", "500"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/events").param("timeline", "TOMORROW"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid value for parameter 'timeline'"));
    }

    @Test
    void unknownEventIsNotFound() throws Exception {
        when(goabaseClient.fetchAllParties()).thenReturn(List.of());
        when(goabaseClient.fetchParty(999)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/events/{id}", 999))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Event 999 not found"));
    }

    @Test
    void keepsServingTheLastKnownEventsWhenGoabaseGoesDown() throws Exception {
        when(goabaseClient.fetchAllParties())
                .thenReturn(List.of(party(1, "Still here", "Serbia")))
                .thenThrow(new GoabaseUnavailableException("down", null));

        mockMvc.perform(get("/api/events")).andExpect(status().isOk());

        mockMvc.perform(get("/api/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].nameParty").value("Still here"));
    }

    @Test
    void goabaseOutageIsAServiceUnavailableNotACrash() throws Exception {
        when(goabaseClient.fetchAllParties()).thenReturn(List.of());
        when(goabaseClient.fetchParty(42)).thenThrow(new GoabaseUnavailableException("down", null));

        mockMvc.perform(get("/api/events/{id}", 42))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("Event data is temporarily unavailable, please try again later"));
    }

    private static GoabaseParty party(long id, String name, String country) {
        return new GoabaseParty(id, name, "2026-10-01T22:00:00+02:00", null, "Festival", "Scheduled",
                country, "Town", 45.0, 19.0, null, null);
    }
}
