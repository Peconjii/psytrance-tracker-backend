package com.psytrance.psytrance_tracker_backend.client;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

/** Which ID tokens count as a Google sign-in for this site, with a test key in place of Google's */
class GoogleIdTokensTest {

    private static final String CLIENT_ID = "test-client.apps.googleusercontent.com";
    private static KeyPair googleKey;
    private static KeyPair otherKey;

    @BeforeAll
    static void keys() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        googleKey = generator.generateKeyPair();
        otherKey = generator.generateKeyPair();
    }

    private static GoogleIdTokens tokens(String clientId) {
        return new GoogleIdTokens(clientId, NimbusJwtDecoder.withPublicKey((RSAPublicKey) googleKey.getPublic()).build());
    }

    /** A token as Google makes it, signed with {@code key}, changed by {@code change} */
    private static String token(KeyPair key, Consumer<JWTClaimsSet.Builder> change) throws Exception {
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                .issuer("https://accounts.google.com")
                .audience(CLIENT_ID)
                .subject("110169484474386276334")
                .claim("email", "someone@gmail.com")
                .claim("email_verified", true)
                .claim("name", "Some One")
                .issueTime(new Date())
                .expirationTime(Date.from(Instant.now().plusSeconds(3600)));
        change.accept(claims);
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims.build());
        jwt.sign(new RSASSASigner(key.getPrivate()));
        return jwt.serialize();
    }

    @Test
    void aGoogleTokenForThisSiteGivesTheAccount() throws Exception {
        assertThat(tokens(CLIENT_ID).verify(token(googleKey, claims -> { })))
                .hasValueSatisfying(account -> {
                    assertThat(account.id()).isEqualTo("110169484474386276334");
                    assertThat(account.email()).isEqualTo("someone@gmail.com");
                    assertThat(account.name()).isEqualTo("Some One");
                });
        // Google also writes the issuer without https://
        assertThat(tokens(CLIENT_ID).verify(token(googleKey, claims -> claims.issuer("accounts.google.com")))).isPresent();
    }

    @Test
    void tokensThatArentAValidGoogleSignInForThisSiteAreRefused() throws Exception {
        GoogleIdTokens tokens = tokens(CLIENT_ID);
        assertThat(tokens.verify(token(otherKey, claims -> { }))).as("signed by someone else").isEmpty();
        assertThat(tokens.verify(token(googleKey, claims -> claims.audience("other-site")))).as("for another site").isEmpty();
        assertThat(tokens.verify(token(googleKey, claims -> claims.issuer("https://evil.example")))).as("not from Google").isEmpty();
        assertThat(tokens.verify(token(googleKey, claims -> claims.expirationTime(Date.from(Instant.now().minusSeconds(3600))))))
                .as("expired").isEmpty();
        assertThat(tokens.verify(token(googleKey, claims -> claims.claim("email_verified", false)))).as("unconfirmed email").isEmpty();
        assertThat(tokens.verify("not a token")).isEmpty();
    }

    @Test
    void withoutAClientIdItIsOff() throws Exception {
        GoogleIdTokens off = tokens("");
        assertThat(off.isEnabled()).isFalse();
        assertThat(off.verify(token(googleKey, claims -> claims.audience("")))).isEmpty();
    }
}
