package com.psytrance.psytrance_tracker_backend.integration;

import com.psytrance.psytrance_tracker_backend.dto.EventDto;
import com.psytrance.psytrance_tracker_backend.service.ArtistService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** What a shared link shows in chat apps: the page's own name, line and picture */
class SharePreviewIntegrationTest extends IntegrationTest {

    @Autowired
    private ArtistService artistService;

    @Test
    void eachKindOfPageHasItsOwnCard() throws Exception {
        String unique = uniqueLetters();
        long event = 999_700L + (long) (Math.random() * 200);
        artistService.saveLineUp(event, List.of("Share & Act " + unique), Instant.now());
        artistService.saveSummary(new EventDto(event, "Share Night " + unique, "Vienna", "Austria", "Club", "Scheduled",
                LocalDate.now().plusDays(3), null, null, null, 1.0, 1.0));

        mockMvc.perform(get("/api/share/artists/{slug}", "share-act-" + unique))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/html"))
                // The name escaped, the gig counted, the address of the real page
                .andExpect(content().string(containsString("og:title\" content=\"Share &amp; Act " + unique + " · Map of Psy\"")))
                .andExpect(content().string(containsString("1 gig coming up")))
                .andExpect(content().string(containsString("/artists/share-act-" + unique + "\"")));
        mockMvc.perform(get("/api/share/events/{id}", event))
                .andExpect(content().string(containsString("Share Night " + unique)))
                .andExpect(content().string(containsString("Vienna, Austria")));
        mockMvc.perform(get("/api/share/labels/{name}", "Iboga Records"))
                .andExpect(content().string(containsString("og:title\" content=\"Iboga Records · Map of Psy\"")))
                .andExpect(content().string(containsString("/labels/Iboga%20Records")));
        mockMvc.perform(get("/api/share/festivals/{id}", "ozora"))
                .andExpect(content().string(containsString("Ozora Festival · Map of Psy")));
        // Nothing known: the site's own card, with its picture
        mockMvc.perform(get("/api/share/artists/{slug}", "nobody-" + unique))
                .andExpect(content().string(containsString("og:title\" content=\"Map of Psy · Map of Psy\"")
                        ))
                .andExpect(content().string(containsString("/og-image.png")))
                .andExpect(content().string(not(containsString("nobody-"))));
    }
}
