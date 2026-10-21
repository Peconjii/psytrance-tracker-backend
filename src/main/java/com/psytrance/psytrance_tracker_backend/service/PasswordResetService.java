package com.psytrance.psytrance_tracker_backend.service;

import com.psytrance.psytrance_tracker_backend.exception.InvalidResetTokenException;
import com.psytrance.psytrance_tracker_backend.model.PasswordResetToken;
import com.psytrance.psytrance_tracker_backend.model.User;
import com.psytrance.psytrance_tracker_backend.repository.PasswordResetTokenRepository;
import com.psytrance.psytrance_tracker_backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Base64;
import java.util.Deque;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * "Forgot password" flow: email a one-time link, then accept a new password for it.
 */
@Service
public class PasswordResetService {

    /** Reset emails one address can get per hour; more are skipped without telling, so nobody can flood an inbox */
    static final int MAX_EMAILS_PER_HOUR = 3;
    private static final Duration WINDOW = Duration.ofHours(1);

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordResetMailer mailer;
    private final Clock clock;
    private final Duration tokenTtl;
    private final String frontendUrl;
    private final SecureRandom random = new SecureRandom();
    // When each address was last sent reset emails, within the hour. The deques are only touched inside compute
    // calls, which the map runs one at a time per address. In memory: a restart forgets it, which is fine.
    private final Map<String, Deque<Instant>> sentByEmail = new ConcurrentHashMap<>();

    public PasswordResetService(UserRepository userRepository,
                                PasswordResetTokenRepository tokenRepository,
                                PasswordEncoder passwordEncoder,
                                PasswordResetMailer mailer,
                                Clock clock,
                                @Value("${app.password-reset.token-ttl}") Duration tokenTtl,
                                @Value("${app.frontend-url}") String frontendUrl) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.mailer = mailer;
        this.clock = clock;
        this.tokenTtl = tokenTtl;
        this.frontendUrl = frontendUrl;
    }

    /**
     * Emails a reset link to every account with this email. Does nothing for an unknown email,
     * and the caller answers the same either way, so the endpoint can't reveal who has an account.
     */
    @Transactional
    public void requestReset(String email) {
        if (!mayEmail(email.trim().toLowerCase(Locale.ROOT))) {
            return;
        }
        // Email isn't unique, so one address can own several accounts; each gets its own link
        for (User user : userRepository.findAllByEmailIgnoreCase(email.trim())) {
            tokenRepository.deleteByUser(user); // only the newest link works

            String rawToken = newRawToken();
            tokenRepository.save(new PasswordResetToken(sha256(rawToken), user, clock.instant().plus(tokenTtl)));
            mailer.sendResetLink(user, frontendUrl + "/reset-password?token=" + rawToken, tokenTtl);
        }
    }

    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        PasswordResetToken token = tokenRepository.findByTokenHash(sha256(rawToken))
                .filter(t -> !t.isExpired(clock.instant()))
                .orElseThrow(InvalidResetTokenException::new);

        User user = token.getUser();
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        tokenRepository.delete(token); // a link works only once
    }

    // True when this address got fewer than MAX_EMAILS_PER_HOUR requests in the last hour, and counts this one
    private boolean mayEmail(String email) {
        Instant now = clock.instant();
        Instant windowStart = now.minus(WINDOW);
        // Addresses quiet for an hour are forgotten, so the map doesn't grow with every address ever typed
        for (String key : sentByEmail.keySet()) {
            sentByEmail.computeIfPresent(key, (k, times) -> times.peekLast().isBefore(windowStart) ? null : times);
        }
        boolean[] allowed = {false};
        sentByEmail.compute(email, (key, times) -> {
            Deque<Instant> kept = times == null ? new ArrayDeque<>() : times;
            while (!kept.isEmpty() && kept.peekFirst().isBefore(windowStart)) {
                kept.pollFirst();
            }
            if (kept.size() < MAX_EMAILS_PER_HOUR) {
                kept.addLast(now);
                allowed[0] = true;
            }
            return kept.isEmpty() ? null : kept;
        });
        return allowed[0];
    }

    // 32 random bytes, URL-safe so it can go straight into the link
    private String newRawToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String sha256(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available in the JDK", e);
        }
    }
}
