package com.psytrance.psytrance_tracker_backend.integration;

import com.jayway.jsonpath.JsonPath;
import com.psytrance.psytrance_tracker_backend.service.ArtistService;
import com.psytrance.psytrance_tracker_backend.service.Organizers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Owners of labels and festivals: their pages, a label's crew through an admin, and who may do what */
class LabelFestivalOwnerIntegrationTest extends IntegrationTest {

    private static final String ADMIN = "feedback_admin"; // the admin of every integration test
    private static final String PASSWORD = "secret-password-123";
    // Labels and festivals of the map no other test changes
    private static final String LABEL = "Iboga Records";
    private static final String FESTIVAL = "psyfi";

    @Autowired
    private ArtistService artistService;

    @Autowired
    private Organizers organizers;

    private String adminToken;
    private String owner;
    private String ownerToken;

    @BeforeEach
    void anAdminAndAnOwner() throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"username": "%s", "email": "%s@example.com", "password": "%s"}
                """.formatted(ADMIN, ADMIN, PASSWORD)));
        adminToken = login(ADMIN, PASSWORD);
        owner = uniqueUsername();
        register(owner, PASSWORD);
        ownerToken = login(owner, PASSWORD);
    }

    @Test
    void aLabelOwnerWritesThePageAndAsksForCrewChanges() throws Exception {
        mockMvc.perform(put("/api/admin/labels/owner").param("label", LABEL).header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"username\": \"%s\"}".formatted(owner)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/me/labels").header("Authorization", bearer(ownerToken)))
                .andExpect(jsonPath("$[0].name").value(LABEL));

        mockMvc.perform(put("/api/labels/profile").param("label", LABEL).header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\": \"Copenhagen since 1999\", \"websiteUrl\": \"https://iboga.example\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/labels/page").param("label", LABEL))
                .andExpect(jsonPath("$.profile.description").value("Copenhagen since 1999"));

        // An artist from the line-ups joins the crew only once an admin approves
        String newcomer = "Crew Newcomer " + uniqueLetters();
        artistService.saveLineUp(996_500L + (long) (Math.random() * 400), List.of(newcomer), Instant.now());
        mockMvc.perform(post("/api/labels/requests").param("label", LABEL).header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"kind\": \"ADD\", \"artistName\": \"%s\"}".formatted(newcomer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value(not(hasItem(newcomer))));
        String requests = mockMvc.perform(get("/api/admin/labels/requests").header("Authorization", bearer(adminToken)))
                .andReturn().getResponse().getContentAsString();
        List<Integer> ids = JsonPath.read(requests, "$[?(@.artistName == '%s')].id".formatted(newcomer));
        mockMvc.perform(post("/api/admin/labels/requests/{id}", ids.get(0)).header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"approve\": true}"))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/labels/page").param("label", LABEL))
                .andExpect(jsonPath("$.crew").value(hasItem(newcomer)));

        // Nobody else edits it; the owner sees the label's own numbers
        String stranger = uniqueUsername();
        register(stranger, PASSWORD);
        mockMvc.perform(get("/api/labels/edit").param("label", LABEL).header("Authorization", bearer(login(stranger, PASSWORD))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/labels/owner-stats").param("label", LABEL).header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk());

        // An admin takes the newcomer off again at once
        mockMvc.perform(post("/api/labels/requests").param("label", LABEL).header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"kind\": \"REMOVE\", \"artistName\": \"%s\"}".formatted(newcomer)))
                .andExpect(jsonPath("$").value(not(hasItem(newcomer))));
        mockMvc.perform(delete("/api/admin/labels/owner").param("label", LABEL).header("Authorization", bearer(adminToken)))
                .andExpect(status().isNoContent());
    }

    @Test
    void aFestivalOwnerWritesThePageAndLinksTheOrganizer() throws Exception {
        String organizer = organizers.record("Festival Crew " + uniqueLetters(), null);
        mockMvc.perform(put("/api/admin/festivals/owner").param("id", FESTIVAL).header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"username\": \"%s\"}".formatted(owner)))
                .andExpect(status().isNoContent());

        mockMvc.perform(put("/api/festivals/profile").param("id", FESTIVAL).header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"ticketUrl": "https://tickets.example/psyfi", "organizerSlug": "%s"}
                                """.formatted(organizer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.organizer.slug").value(organizer));
        mockMvc.perform(get("/api/festivals/page").param("id", FESTIVAL))
                .andExpect(jsonPath("$.profile.ticketUrl").value("https://tickets.example/psyfi"));
        mockMvc.perform(get("/api/me/festivals").header("Authorization", bearer(ownerToken)))
                .andExpect(jsonPath("$[0].id").value(FESTIVAL));

        mockMvc.perform(put("/api/festivals/profile").param("id", FESTIVAL).header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"ticketUrl\": \"http://plain.example\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/festivals/edit").param("id", FESTIVAL)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/festivals/page").param("id", "no-such-festival")).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/festivals/owner-stats").param("id", FESTIVAL).header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk());
    }
}
