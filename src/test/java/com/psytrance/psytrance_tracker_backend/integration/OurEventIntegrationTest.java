package com.psytrance.psytrance_tracker_backend.integration;

import com.jayway.jsonpath.JsonPath;
import com.psytrance.psytrance_tracker_backend.client.GoabaseParty;
import com.psytrance.psytrance_tracker_backend.repository.UserRepository;
import com.psytrance.psytrance_tracker_backend.service.ArtistIndexer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Events added on our site: a confirmed email, an admin's approval, then a party like the Goabase ones. */
class OurEventIntegrationTest extends IntegrationTest {

    private static final AtomicLong NEXT_EVENT_ID = new AtomicLong(997_000);
    private static final String ADMIN = "feedback_admin"; // the admin of every integration test
    private static final String PASSWORD = "secret-password-123";

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ArtistIndexer artistIndexer;

    private String adminToken;
    private String unique;
    private long goabaseTwin;
    private LocalDate day;

    @BeforeEach
    void aGoabaseEventOnTheSameDay() throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"username": "%s", "email": "%s@example.com", "password": "%s"}
                """.formatted(ADMIN, ADMIN, PASSWORD)));
        adminToken = login(ADMIN, PASSWORD);
        unique = uniqueLetters();
        day = LocalDate.now().plusDays(20);
        goabaseTwin = NEXT_EVENT_ID.getAndIncrement();
        when(goabaseClient.fetchAllParties()).thenReturn(List.of(new GoabaseParty(goabaseTwin, "Forest Gathering " + unique,
                day + "T22:00:00+01:00", null, "Open Air", "Scheduled", "Portugal", "Sintra", 38.8, -9.4, null, null)));
    }

    @Test
    void anApprovedEventIsListedReadAndOnItsPage() throws Exception {
        String token = verifiedUser();
        String body = mockMvc.perform(post("/api/our-events").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(event("Forest Gathering " + unique + " Afterparty")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(body, "$.id")).longValue();
        long eventId = 1_000_000_000L + id;
        // JSON numbers this size are read back as int

        // Waiting: not listed yet, and the admin sees the Goabase event that may be the same one
        mockMvc.perform(get("/api/events/{id}/details", eventId)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/admin/our-events").header("Authorization", bearer(adminToken)))
                .andExpect(jsonPath("$[?(@.id == %d)].possibleDuplicates[0].id".formatted(id)).value(hasItem((int) goabaseTwin)));

        mockMvc.perform(post("/api/admin/our-events/{id}/approve", id).header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value(eventId));
        mockMvc.perform(get("/api/events/{id}/details", eventId))
                .andExpect(jsonPath("$.nameParty").value("Forest Gathering " + unique + " Afterparty"))
                .andExpect(jsonPath("$.urlPartyHtml").value("https://tickets.example.com/forest"))
                .andExpect(jsonPath("$.startsAt").value(day + "T22:00:00"));
        mockMvc.perform(get("/api/events").param("search", unique))
                .andExpect(jsonPath("$.content[*].id").value(hasItem((int) eventId)));
        // The genres the organizer picked are set by hand, so the line-up can't change them
        mockMvc.perform(get("/api/admin/events/{id}/genres", eventId).header("Authorization", bearer(adminToken)))
                .andExpect(jsonPath("$.manual").value(true))
                .andExpect(jsonPath("$.genres[0].genreId").value("forest"));

        // The line-up is read like a Goabase one: the artist's page lists the gig
        artistIndexer.indexNextBatch();
        mockMvc.perform(get("/api/artists/{slug}", "ground-crew-" + unique))
                .andExpect(jsonPath("$.upcomingEvents[*].id").value(hasItem((int) eventId)));

        // Taken down: off the lists again
        mockMvc.perform(post("/api/admin/our-events/{id}/reject", id).header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\": \"Cancelled\"}"))
                .andExpect(jsonPath("$.status").value("REJECTED"));
        mockMvc.perform(get("/api/events").param("search", unique))
                .andExpect(jsonPath("$.content[*].id").value(not(hasItem((int) eventId))));
        mockMvc.perform(get("/api/our-events/mine").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$[0].rejectReason").value("Cancelled"));
    }

    @Test
    void onlyConfirmedAccountsAddAndAFewWaitAtOnce() throws Exception {
        String username = uniqueUsername();
        register(username, PASSWORD);
        String unconfirmed = login(username, PASSWORD);
        mockMvc.perform(post("/api/our-events").header("Authorization", bearer(unconfirmed))
                        .contentType(MediaType.APPLICATION_JSON).content(event("Unconfirmed " + unique)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/our-events").contentType(MediaType.APPLICATION_JSON).content(event("Guest " + unique)))
                .andExpect(status().isUnauthorized());

        String token = verifiedUser();
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/our-events").header("Authorization", bearer(token))
                            .contentType(MediaType.APPLICATION_JSON).content(event("Night " + i + " " + unique)))
                    .andExpect(status().isCreated());
        }
        mockMvc.perform(post("/api/our-events").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(event("One too many " + unique)))
                .andExpect(status().isConflict());

        // Taking one back makes room again
        String mine = mockMvc.perform(get("/api/our-events/mine").header("Authorization", bearer(token)))
                .andReturn().getResponse().getContentAsString();
        long first = ((Number) JsonPath.read(mine, "$[0].id")).longValue();
        mockMvc.perform(delete("/api/our-events/{id}", first).header("Authorization", bearer(token)))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/our-events").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(event("Room again " + unique)))
                .andExpect(status().isCreated());
    }

    @Test
    void anAdminsEventIsApprovedRightAwayAndBadFieldsAreRefused() throws Exception {
        mockMvc.perform(post("/api/our-events").header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON).content(event("Admin Night " + unique)))
                .andExpect(jsonPath("$.status").value("APPROVED"));

        String token = verifiedUser();
        mockMvc.perform(post("/api/our-events").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(event("Bad link " + unique).replace("https://tickets.example.com/forest", "http://plain.example")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/our-events").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(event("Old " + unique).replace(day.toString(), "2020-01-01")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/our-events").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(event("Bad genre " + unique).replace("\"forest\"", "\"polka\"")))
                .andExpect(status().isBadRequest());
    }

    // An account whose email is confirmed (the link itself is tested in EmailVerificationIntegrationTest)
    private String verifiedUser() throws Exception {
        String username = uniqueUsername();
        register(username, PASSWORD);
        var user = userRepository.findByUsername(username).orElseThrow();
        user.setEmailVerified(true);
        userRepository.save(user);
        return login(username, PASSWORD);
    }

    private String event(String name) {
        return """
                {"name": "%s", "eventType": "Open Air", "startsAt": "%sT22:00:00", "endsAt": null,
                 "timeZone": "Europe/Lisbon", "country": "Portugal", "town": "Sintra", "lat": 38.8, "lon": -9.4,
                 "lineUp": "Ground Crew %s\\nAnother Act %s", "ticketUrl": "https://tickets.example.com/forest",
                 "organizerName": "Test Crew %s", "genres": ["forest"]}
                """.formatted(name, day, unique, unique, unique);
    }
}
