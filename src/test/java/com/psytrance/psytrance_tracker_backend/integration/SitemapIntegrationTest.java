package com.psytrance.psytrance_tracker_backend.integration;

import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The sitemap for search engines lists the public pages, open to anyone */
class SitemapIntegrationTest extends IntegrationTest {

    @Test
    void listsThePublicPages() throws Exception {
        mockMvc.perform(get("/api/sitemap.xml"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/xml"))
                .andExpect(content().string(containsString("<urlset")))
                .andExpect(content().string(containsString("/events</loc>")))
                // A map artist, a label (a space in its name) and a festival of the map
                .andExpect(content().string(containsString("/artists/derango</loc>")))
                .andExpect(content().string(containsString("/labels/Iboga%20Records</loc>")))
                .andExpect(content().string(containsString("/festivals/ozora</loc>")))
                .andExpect(content().string(not(containsString("/admin"))))
                .andExpect(content().string(not(containsString("/profile"))));
    }
}
