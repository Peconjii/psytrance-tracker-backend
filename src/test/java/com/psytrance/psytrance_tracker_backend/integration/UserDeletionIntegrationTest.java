package com.psytrance.psytrance_tracker_backend.integration;

import com.psytrance.psytrance_tracker_backend.model.AdminChange;
import com.psytrance.psytrance_tracker_backend.repository.AdminChangeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Admin → Users → Delete: the account and what was only theirs go; the site keeps the rest */
class UserDeletionIntegrationTest extends IntegrationTest {

    private static final String ADMIN = "feedback_admin";
    private static final String PASSWORD = "secret-password-123";

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private AdminChangeRepository changeRepository;

    private String adminToken;

    @BeforeEach
    void admin() throws Exception {
        // The admin may already exist: all test classes share one database
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"username": "%s", "email": "%s@example.com", "password": "%s"}
                """.formatted(ADMIN, ADMIN, PASSWORD)));
        adminToken = login(ADMIN, PASSWORD);
    }

    @Test
    void anAdminDeletesAnAccountItCanNoLongerLogInAndItIsInTheHistory() throws Exception {
        String username = uniqueUsername();
        register(username, "password123");
        login(username, "password123");

        mockMvc.perform(delete("/api/admin/users/{u}", username).header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk());

        assertThat(jdbc.sql("select count(*) from app_user where username = :u").param("u", username)
                .query(Long.class).single()).isZero();
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"%s\", \"password\": \"password123\"}".formatted(username)))
                .andExpect(status().is4xxClientError());
        assertThat(changeRepository.findTop200ByOrderByCreatedAtDescIdDesc()).anyMatch(c ->
                AdminChange.USER_DELETED.equals(c.getKind()) && username.equals(c.getTarget()));

        // The name is free again
        register(username, "password456");
    }

    @Test
    void nobodyDeletesThemselvesAndOnlyAdminsDelete() throws Exception {
        mockMvc.perform(delete("/api/admin/users/{u}", ADMIN).header("Authorization", bearer(adminToken)))
                .andExpect(status().isConflict());

        String username = uniqueUsername();
        register(username, "password123");
        String token = login(username, "password123");
        mockMvc.perform(delete("/api/admin/users/{u}", ADMIN).header("Authorization", bearer(token)))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/admin/users/{u}", "no-such-account-xyz").header("Authorization", bearer(adminToken)))
                .andExpect(status().isNotFound());
    }
}
