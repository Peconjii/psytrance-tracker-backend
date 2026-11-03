package com.psytrance.psytrance_tracker_backend.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;

/**
 * Checks the ID token the "Sign in with Google" button gives the browser: signed with one of Google's public keys,
 * made by Google for this site's client id, not expired, and for an email Google has confirmed. Without a client id
 * Google sign-in is off.
 */
@Component
public class GoogleIdTokens {

    private static final Logger log = LoggerFactory.getLogger(GoogleIdTokens.class);
    private static final String GOOGLE_KEYS = "https://www.googleapis.com/oauth2/v3/certs";
    private static final Set<String> GOOGLE_ISSUERS = Set.of("accounts.google.com", "https://accounts.google.com");

    /** {@code id}: Google's id for the account, the same for good even when its email changes */
    public record GoogleAccount(String id, String email, String name) {
    }

    private final String clientId;
    private final NimbusJwtDecoder decoder;

    @Autowired
    public GoogleIdTokens(@Value("${app.google.client-id:}") String clientId) {
        this(clientId, NimbusJwtDecoder.withJwkSetUri(GOOGLE_KEYS).restOperations(withTimeouts()).build());
    }

    /** For tests: tokens signed with a test key instead of Google's */
    GoogleIdTokens(String clientId, NimbusJwtDecoder decoder) {
        this.clientId = clientId;
        this.decoder = decoder;
        OAuth2TokenValidator<Jwt> fromGoogleForThisSite = jwt ->
                GOOGLE_ISSUERS.contains(jwt.getClaimAsString("iss")) && jwt.getAudience() != null
                        && jwt.getAudience().contains(clientId)
                        ? OAuth2TokenValidatorResult.success()
                        : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Not a Google sign-in for this site", null));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(new JwtTimestampValidator(), fromGoogleForThisSite));
    }

    private static RestTemplate withTimeouts() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(10));
        return new RestTemplate(factory);
    }

    public boolean isEnabled() {
        return clientId != null && !clientId.isBlank();
    }

    /** The Google account the token is for, or empty for a token that isn't valid (or Google sign-in being off) */
    public Optional<GoogleAccount> verify(String idToken) {
        if (!isEnabled()) {
            return Optional.empty();
        }
        try {
            Jwt jwt = decoder.decode(idToken);
            String email = jwt.getClaimAsString("email");
            // Only an email Google has confirmed may find an existing account with that email
            if (email == null || email.isBlank() || !Boolean.TRUE.equals(jwt.getClaimAsBoolean("email_verified"))) {
                return Optional.empty();
            }
            return Optional.of(new GoogleAccount(jwt.getSubject(), email, jwt.getClaimAsString("name")));
        } catch (JwtException e) {
            log.info("Refused a Google sign-in token: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
