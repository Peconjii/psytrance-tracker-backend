package com.psytrance.psytrance_tracker_backend.integration;

import com.jayway.jsonpath.JsonPath;
import com.psytrance.psytrance_tracker_backend.client.GoabaseParty;
import com.psytrance.psytrance_tracker_backend.service.ArtistService;
import com.psytrance.psytrance_tracker_backend.service.MapPlacements;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.hamcrest.Matchers.hasItem;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * An artist editing their own profile: an admin links their account; they edit the profile, tracks and festivals
 * directly, and ask for genres and a name, which an admin approves or rejects.
 */
class ArtistOwnerIntegrationTest extends IntegrationTest {

    private static final AtomicLong NEXT_EVENT_ID = new AtomicLong(970_000);
    private static final String ADMIN = "feedback_admin"; // the admin of every integration test
    private static final String PASSWORD = "secret-password-123";

    @Autowired
    private ArtistService artistService;

    @Autowired
    private MapPlacements mapPlacements;

    private String adminToken;
    private String ownerToken;
    private String artistName;
    private String artistSlug;

    @BeforeEach
    void anArtistWithAnOwner() throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"username": "%s", "email": "%s@example.com", "password": "%s"}
                """.formatted(ADMIN, ADMIN, PASSWORD)));
        adminToken = login(ADMIN, PASSWORD);

        long gig = NEXT_EVENT_ID.getAndIncrement();
        String unique = uniqueLetters();
        artistName = "Owner Test " + unique;
        artistSlug = "owner-test-" + unique;
        when(goabaseClient.fetchAllParties()).thenReturn(List.of(new GoabaseParty(gig, "Owner Fest",
                LocalDate.now().plusDays(7) + "T22:00:00+02:00", null, "Festival", "Scheduled", "Portugal", "Lisbon",
                38.7, -9.1, null, null)));
        artistService.saveLineUp(gig, List.of(artistName), Instant.now());

        String owner = uniqueUsername();
        register(owner, PASSWORD);
        ownerToken = login(owner, PASSWORD);
        mockMvc.perform(put("/api/admin/artists/{slug}/owner", artistSlug).header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"username\": \"%s\"}".formatted(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.owner").value(owner));
    }

    @AfterEach
    void offTheMapAgain() {
        // All tests share one map: an approved genre request is taken back
        mapPlacements.setGenres(artistName, List.of(), ADMIN);
    }

    @Test
    void theOwnerEditsTheProfileTracksAndFestivals() throws Exception {
        mockMvc.perform(get("/api/me/artists").header("Authorization", bearer(ownerToken)))
                .andExpect(jsonPath("$[0].slug").value(artistSlug));

        mockMvc.perform(put("/api/artists/{slug}/profile", artistSlug).header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"bio\": \"Written by the artist.\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/artists/{slug}/tracks", artistSlug).header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"tracks": [{"title": "First Light", "url": "https://soundcloud.com/x/first-light"},
                                            {"title": "Deep Roots", "url": "https://youtu.be/abc"}]}
                                """))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/artists/{slug}/festivals", artistSlug).header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"festivals": [{"name": "Jungle Island", "year": 2025}, {"name": "Ozora", "url": ""}]}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/artists/{slug}", artistSlug))
                .andExpect(jsonPath("$.profile.bio").value("Written by the artist."))
                .andExpect(jsonPath("$.tracks[1].title").value("Deep Roots"))
                .andExpect(jsonPath("$.festivals[0].year").value(2025))
                .andExpect(jsonPath("$.festivals[1].url").doesNotExist());
    }

    @Test
    void onlyTheOwnerAndAdminsMayEditAndListsHaveLimits() throws Exception {
        String stranger = uniqueUsername();
        register(stranger, PASSWORD);
        mockMvc.perform(put("/api/artists/{slug}/profile", artistSlug).header("Authorization", bearer(login(stranger, PASSWORD)))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/artists/{slug}/edit", artistSlug))
                .andExpect(status().isUnauthorized());
        // Admins see who owns the profile; the owner doesn't need to
        mockMvc.perform(get("/api/artists/{slug}/edit", artistSlug).header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.owner").exists());

        String sevenTracks = "{\"tracks\": [" + String.join(",", List.of("1", "2", "3", "4", "5", "6", "7").stream()
                .map(n -> "{\"title\": \"T" + n + "\", \"url\": \"https://example.com/" + n + "\"}").toList()) + "]}";
        mockMvc.perform(put("/api/artists/{slug}/tracks", artistSlug).header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON).content(sevenTracks))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/artists/{slug}/tracks", artistSlug).header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"tracks": [{"title": "Unsafe", "url": "javascript:alert(1)"}]}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void genresAndTheNameWaitForAnAdmin() throws Exception {
        String genresRequest = mockMvc.perform(post("/api/artists/{slug}/requests", artistSlug).header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"kind\": \"GENRES\", \"genres\": [\"forest\", \"twilight\"]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn().getResponse().getContentAsString();
        String nameRequest = mockMvc.perform(post("/api/artists/{slug}/requests", artistSlug).header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"kind\": \"NAME\", \"name\": \"Owner Test Live\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        mockMvc.perform(post("/api/artists/{slug}/requests", artistSlug).header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"kind\": \"GENRES\", \"genres\": [\"not-a-genre\"]}"))
                .andExpect(status().isBadRequest());

        // Nothing changed yet: the map and the page wait for an admin
        mockMvc.perform(get("/api/map/placements"))
                .andExpect(jsonPath("$.forest", org.hamcrest.Matchers.not(hasItem(artistName))));
        mockMvc.perform(get("/api/admin/artists/requests").header("Authorization", bearer(adminToken)))
                .andExpect(jsonPath("$[*].artistSlug", hasItem(artistSlug)));

        long genresId = ((Number) JsonPath.read(genresRequest, "$.id")).longValue();
        long nameId = ((Number) JsonPath.read(nameRequest, "$.id")).longValue();
        mockMvc.perform(post("/api/admin/artists/requests/{id}", genresId).header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"approve\": true}"))
                .andExpect(jsonPath("$.status").value("APPROVED"));
        mockMvc.perform(post("/api/admin/artists/requests/{id}", nameId).header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"approve\": false}"))
                .andExpect(jsonPath("$.status").value("REJECTED"));
        // Decided once
        mockMvc.perform(post("/api/admin/artists/requests/{id}", nameId).header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"approve\": true}"))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/api/map/placements"))
                .andExpect(jsonPath("$.forest", hasItem(artistName)))
                .andExpect(jsonPath("$.twilight", hasItem(artistName)));
        mockMvc.perform(get("/api/artists/{slug}", artistSlug))
                .andExpect(jsonPath("$.name").value(artistName));
    }

    @Test
    void theOwnerPicksTheRoleAndLiveGigsAreCounted() throws Exception {
        // A second gig where the line-up wrote "live" next to the artist: half of their gigs are live
        artistService.saveLineUp(NEXT_EVENT_ID.getAndIncrement(), List.of(artistName), Set.of(artistSlug), Instant.now());
        mockMvc.perform(get("/api/artists/{slug}", artistSlug))
                .andExpect(jsonPath("$.role").doesNotExist())
                .andExpect(jsonPath("$.liveShare").value(0.5));

        mockMvc.perform(put("/api/artists/{slug}/role", artistSlug).header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\": \"live\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("LIVE"));
        mockMvc.perform(get("/api/artists").param("search", artistName))
                .andExpect(jsonPath("$[0].role").value("LIVE"));
        mockMvc.perform(put("/api/artists/{slug}/role", artistSlug).header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\": \"ROCKSTAR\"}"))
                .andExpect(status().isBadRequest());
        // Back to the guess
        mockMvc.perform(put("/api/artists/{slug}/role", artistSlug).header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\": null}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/artists/{slug}", artistSlug))
                .andExpect(jsonPath("$.role").doesNotExist());
    }

    @Test
    void anApprovedNameIsShownOnThePage() throws Exception {
        String request = mockMvc.perform(post("/api/artists/{slug}/requests", artistSlug).header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"kind\": \"NAME\", \"name\": \"Owner Test Live\"}"))
                .andReturn().getResponse().getContentAsString();
        mockMvc.perform(post("/api/admin/artists/requests/{id}", ((Number) JsonPath.read(request, "$.id")).longValue())
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"approve\": true}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/artists/{slug}", artistSlug))
                .andExpect(jsonPath("$.name").value("Owner Test Live"));
    }

    @Test
    void aTrustedOwnersChangesSkipTheAdmins() throws Exception {
        String trustBody = "{\"kind\": \"ARTIST\", \"key\": \"%s\", \"trusted\": %s}";
        mockMvc.perform(get("/api/me/page-trust").param("kind", "ARTIST").param("key", artistSlug).header("Authorization", bearer(ownerToken)))
                .andExpect(jsonPath("$.trusted").value(false));

        mockMvc.perform(put("/api/admin/page-trust").header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON).content(trustBody.formatted(artistSlug, true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trusted").value(true));
        // Now a new name is on the page at once, with no admin in between
        mockMvc.perform(post("/api/artists/{slug}/requests", artistSlug).header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"kind\": \"NAME\", \"name\": \"Owner Test Trusted\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("APPROVED"));
        mockMvc.perform(get("/api/artists/{slug}", artistSlug)).andExpect(jsonPath("$.name").value("Owner Test Trusted"));

        // Someone else may not read it; a new owner starts untrusted
        String other = uniqueUsername();
        register(other, PASSWORD);
        String otherToken = login(other, PASSWORD);
        mockMvc.perform(get("/api/me/page-trust").param("kind", "ARTIST").param("key", artistSlug).header("Authorization", bearer(otherToken)))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/admin/artists/{slug}/owner", artistSlug).header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"username\": \"%s\"}".formatted(other)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/me/page-trust").param("kind", "ARTIST").param("key", artistSlug).header("Authorization", bearer(otherToken)))
                .andExpect(jsonPath("$.owner").value(other))
                .andExpect(jsonPath("$.trusted").value(false));
        mockMvc.perform(post("/api/artists/{slug}/requests", artistSlug).header("Authorization", bearer(otherToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"kind\": \"NAME\", \"name\": \"Owner Test Again\"}"))
                .andExpect(jsonPath("$.status").value("PENDING"));
        // Admin → Users shows the account with its page
        mockMvc.perform(get("/api/admin/users").param("search", other).header("Authorization", bearer(adminToken)))
                .andExpect(jsonPath("$.users[0].pages[0].kind").value("ARTIST"))
                .andExpect(jsonPath("$.users[0].pages[0].key").value(artistSlug))
                .andExpect(jsonPath("$.users[0].pages[0].trusted").value(false));
        // Only admins trust
        mockMvc.perform(put("/api/admin/page-trust").header("Authorization", bearer(otherToken))
                        .contentType(MediaType.APPLICATION_JSON).content(trustBody.formatted(artistSlug, true)))
                .andExpect(status().isForbidden());
    }
}
