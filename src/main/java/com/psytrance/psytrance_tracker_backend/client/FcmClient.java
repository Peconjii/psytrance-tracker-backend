package com.psytrance.psytrance_tracker_backend.client;

import io.jsonwebtoken.Jwts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.json.JsonParserFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Map;

/**
 * Sends push notifications to the Android app through Firebase Cloud Messaging (HTTP v1). The Firebase service
 * account (its JSON, as it is or in base64) comes from FIREBASE_CREDENTIALS; without it push is off and nothing is
 * sent. The service account signs a short token that Google trades for an access token, kept for most of its hour.
 */
@Component
public class FcmClient {

    private static final Logger log = LoggerFactory.getLogger(FcmClient.class);
    private static final String SCOPE = "https://www.googleapis.com/auth/firebase.messaging";

    /** What became of one notification: sent, the phone's token is gone for good (app removed), or it failed */
    public enum Result { SENT, TOKEN_GONE, FAILED }

    private final String projectId;
    private final String clientEmail;
    private final String tokenUri;
    private final PrivateKey key;
    private final RestClient http;
    private String accessToken;
    private Instant accessTokenUntil = Instant.EPOCH;

    public FcmClient(@Value("${app.push.firebase-credentials:}") String credentials) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(10));
        this.http = RestClient.builder().requestFactory(factory).build();
        String json = credentials == null ? "" : credentials.strip();
        if (json.isEmpty()) {
            projectId = clientEmail = tokenUri = null;
            key = null;
            return;
        }
        if (!json.startsWith("{")) {
            json = new String(Base64.getMimeDecoder().decode(json), StandardCharsets.UTF_8);
        }
        Map<String, Object> account = JsonParserFactory.getJsonParser().parseMap(json);
        projectId = (String) account.get("project_id");
        clientEmail = (String) account.get("client_email");
        tokenUri = (String) account.getOrDefault("token_uri", "https://oauth2.googleapis.com/token");
        key = privateKey((String) account.get("private_key"));
    }

    private static PrivateKey privateKey(String pem) {
        try {
            String base64 = pem.replaceAll("-----[A-Z ]+-----", "").replaceAll("\\s", "");
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(base64)));
        } catch (Exception e) {
            throw new IllegalStateException("FIREBASE_CREDENTIALS has no usable private key", e);
        }
    }

    public boolean isEnabled() {
        return key != null;
    }

    /** One notification to one phone; {@code path} is the page the app opens when it is tapped */
    public Result send(String token, String title, String body, String path) {
        if (!isEnabled()) {
            return Result.FAILED;
        }
        Map<String, Object> message = Map.of("message", Map.of(
                "token", token,
                "notification", Map.of("title", title, "body", body),
                "data", Map.of("path", path),
                "android", Map.of("priority", "high")));
        try {
            http.post().uri("https://fcm.googleapis.com/v1/projects/{p}/messages:send", projectId)
                    .header("Authorization", "Bearer " + accessToken())
                    .contentType(MediaType.APPLICATION_JSON).body(message).retrieve().toBodilessEntity();
            return Result.SENT;
        } catch (RestClientResponseException e) {
            HttpStatusCode status = e.getStatusCode();
            // 404 UNREGISTERED: the app was removed or its token renewed; 400 INVALID_ARGUMENT on a bad token
            if (status.value() == 404 || (status.value() == 400 && e.getResponseBodyAsString().contains("registration token"))) {
                return Result.TOKEN_GONE;
            }
            log.warn("Push notification failed: {} {}", status.value(), e.getResponseBodyAsString());
            return Result.FAILED;
        } catch (Exception e) {
            log.warn("Push notification failed: {}", e.getMessage());
            return Result.FAILED;
        }
    }

    private synchronized String accessToken() {
        Instant now = Instant.now();
        if (accessToken != null && now.isBefore(accessTokenUntil)) {
            return accessToken;
        }
        String assertion = Jwts.builder().issuer(clientEmail).audience().single(tokenUri)
                .claim("scope", SCOPE).issuedAt(Date.from(now)).expiration(Date.from(now.plusSeconds(3600)))
                .signWith(key, Jwts.SIG.RS256).compact();
        LinkedMultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer");
        form.add("assertion", assertion);
        String response = http.post().uri(tokenUri).contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form)
                .retrieve().body(String.class);
        Map<String, Object> parsed = JsonParserFactory.getJsonParser().parseMap(response);
        accessToken = (String) parsed.get("access_token");
        accessTokenUntil = now.plusSeconds(((Number) parsed.getOrDefault("expires_in", 3600)).longValue() - 300);
        return accessToken;
    }
}
