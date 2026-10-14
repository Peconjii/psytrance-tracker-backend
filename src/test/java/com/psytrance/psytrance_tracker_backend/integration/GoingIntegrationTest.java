package com.psytrance.psytrance_tracker_backend.integration;

import com.psytrance.psytrance_tracker_backend.client.GoabaseParty;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GoingIntegrationTest extends IntegrationTest {

    // All tests share one database, so each test uses its own event ids and counts start at 0
    private static final AtomicLong NEXT_EVENT_ID = new AtomicLong(700_000);

    // JUnit creates a new instance for every test, so each test starts with an empty list
    private final List<GoabaseParty> goabaseParties = new ArrayList<>();

    @Test
    void guestsSeeTheCountButMustLogInToMarkGoing() throws Exception {
        long eventId = eventOnGoabase("Ozora");

        mockMvc.perform(get("/api/events/{id}/going", eventId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(0))
                .andExpect(jsonPath("$.going").value(false));

        mockMvc.perform(put("/api/events/{id}/going", eventId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void markingTwiceCountsOnceAndUnmarkingUndoesIt() throws Exception {
        long eventId = eventOnGoabase("Boom");
        String token = newUserToken();

        for (int press = 0; press < 2; press++) {
            mockMvc.perform(put("/api/events/{id}/going", eventId).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.count").value(1))
                    .andExpect(jsonPath("$.going").value(true));
        }

        mockMvc.perform(delete("/api/events/{id}/going", eventId).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(0))
                .andExpect(jsonPath("$.going").value(false));
    }

    @Test
    void aWholePageOfEventsIsAnsweredInOneRequest() throws Exception {
        long busy = eventOnGoabase("Busy");
        long quiet = eventOnGoabase("Quiet");
        String alice = newUserToken();
        String bob = newUserToken();
        mockMvc.perform(put("/api/events/{id}/going", busy).header(HttpHeaders.AUTHORIZATION, bearer(alice)));
        mockMvc.perform(put("/api/events/{id}/going", busy).header(HttpHeaders.AUTHORIZATION, bearer(bob)));

        mockMvc.perform(get("/api/events/going")
                        .param("ids", busy + "," + quiet)
                        .header(HttpHeaders.AUTHORIZATION, bearer(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['%d'].count".formatted(busy)).value(2))
                .andExpect(jsonPath("$['%d'].going".formatted(busy)).value(true))
                .andExpect(jsonPath("$['%d'].count".formatted(quiet)).value(0))
                .andExpect(jsonPath("$['%d'].going".formatted(quiet)).value(false));
    }

    @Test
    void profileListsTheEventsTheUserIsGoingTo() throws Exception {
        long eventId = eventOnGoabase("Transition Experience");
        String token = newUserToken();
        mockMvc.perform(put("/api/events/{id}/going", eventId).header(HttpHeaders.AUTHORIZATION, bearer(token)));

        mockMvc.perform(get("/api/me/going").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].eventId").value(eventId))
                .andExpect(jsonPath("$[0].eventName").value("Transition Experience"))
                .andExpect(jsonPath("$[0].startsOn").value(LocalDate.now().plusDays(10).toString()));
    }

    @Test
    void unknownEventsCannotBeMarked() throws Exception {
        when(goabaseClient.fetchAllParties()).thenReturn(List.of());
        when(goabaseClient.fetchParty(999)).thenReturn(Optional.empty());

        mockMvc.perform(put("/api/events/{id}/going", 999).header(HttpHeaders.AUTHORIZATION, bearer(newUserToken())))
                .andExpect(status().isNotFound());
    }

    @Test
    void tooManyEventsAtOnceIsABadRequest() throws Exception {
        String ids = String.join(",", IntStream.rangeClosed(1, 101).mapToObj(String::valueOf).toList());

        mockMvc.perform(get("/api/events/going").param("ids", ids))
                .andExpect(status().isBadRequest());
    }

    /** Adds an event starting in 10 days to the fake Goabase list and returns its id. */
    private long eventOnGoabase(String name) {
        long id = NEXT_EVENT_ID.getAndIncrement();
        goabaseParties.add(new GoabaseParty(id, name, LocalDate.now().plusDays(10) + "T22:00:00+02:00", null,
                "Festival", "Scheduled", "Hungary", "Ozora", 46.75, 18.4, null, null));
        when(goabaseClient.fetchAllParties()).thenReturn(List.copyOf(goabaseParties));
        return id;
    }

    private String newUserToken() throws Exception {
        String username = uniqueUsername();
        register(username, "password123");
        return login(username, "password123");
    }
}
