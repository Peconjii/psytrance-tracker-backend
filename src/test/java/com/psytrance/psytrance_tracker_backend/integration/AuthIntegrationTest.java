package com.psytrance.psytrance_tracker_backend.integration;

import com.psytrance.psytrance_tracker_backend.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthIntegrationTest extends IntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    void registeredUserCanLogInAndSeeTheirAccountWithoutPassword() throws Exception {
        String username = uniqueUsername();
        register(username, "password123");
        String token = login(username, "password123");

        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(username))
                .andExpect(jsonPath("$.email").value(username + "@example.com"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void passwordIsStoredAsBcryptHashAndNeverReturned() throws Exception {
        String username = uniqueUsername();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "%s", "email": "a@example.com", "password": "password123"}
                                """.formatted(username)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.password").doesNotExist());

        String stored = userRepository.findByUsername(username).orElseThrow().getPassword();
        assertThat(stored).isNotEqualTo("password123").startsWith("$2"); // "$2a$..." is a BCrypt hash
    }

    @Test
    void registeringATakenUsernameIsAConflict() throws Exception {
        String username = uniqueUsername();
        register(username, "password123");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "%s", "email": "other@example.com", "password": "password123"}
                                """.formatted(username)))
                .andExpect(status().isConflict());
    }

    @Test
    void invalidRegistrationReturnsWhichFieldsAreWrong() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "ab", "email": "not-an-email", "password": "short"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.username").exists())
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.password").exists());
    }

    @Test
    void wrongPasswordIsRejected() throws Exception {
        String username = uniqueUsername();
        register(username, "password123");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "%s", "password": "wrong-password"}
                                """.formatted(username)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpointsNeedAValidToken() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer("not.a.real-token")))
                .andExpect(status().isUnauthorized());
    }
}
