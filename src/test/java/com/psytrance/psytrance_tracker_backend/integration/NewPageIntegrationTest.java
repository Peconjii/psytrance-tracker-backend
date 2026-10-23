package com.psytrance.psytrance_tracker_backend.integration;

import com.psytrance.psytrance_tracker_backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** "Not on the map yet?": an admin approves a new page; an artist or organizer gets it at once, with its owner */
class NewPageIntegrationTest extends IntegrationTest {

    private static final String ADMIN = "feedback_admin"; // the admin of every integration test
    private static final String PASSWORD = "secret-password-123";

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcClient jdbc;

    private String adminToken;

    @BeforeEach
    void anAdmin() throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"username": "%s", "email": "%s@example.com", "password": "%s"}
                """.formatted(ADMIN, ADMIN, PASSWORD)));
        adminToken = login(ADMIN, PASSWORD);
    }

    @Test
    void anApprovedArtistGetsAPageAndOwnsIt() throws Exception {
        String unique = uniqueLetters();
        String token = verifiedUser(unique + "@gmail.com");

        long id = ask(token, """
                {"kind": "ARTIST", "name": "Brand New Act %s", "country": "Serbia", "proofUrl": "https://instagram.com/brandnew",
                 "genres": "dark, forest", "description": "Night sounds", "imageUrl": "https://pics.example/act.jpg"}
                """.formatted(unique));
        mockMvc.perform(get("/api/me/page-requests").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$[0].status").value("PENDING"))
                .andExpect(jsonPath("$[0].genres").value("dark, forest"));

        mockMvc.perform(post("/api/admin/page-requests/{id}", id).header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"approve\": true}"))
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.createdSlug").value("brand-new-act-" + unique));
        mockMvc.perform(get("/api/me/artists").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$[0].slug").value("brand-new-act-" + unique));
        // The photo and words of the request are on the new page
        assertThat(jdbc.sql("select photo_url, bio from artist_profiles where slug = :slug").param("slug", "brand-new-act-" + unique)
                .query((rs, row) -> List.of(rs.getString(1), rs.getString(2))).single())
                .containsExactly("https://pics.example/act.jpg", "Night sounds");
        // No gigs yet, so the artists list doesn't have it, but the new pages under it do
        mockMvc.perform(get("/api/artists/new-pages").param("search", "Brand New Act " + unique))
                .andExpect(jsonPath("$[0].slug").value("brand-new-act-" + unique))
                .andExpect(jsonPath("$[0].photoUrl").value("https://pics.example/act.jpg"));

        // Now it's on the site: asking again points to the page to claim
        mockMvc.perform(post("/api/page-requests").header("Authorization", bearer(verifiedUser(uniqueLetters() + "@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\": \"ARTIST\", \"name\": \"Brand New Act %s\"}".formatted(unique)))
                .andExpect(status().isConflict());
    }

    @Test
    void anApprovedOrganizerGetsAPageWithItsWebsite() throws Exception {
        String unique = uniqueLetters();
        String token = verifiedUser(unique + "@example.com");
        long id = ask(token, """
                {"kind": "ORGANIZER", "name": "New Crew %s", "websiteUrl": "https://newcrew.example",
                 "description": "Forest parties", "imageUrl": "https://pics.example/crew.png"}
                """.formatted(unique));

        mockMvc.perform(post("/api/admin/page-requests/{id}", id).header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"approve\": true}"))
                .andExpect(jsonPath("$.createdSlug").value("new-crew-" + unique));
        mockMvc.perform(get("/api/me/organizers").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$[0].slug").value("new-crew-" + unique));
        assertThat(jdbc.sql("select website_url, photo_url, bio from organizers where slug = :slug").param("slug", "new-crew-" + unique)
                .query((rs, row) -> List.of(rs.getString(1), rs.getString(2), rs.getString(3))).single())
                .containsExactly("https://newcrew.example", "https://pics.example/crew.png", "Forest parties");
        mockMvc.perform(get("/api/organizers/new-pages").param("search", "new crew " + unique))
                .andExpect(jsonPath("$[0].slug").value("new-crew-" + unique));
    }

    @Test
    void aLabelIsNotedAndARejectionSaysWhy() throws Exception {
        String unique = uniqueLetters();
        String token = verifiedUser(unique + "@example.com");
        long label = ask(token, "{\"kind\": \"LABEL\", \"name\": \"Fresh Label %s\", \"websiteUrl\": \"https://fresh.example\"}".formatted(unique));
        long festival = ask(token, "{\"kind\": \"FESTIVAL\", \"name\": \"Tiny Gathering %s\"}".formatted(unique));

        mockMvc.perform(post("/api/admin/page-requests/{id}", label).header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"approve\": true}"))
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.createdSlug").doesNotExist());
        mockMvc.perform(post("/api/admin/page-requests/{id}", festival).header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"approve\": false, \"reason\": \"No psytrance in the line-up\"}"))
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectReason").value("No psytrance in the line-up"));
    }

    @Test
    void onlyConfirmedAccountsAskAndOnlyForPagesTheSiteDoesntHave() throws Exception {
        String username = uniqueUsername();
        register(username, PASSWORD);
        mockMvc.perform(post("/api/page-requests").header("Authorization", bearer(login(username, PASSWORD)))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"kind\": \"LABEL\", \"name\": \"Anything\"}"))
                .andExpect(status().isForbidden());
        String token = verifiedUser(uniqueLetters() + "@example.com");
        mockMvc.perform(post("/api/page-requests").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"kind\": \"FESTIVAL\", \"name\": \"ozora festival\"}"))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/page-requests").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"kind\": \"LABEL\", \"name\": \"Mittai Music\"}"))
                .andExpect(status().isConflict());
        mockMvc.perform(get("/api/admin/page-requests").header("Authorization", bearer(token)))
                .andExpect(status().isForbidden());
    }

    private long ask(String token, String json) throws Exception {
        String body = mockMvc.perform(post("/api/page-requests").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) com.jayway.jsonpath.JsonPath.read(body, "$.id")).longValue();
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
