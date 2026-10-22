package com.psytrance.psytrance_tracker_backend.service;

import com.psytrance.psytrance_tracker_backend.model.EmailVerificationToken;
import com.psytrance.psytrance_tracker_backend.model.User;
import com.psytrance.psytrance_tracker_backend.repository.EmailVerificationTokenRepository;
import com.psytrance.psytrance_tracker_backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

/**
 * Confirming a user's email: a one-time link is mailed when they register (and again when they ask), and opening it
 * marks the account verified. Only verified accounts may add events.
 */
@Service
public class EmailVerification {

    /** How long a user waits before asking for another link, so the button can't flood an inbox */
    static final Duration RESEND_AFTER = Duration.ofMinutes(2);

    private final EmailVerificationTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final VerificationMailer mailer;
    private final Clock clock;
    private final Duration tokenTtl;
    private final String frontendUrl;
    private final SecureRandom random = new SecureRandom();

    public EmailVerification(EmailVerificationTokenRepository tokenRepository, UserRepository userRepository,
                             VerificationMailer mailer, Clock clock,
                             @Value("${app.email-verification.token-ttl}") Duration tokenTtl,
                             @Value("${app.frontend-url}") String frontendUrl) {
        this.tokenRepository = tokenRepository;
        this.userRepository = userRepository;
        this.mailer = mailer;
        this.clock = clock;
        this.tokenTtl = tokenTtl;
        this.frontendUrl = frontendUrl;
    }

    /** Mails a new link (only the newest one works). Nothing for a verified account or one without an email. */
    @Transactional
    public void sendLink(User user) {
        if (user.isEmailVerified() || user.getEmail() == null || user.getEmail().isBlank()) {
            return;
        }
        tokenRepository.deleteByUser(user);
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant now = clock.instant();
        tokenRepository.save(new EmailVerificationToken(PasswordResetService.sha256(rawToken), user, now, now.plus(tokenTtl)));
        mailer.sendVerificationLink(user, frontendUrl + "/verify-email?token=" + rawToken, tokenTtl);
    }

    /** "Send the link again" from the profile: 429 when the last link went out less than RESEND_AFTER ago */
    @Transactional
    public void resend(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        if (user.isEmailVerified()) {
            return;
        }
        boolean justSent = tokenRepository.findFirstByUserOrderByCreatedAtDesc(user)
                .filter(last -> last.getCreatedAt().plus(RESEND_AFTER).isAfter(clock.instant()))
                .isPresent();
        if (justSent) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "A link was just sent. Check your inbox (and spam), or try again in a few minutes.");
        }
        sendLink(user);
    }

    /** Opens a link: the account is verified and the link stops working. Returns the username. */
    @Transactional
    public String verify(String rawToken) {
        EmailVerificationToken token = tokenRepository.findByTokenHash(PasswordResetService.sha256(rawToken))
                .filter(t -> !t.isExpired(clock.instant()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "This link is invalid or has expired. Log in and ask for a new one on your profile."));
        User user = token.getUser();
        user.setEmailVerified(true);
        userRepository.save(user);
        tokenRepository.deleteByUser(user);
        return user.getUsername();
    }
}
