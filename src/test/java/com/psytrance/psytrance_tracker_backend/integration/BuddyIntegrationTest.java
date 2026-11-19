package com.psytrance.psytrance_tracker_backend.integration;

import com.psytrance.psytrance_tracker_backend.client.GoabaseParty;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BuddyIntegrationTest extends IntegrationTest {

    private static final AtomicLong NEXT_EVENT_ID = new AtomicLong(710_000);

    private final List<GoabaseParty> goabaseParties = new ArrayList<>();

    @Test
    void buddiesAreOffByDefaultAndGuestsOnlySeeTheCount() throws Exception {
        long eventId = eventOnGoabase("Ozora");
        String alice = newUserToken();
        going(alice, eventId);

        mockMvc.perform(get("/api/me/buddy").header(HttpHeaders.AUTHORIZATION, bearer(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.visible").value(false));

        visible(alice, "{\"visible\":true,\"country\":\"RS\",\"note\":\"Ride from Belgrade\",\"instagram\":\"alice.psy\"}");

        mockMvc.perform(get("/api/events/{id}/buddies", eventId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.visibleCount").value(1))
                .andExpect(jsonPath("$.canSee").value(false))
                .andExpect(jsonPath("$.reason").value("login"))
                .andExpect(jsonPath("$.buddies.length()").value(0));
    }

    @Test
    void onlyVisiblePeopleGoingThemselvesSeeEachOther() throws Exception {
        long eventId = eventOnGoabase("Boom");
        String alice = newUserToken();
        String bob = newUserToken();
        String hidden = newUserToken();
        String notGoing = newUserToken();
        going(alice, eventId);
        going(bob, eventId);
        going(hidden, eventId);
        visible(alice, "{\"visible\":true,\"country\":\"RS\",\"instagram\":\"alice.psy\"}");
        visible(bob, "{\"visible\":true,\"country\":\"PT\"}");
        visible(notGoing, "{\"visible\":true}");

        mockMvc.perform(get("/api/events/{id}/buddies", eventId).header(HttpHeaders.AUTHORIZATION, bearer(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canSee").value(true))
                .andExpect(jsonPath("$.visibleCount").value(2))
                .andExpect(jsonPath("$.buddies.length()").value(2))
                .andExpect(jsonPath("$.buddies[0].me").value(true))
                .andExpect(jsonPath("$.buddies[0].instagram").value("alice.psy"))
                .andExpect(jsonPath("$.buddies[1].country").value("PT"));

        // Going but hidden: sees nobody until they show themselves
        mockMvc.perform(get("/api/events/{id}/buddies", eventId).header(HttpHeaders.AUTHORIZATION, bearer(hidden)))
                .andExpect(jsonPath("$.canSee").value(false))
                .andExpect(jsonPath("$.reason").value("not-visible"))
                .andExpect(jsonPath("$.buddies.length()").value(0));

        // Visible but not going
        mockMvc.perform(get("/api/events/{id}/buddies", eventId).header(HttpHeaders.AUTHORIZATION, bearer(notGoing)))
                .andExpect(jsonPath("$.canSee").value(false))
                .andExpect(jsonPath("$.reason").value("not-going"));
    }

    @Test
    void badSettingsAreRejected() throws Exception {
        String alice = newUserToken();
        mockMvc.perform(put("/api/me/buddy").header(HttpHeaders.AUTHORIZATION, bearer(alice))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"visible\":true,\"country\":\"XX\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/me/buddy").header(HttpHeaders.AUTHORIZATION, bearer(alice))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"visible\":true,\"instagram\":\"https://x.com\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/me/buddy").contentType(MediaType.APPLICATION_JSON).content("{\"visible\":true}"))
                .andExpect(status().isUnauthorized());
    }

    private void going(String token, long eventId) throws Exception {
        mockMvc.perform(put("/api/events/{id}/going", eventId).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());
    }

    private void visible(String token, String json) throws Exception {
        mockMvc.perform(put("/api/me/buddy").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk());
    }

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
