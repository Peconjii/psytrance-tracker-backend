package com.psytrance.psytrance_tracker_backend.integration;

import com.psytrance.psytrance_tracker_backend.dto.EventDto;
import com.psytrance.psytrance_tracker_backend.model.OurEvent;
import com.psytrance.psytrance_tracker_backend.repository.UserRepository;
import com.psytrance.psytrance_tracker_backend.service.ArtistService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** "Delete my account": the account and what is the person's go; their feedback stays without the account */
class AccountDeletionIntegrationTest extends IntegrationTest {

    private static final String PASSWORD = "secret-password-123";

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ArtistService artistService;

    @Autowired
    private JdbcClient jdbc;

    @Test
    void theAccountAndWhatIsTheirsAreGone() throws Exception {
        String unique = uniqueLetters();
        String username = uniqueUsername();
        register(username, PASSWORD);
        var user = userRepository.findByUsername(username).orElseThrow();
        user.setEmailVerified(true);
        userRepository.save(user);
        long userId = user.getId();
        String token = bearer(login(username, PASSWORD));

        // Things that are theirs: a follow, a label follow, an added event the site kept, a message
        artistService.saveLineUp(999_950L + (long) (Math.random() * 40), java.util.List.of("Followed Act " + unique), java.time.Instant.now());
        mockMvc.perform(put("/api/me/follows/{slug}", "followed-act-" + unique).header("Authorization", token)).andExpect(status().isOk());
        mockMvc.perform(put("/api/me/label-follows").param("label", "Iboga Records").header("Authorization", token))
                .andExpect(status().isOk());
        String added = mockMvc.perform(post("/api/our-events").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Gone Party %s", "eventType": "Club", "startsAt": "%sT22:00:00", "timeZone": "Europe/Belgrade",
                                 "country": "Serbia", "town": "Novi Sad", "lat": 45.25, "lon": 19.84, "lineUp": "Somebody", "genres": []}
                                """.formatted(unique, LocalDate.now().plusDays(20))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long eventId = OurEvent.ID_BASE + ((Number) com.jayway.jsonpath.JsonPath.read(added, "$.id")).longValue();
        artistService.saveSummary(new EventDto(eventId, "Gone Party " + unique, "Novi Sad", "Serbia", "Club", "Scheduled",
                LocalDate.now().plusDays(20), null, null, null, 45.25, 19.84));
        mockMvc.perform(post("/api/feedback").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\": \"OTHER\", \"message\": \"Bye from %s\"}".formatted(unique)))
                .andExpect(status().isCreated());

        // The password again; a wrong one changes nothing
        mockMvc.perform(post("/api/me/delete-account").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\": \"not-it\"}"))
                .andExpect(status().isForbidden());
        assertThat(userRepository.findByUsername(username)).isPresent();

        mockMvc.perform(post("/api/me/delete-account").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\": \"%s\"}".formatted(PASSWORD)))
                .andExpect(status().isNoContent());

        assertThat(userRepository.findByUsername(username)).isEmpty();
        assertThat(count("select count(*) from artist_follows where user_id = " + userId)).isZero();
        assertThat(count("select count(*) from label_follows where user_id = " + userId)).isZero();
        assertThat(count("select count(*) from our_events where submitted_by = " + userId)).isZero();
        assertThat(count("select count(*) from event_summaries where event_id = " + eventId)).isZero();
        // The message stays, without the account
        assertThat(count("select count(*) from feedback where message = 'Bye from " + unique + "' and user_id is null")).isEqualTo(1);
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"%s\", \"password\": \"%s\"}".formatted(username, PASSWORD)))
                .andExpect(status().isUnauthorized());
    }

    private long count(String sql) {
        return jdbc.sql(sql).query(Long.class).single();
    }
}
