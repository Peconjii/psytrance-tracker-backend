package com.psytrance.psytrance_tracker_backend.integration;

import com.psytrance.psytrance_tracker_backend.client.GoabaseParty;
import com.psytrance.psytrance_tracker_backend.model.FollowNotice;
import com.psytrance.psytrance_tracker_backend.repository.FollowNoticeRepository;
import com.psytrance.psytrance_tracker_backend.repository.UserRepository;
import com.psytrance.psytrance_tracker_backend.service.ArtistFollows;
import com.psytrance.psytrance_tracker_backend.service.ArtistService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Following an artist: the list, the gigs for the gold ships, and the new-gig emails. */
class FollowIntegrationTest extends IntegrationTest {

    private static final AtomicLong NEXT_EVENT_ID = new AtomicLong(950_000);
    private static final String PASSWORD = "secret-password-123";

    @Autowired
    private ArtistService artistService;

    @Autowired
    private ArtistFollows artistFollows;

    @Autowired
    private FollowNoticeRepository noticeRepository;

    @Autowired
    private UserRepository userRepository;

    private long firstGig;
    private long secondGig;
    // Unique per test: all tests share one database
    private String artistName;
    private String artistSlug;

    @BeforeEach
    void anArtistWithOneGig() {
        firstGig = NEXT_EVENT_ID.getAndIncrement();
        secondGig = NEXT_EVENT_ID.getAndIncrement();
        when(goabaseClient.fetchAllParties()).thenReturn(List.of(party(firstGig, 5), party(secondGig, 12)));
        String unique = uniqueLetters();
        artistName = "Gig Hunter " + unique;
        artistSlug = "gig-hunter-" + unique;
        artistService.saveLineUp(firstGig, List.of(artistName), Instant.now());
    }

    @Test
    void followingListsTheArtistAndTheirGigs() throws Exception {
        String token = newUser();
        mockMvc.perform(put("/api/me/follows/{slug}", artistSlug).header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(artistName));
        // Following twice changes nothing
        mockMvc.perform(put("/api/me/follows/{slug}", artistSlug).header("Authorization", bearer(token)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/me/follows").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].slug").value(artistSlug));
        mockMvc.perform(get("/api/me/follows/gigs").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].eventId").value(firstGig))
                .andExpect(jsonPath("$[0].artists[0]").value(artistName));

        mockMvc.perform(delete("/api/me/follows/{slug}", artistSlug).header("Authorization", bearer(token)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/me/follows").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void onlyNewGigsAreEmailedAndEachOnlyOnce() throws Exception {
        String username = uniqueUsername();
        register(username, PASSWORD);
        String token = login(username, PASSWORD);
        long userId = userRepository.findByUsername(username).orElseThrow().getId();
        mockMvc.perform(put("/api/me/follows/{slug}", artistSlug).header("Authorization", bearer(token)))
                .andExpect(status().isOk());

        // The gig they had when the user started following is already known: nothing to email
        artistFollows.sendNewGigEmails();
        assertThat(noticedEvents(userId)).containsExactly(firstGig);

        // A new line-up with the artist is read: that gig is emailed, and only once
        artistService.saveLineUp(secondGig, List.of(artistName, "Someone Else"), Instant.now());
        artistFollows.sendNewGigEmails();
        artistFollows.sendNewGigEmails();
        assertThat(noticedEvents(userId)).containsExactlyInAnyOrder(firstGig, secondGig);
    }

    @Test
    void followingNeedsLoginAndAnArtistFromTheLineUps() throws Exception {
        mockMvc.perform(put("/api/me/follows/{slug}", artistSlug))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/me/follows/{slug}", "nobody-plays-here").header("Authorization", bearer(newUser())))
                .andExpect(status().isNotFound());
    }

    private List<Long> noticedEvents(long userId) {
        return noticeRepository.findByUserId(userId).stream().map(FollowNotice::getEventId).toList();
    }

    private String newUser() throws Exception {
        String username = uniqueUsername();
        register(username, PASSWORD);
        return login(username, PASSWORD);
    }

    private static GoabaseParty party(long id, int daysFromNow) {
        return new GoabaseParty(id, "Follow Fest " + id, LocalDate.now().plusDays(daysFromNow) + "T22:00:00+02:00", null,
                "Festival", "Scheduled", "Portugal", "Idanha-a-Nova", 39.9, -7.2, null, null);
    }
}
