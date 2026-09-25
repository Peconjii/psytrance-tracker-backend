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
import java.util.Base64;
import java.util.HexFormat;

/**
 * "Forgot password" flow: email a one-time link, then accept a new password for it.
 */
@Service
public class PasswordResetService {

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordResetMailer mailer;
    private final Clock clock;
    private final Duration tokenTtl;
    private final String frontendUrl;
    private final SecureRandom random = new SecureRandom();

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
