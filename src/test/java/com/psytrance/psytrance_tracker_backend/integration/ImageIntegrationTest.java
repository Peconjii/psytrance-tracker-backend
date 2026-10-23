package com.psytrance.psytrance_tracker_backend.integration;

import com.jayway.jsonpath.JsonPath;
import com.psytrance.psytrance_tracker_backend.service.Images;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Uploaded pictures: who may send one, what comes back, using one as a logo, and clearing the unused ones */
class ImageIntegrationTest extends IntegrationTest {

    private static final String ADMIN = "feedback_admin"; // the admin of every integration test
    private static final String PASSWORD = "secret-password-123";
    private static final String FESTIVAL = "vortex"; // a festival of the map no other test writes
    // The first bytes of a PNG are all the upload checks
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3};

    @Autowired
    private Images images;

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

    private String upload(byte[] data) throws Exception {
        String body = mockMvc.perform(multipart("/api/images").file(new MockMultipartFile("file", "logo.png", "image/png", data))
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.url");
    }

    private static long idOf(String url) {
        return Long.parseLong(url.substring(url.lastIndexOf('/') + 1));
    }

    @Test
    void anUploadedPictureIsShownAndCanBeALogo() throws Exception {
        String url = upload(PNG);
        assertThat(url).matches("http://localhost(:\\d+)?/api/images/\\d+");

        mockMvc.perform(get("/api/images/{id}", idOf(url)))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andExpect(content().bytes(PNG))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));

        mockMvc.perform(put("/api/festivals/profile").param("id", FESTIVAL).header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"logoUrl\": \"%s\"}".formatted(url)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/festivals/page").param("id", FESTIVAL))
                .andExpect(jsonPath("$.profile.logoUrl").value(url));
        // Some other site's plain http address is still refused
        mockMvc.perform(put("/api/festivals/profile").param("id", FESTIVAL).header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"logoUrl\": \"http://example.com/logo.png\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void onlyPicturesFromLoggedInUsers() throws Exception {
        mockMvc.perform(multipart("/api/images").file(new MockMultipartFile("file", "x.png", "image/png", PNG)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(multipart("/api/images").file(new MockMultipartFile("file", "x.png", "image/png", "<svg/>".getBytes()))
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isUnsupportedMediaType());
        mockMvc.perform(get("/api/images/{id}", 999_999_999)).andExpect(status().isNotFound());
    }

    @Test
    void unusedOldPicturesAreDeleted() throws Exception {
        String used = upload(PNG);
        String unused = upload(PNG);
        String fresh = upload(PNG);
        mockMvc.perform(put("/api/festivals/profile").param("id", FESTIVAL).header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"logoUrl\": \"%s\"}".formatted(used)))
                .andExpect(status().isOk());
        jdbc.sql("update images set created_at = created_at - interval '2 days' where id in (:ids)")
                .param("ids", java.util.List.of(idOf(used), idOf(unused))).update();

        images.deleteUnused();

        mockMvc.perform(get("/api/images/{id}", idOf(used))).andExpect(status().isOk());
        mockMvc.perform(get("/api/images/{id}", idOf(unused))).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/images/{id}", idOf(fresh))).andExpect(status().isOk()); // maybe still being saved
    }
}
