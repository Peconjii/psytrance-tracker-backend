package com.psytrance.psytrance_tracker_backend.integration;

import com.psytrance.psytrance_tracker_backend.client.GoabaseParty;
import com.psytrance.psytrance_tracker_backend.dto.EventDto;
import com.psytrance.psytrance_tracker_backend.service.ArtistService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** An artist's page with the profile admins write, the gigs they played and how many follow them. */
class ArtistProfileIntegrationTest extends IntegrationTest {

    private static final AtomicLong NEXT_EVENT_ID = new AtomicLong(960_000);
    private static final String ADMIN = "feedback_admin"; // the admin of every integration test
    private static final String PASSWORD = "secret-password-123";

    @Autowired
    private ArtistService artistService;

    private String adminToken;
    private String artistName;
    private String artistSlug;
    private long upcomingGig;
    private long pastGig;

    @BeforeEach
    void anArtistWithAGigAheadAndOneBehind() throws Exception {
        // All tests share one database: the admin account may exist already
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"username": "%s", "email": "%s@example.com", "password": "%s"}
                """.formatted(ADMIN, ADMIN, PASSWORD)));
        adminToken = login(ADMIN, PASSWORD);

        upcomingGig = NEXT_EVENT_ID.getAndIncrement();
        pastGig = NEXT_EVENT_ID.getAndIncrement();
        String unique = UUID.randomUUID().toString().substring(0, 6).replaceAll("\\d", "x");
        artistName = "Profile Test " + unique;
        artistSlug = "profile-test-" + unique;
        // Goabase lists only the upcoming gig; the past one was read while it was still upcoming
        when(goabaseClient.fetchAllParties()).thenReturn(List.of(party(upcomingGig, "Ahead Festival", 10)));
        artistService.saveLineUp(upcomingGig, List.of(artistName), Instant.now());
        artistService.saveLineUp(pastGig, List.of(artistName), Instant.now());
        artistService.saveSummary(EventDto.from(party(pastGig, "Behind Festival", -30)));
    }

    @Test
    void theArtistPageShowsTheProfilePastGigsAndFollowers() throws Exception {
        mockMvc.perform(put("/api/admin/artists/{slug}/profile", artistSlug).header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"photoUrl": "https://img.example.com/a.jpg", "bio": "Twilight from the forest.",
                                 "instagramUrl": "https://instagram.com/test", "bookingEmail": "booking@example.com",
                                 "soundcloudUrl": "  "}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bio").value("Twilight from the forest."))
                .andExpect(jsonPath("$.soundcloudUrl").doesNotExist());

        String follower = uniqueUsername();
        register(follower, PASSWORD);
        mockMvc.perform(put("/api/me/follows/{slug}", artistSlug).header("Authorization", bearer(login(follower, PASSWORD))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/artists/{slug}", artistSlug))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profile.photoUrl").value("https://img.example.com/a.jpg"))
                .andExpect(jsonPath("$.profile.bookingEmail").value("booking@example.com"))
                .andExpect(jsonPath("$.upcomingEvents.length()").value(1))
                .andExpect(jsonPath("$.upcomingEvents[0].nameParty").value("Ahead Festival"))
                .andExpect(jsonPath("$.pastEvents.length()").value(1))
                .andExpect(jsonPath("$.pastEvents[0].name").value("Behind Festival"))
                .andExpect(jsonPath("$.followers").value(1));

        // Logged in the admin history with the parts that were filled in
        mockMvc.perform(get("/api/admin/changes").header("Authorization", bearer(adminToken)))
                .andExpect(jsonPath("$[0].kind").value("ARTIST_PROFILE"))
                .andExpect(jsonPath("$[0].target").value(artistName))
                .andExpect(jsonPath("$[0].after").value("photo, bio, instagram, booking"));
    }

    @Test
    void anArtistWithoutAProfileHasNone() throws Exception {
        mockMvc.perform(get("/api/artists/{slug}", artistSlug))
                .andExpect(jsonPath("$.profile").doesNotExist())
                .andExpect(jsonPath("$.followers").value(0));
    }

    @Test
    void linksMustBeHttpsAndTheArtistMustExist() throws Exception {
        for (String link : List.of("javascript:alert(1)", "http://example.com", "https://has space.com")) {
            mockMvc.perform(put("/api/admin/artists/{slug}/profile", artistSlug).header("Authorization", bearer(adminToken))
                            .contentType(MediaType.APPLICATION_JSON).content("""
                                    {"websiteUrl": "%s"}
                                    """.formatted(link)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.websiteUrl").exists());
        }
        mockMvc.perform(put("/api/admin/artists/{slug}/profile", "nobody-plays-here").header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound());
    }

    private static GoabaseParty party(long id, String name, int daysFromNow) {
        return new GoabaseParty(id, name, LocalDate.now().plusDays(daysFromNow) + "T22:00:00+02:00", null,
                "Festival", "Scheduled", "Portugal", "Idanha-a-Nova", 39.9, -7.2, null, null);
    }
}
