package com.psytrance.psytrance_tracker_backend.config;

import com.psytrance.psytrance_tracker_backend.filter.JwtFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;
import java.util.stream.Stream;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtFilter jwtFilter;
    private final Admins admins;
    private final String frontendUrl;

    public SecurityConfig(JwtFilter jwtFilter, Admins admins, @Value("${app.frontend-url}") String frontendUrl) {
        this.jwtFilter = jwtFilter;
        this.admins = admins;
        // A trailing slash would never match the browser's Origin header
        this.frontendUrl = frontendUrl.replaceAll("/+$", "");
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/api/auth/login", "/api/auth/register",
                                "/api/auth/forgot-password", "/api/auth/reset-password", "/api/auth/verify-email").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/events/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/reviews/event/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/artists/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/organizers/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/labels/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/map/**").permitAll()
                        // The keep-awake ping of an outside cron job (see HealthController)
                        .requestMatchers(HttpMethod.GET, "/api/health").permitAll()
                        // Guests can send feedback; reading it is for admins (checked in FeedbackController)
                        .requestMatchers(HttpMethod.POST, "/api/feedback").permitAll()
                        // Spring renders errors like "no such endpoint" at /error; if that needed a token,
                        // a guest would get a misleading 401 instead of the real 404
                        .requestMatchers("/error").permitAll()
                        // The admin side as a whole, so an endpoint that forgets Admins.requireAdmin is still closed.
                        // Guests get 401 (log in first), other users 403
                        .requestMatchers("/api/admin/**").access((authentication, context) ->
                                new AuthorizationDecision(admins.isAdmin(authentication.get().getName())))
                        // Everything else (reviews POST, favorites, "I'm going", /api/me/..., /api/auth/me) requires a valid token
                        .anyRequest().authenticated()
                )
                // Missing or invalid token -> 401 "log in first" (the default would be 403 "forbidden")
                .exceptionHandling(ex -> ex.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // The deployed frontend (FRONTEND_URL) plus the local dev server. distinct() matters: locally
        // FRONTEND_URL defaults to http://localhost:5173, and Set.of would throw on that duplicate
        configuration.setAllowedOrigins(
                Stream.of(frontendUrl, "http://localhost:5173", "http://127.0.0.1:5173").distinct().toList());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Requested-With"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}