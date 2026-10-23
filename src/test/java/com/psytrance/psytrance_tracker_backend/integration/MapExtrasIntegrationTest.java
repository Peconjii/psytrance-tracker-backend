package com.psytrance.psytrance_tracker_backend.integration;

import com.psytrance.psytrance_tracker_backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Admins add labels and festivals to the map from the panel; they work like the ones in the map files */
class MapExtrasIntegrationTest extends IntegrationTest {

    private static final String ADMIN = "feedback_admin"; // the admin of every integration test
    private static final String PASSWORD = "secret-password-123";

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private com.psytrance.psytrance_tracker_backend.service.ArtistService artistService;

    private String adminToken;

    @BeforeEach
    void anAdmin() throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"username": "%s", "email": "%s@example.com", "password": "%s"}
                """.formatted(ADMIN, ADMIN, PASSWORD)));
        adminToken = login(ADMIN, PASSWORD);
    }

    @Test
    void anAddedLabelIsOnTheMapWithItsPage() throws Exception {
        String name = "Added Label " + uniqueLetters();
        mockMvc.perform(post("/api/admin/map/labels").header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"%s\", \"country\": \"Serbia\", \"since\": 2020, \"genres\": [\"hitech\", \"dark\"]}".formatted(name)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.genres[0]").value("hitech"))
                .andExpect(jsonPath("$.x").isNumber());
        mockMvc.perform(get("/api/map/extras"))
                .andExpect(jsonPath("$.labels[*].name").value(hasItem(name)));
        mockMvc.perform(get("/api/labels/page").param("label", name))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.crew.length()").value(0));

        // The same name again, a genre the map doesn't have, and anyone but an admin
        mockMvc.perform(post("/api/admin/map/labels").header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\": \"%s\", \"genres\": [\"hitech\"]}".formatted(name.toUpperCase())))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/admin/map/labels").header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\": \"Other %s\", \"genres\": [\"polka\"]}".formatted(name)))
                .andExpect(status().isBadRequest());
        String user = uniqueUsername();
        register(user, PASSWORD);
        mockMvc.perform(post("/api/admin/map/labels").header("Authorization", bearer(login(user, PASSWORD)))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\": \"Mine %s\", \"genres\": [\"hitech\"]}".formatted(name)))
                .andExpect(status().isForbidden());
    }

    @Test
    void anAddedLabelCanBeTakenOffAgainButNotOneOfTheMapFile() throws Exception {
        String name = "Mistake Label " + uniqueLetters();
        mockMvc.perform(post("/api/admin/map/labels").header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\": \"%s\", \"genres\": [\"forest\"]}".formatted(name)))
                .andExpect(status().isCreated());
        String user = uniqueUsername();
        register(user, PASSWORD);
        mockMvc.perform(put("/api/me/label-follows").param("label", name).header("Authorization", bearer(login(user, PASSWORD))))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/admin/map/labels").param("name", name).header("Authorization", bearer(adminToken)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/map/extras")).andExpect(jsonPath("$.labels[?(@.name == '%s')]".formatted(name)).isEmpty());
        mockMvc.perform(get("/api/labels/page").param("label", name)).andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/admin/map/labels").param("name", "Iboga Records").header("Authorization", bearer(adminToken)))
                .andExpect(status().isNotFound());
    }

    @Test
    void aLabelsProposalBecomesItsPageAndCrew() throws Exception {
        String unique = uniqueLetters();
        String known = "Known Crew " + unique;
        artistService.saveLineUp(999_990L, java.util.List.of(known), java.time.Instant.now());
        String token = verifiedToken();
        String body = mockMvc.perform(post("/api/page-requests").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"kind": "LABEL", "name": "Proposal Records %s", "country": "Serbia", "since": 2019,
                                 "genres": "prog, psytechno", "description": "Deep night grooves",
                                 "imageUrl": "https://example.com/logo.png", "websiteUrl": "https://proposal.bandcamp.com",
                                 "artists": "%s, Nobody Yet %s"}
                                """.formatted(unique, known, unique)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.since").value(2019))
                .andExpect(jsonPath("$.artists").value(known + ", Nobody Yet " + unique))
                .andReturn().getResponse().getContentAsString();
        long id = ((Number) com.jayway.jsonpath.JsonPath.read(body, "$.id")).longValue();

        mockMvc.perform(post("/api/admin/page-requests/{id}", id).header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"approve\": true, \"onTheMap\": {\"since\": 2019, \"genres\": [\"prog\", \"psytechno\"]}}"))
                .andExpect(jsonPath("$.createdSlug").value("Proposal Records " + unique));
        // The known artist is on the crew, the unknown one left out; the page has the words, logo and Bandcamp
        mockMvc.perform(get("/api/labels/page").param("label", "Proposal Records " + unique))
                .andExpect(jsonPath("$.crew").value(org.hamcrest.Matchers.contains(known)))
                .andExpect(jsonPath("$.profile.description").value("Deep night grooves"))
                .andExpect(jsonPath("$.profile.logoUrl").value("https://example.com/logo.png"))
                .andExpect(jsonPath("$.profile.bandcampUrl").value("https://proposal.bandcamp.com"));
    }

    private String verifiedToken() throws Exception {
        String username = uniqueUsername();
        register(username, PASSWORD);
        var user = userRepository.findByUsername(username).orElseThrow();
        user.setEmailVerified(true);
        userRepository.save(user);
        return login(username, PASSWORD);
    }

    @Test
    void anApprovedFestivalRequestGoesOnTheMapWithItsOwner() throws Exception {
        String unique = uniqueLetters();
        String username = uniqueUsername();
        register(username, PASSWORD);
        var user = userRepository.findByUsername(username).orElseThrow();
        user.setEmailVerified(true);
        userRepository.save(user);
        String token = login(username, PASSWORD);

        String body = mockMvc.perform(post("/api/page-requests").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\": \"FESTIVAL\", \"name\": \"Tiny Gathering %s\", \"country\": \"Serbia\"}".formatted(unique)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long id = ((Number) com.jayway.jsonpath.JsonPath.read(body, "$.id")).longValue();

        mockMvc.perform(post("/api/admin/page-requests/{id}", id).header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"approve": true, "onTheMap": {"since": 2024, "description": "A small forest gathering", "genres": ["forest"]}}
                                """))
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.createdSlug").value("tiny-gathering-" + unique));
        mockMvc.perform(get("/api/map/extras"))
                .andExpect(jsonPath("$.festivals[?(@.id == 'tiny-gathering-%s')].country".formatted(unique)).value("Serbia"));
        mockMvc.perform(get("/api/festivals/page").param("id", "tiny-gathering-" + unique)).andExpect(status().isOk());
        mockMvc.perform(get("/api/me/festivals").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$[0].id").value("tiny-gathering-" + unique));
    }
}
