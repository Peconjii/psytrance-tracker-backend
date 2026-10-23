package com.psytrance.psytrance_tracker_backend.integration;

import com.psytrance.psytrance_tracker_backend.repository.UserRepository;
import com.psytrance.psytrance_tracker_backend.service.ArtistService;
import com.psytrance.psytrance_tracker_backend.service.Organizers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.time.Instant;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** "Claim this page": approved by itself with an email on the official site's domain, otherwise by an admin */
class PageClaimIntegrationTest extends IntegrationTest {

    private static final String ADMIN = "feedback_admin"; // the admin of every integration test
    private static final String PASSWORD = "secret-password-123";

    @Autowired
    private ArtistService artistService;

    @Autowired
    private Organizers organizers;

    @Autowired
    private UserRepository userRepository;

    private String adminToken;

    @BeforeEach
    void anAdmin() throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"username": "%s", "email": "%s@example.com", "password": "%s"}
                """.formatted(ADMIN, ADMIN, PASSWORD)));
        adminToken = login(ADMIN, PASSWORD);
    }

    @Test
    void anEmailOnTheOrganizersDomainOwnsThePageAtOnce() throws Exception {
        String unique = uniqueLetters();
        String organizer = organizers.record("Claim Crew " + unique, "https://www." + unique + "-crew.example/");
        String token = verifiedUser("crew@" + unique + "-crew.example");

        mockMvc.perform(post("/api/claims").header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\": \"ORGANIZER\", \"target\": \"%s\"}".formatted(organizer)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("APPROVED"));
        mockMvc.perform(get("/api/me/organizers").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$[0].slug").value(organizer));

        // Now it has an owner: nobody else can claim it
        mockMvc.perform(post("/api/claims").header("Authorization", bearer(verifiedUser("someone@" + unique + "-crew.example")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\": \"ORGANIZER\", \"target\": \"%s\"}".formatted(organizer)))
                .andExpect(status().isConflict());
    }

    @Test
    void otherwiseAnAdminDecides() throws Exception {
        String unique = uniqueLetters();
        String artist = "Claimed Act " + unique;
        artistService.saveLineUp(996_900L + (long) (Math.random() * 90), List.of(artist), Instant.now());
        String token = verifiedUser(unique + "@gmail.com");

        String body = mockMvc.perform(post("/api/claims").header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"kind": "ARTIST", "target": "%s", "proofUrl": "https://instagram.com/claimedact", "message": "It's me"}
                                """.formatted(artist)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn().getResponse().getContentAsString();
        long id = ((Number) com.jayway.jsonpath.JsonPath.read(body, "$.id")).longValue();

        mockMvc.perform(get("/api/admin/claims").header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/admin/claims/{id}", id).header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"approve\": true, \"trusted\": true}"))
                .andExpect(jsonPath("$.status").value("APPROVED"));
        mockMvc.perform(get("/api/me/artists").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$[0].slug").value("claimed-act-" + unique));
        // Trusted along with the approval: their changes skip the admins from now on
        mockMvc.perform(get("/api/me/page-trust").param("kind", "ARTIST").param("key", "claimed-act-" + unique)
                        .header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.trusted").value(true));
        // The claimer hears of it by mail, with the link to the page
        org.mockito.Mockito.verify(decisionMailer).send(unique + "@gmail.com", "Your claim of " + artist, true,
                "The page is yours now: open it and use \"Edit\".", "/artists/claimed-act-" + unique, null);
    }

    @Test
    void onlyConfirmedAccountsClaimAndOnlyRealPages() throws Exception {
        String username = uniqueUsername();
        register(username, PASSWORD);
        mockMvc.perform(post("/api/claims").header("Authorization", bearer(login(username, PASSWORD)))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"kind\": \"FESTIVAL\", \"target\": \"ozora\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/claims").header("Authorization", bearer(verifiedUser(uniqueLetters() + "@example.com")))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"kind\": \"FESTIVAL\", \"target\": \"no-such-festival\"}"))
                .andExpect(status().isNotFound());
    }

    private String verifiedUser(String email) throws Exception {
        String username = uniqueUsername();
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"username": "%s", "email": "%s", "password": "%s"}
                """.formatted(username, email, PASSWORD))).andExpect(status().isCreated());
        var user = userRepository.findByUsername(username).orElseThrow();
        user.setEmailVerified(true);
        userRepository.save(user);
        return login(username, PASSWORD);
    }
}
