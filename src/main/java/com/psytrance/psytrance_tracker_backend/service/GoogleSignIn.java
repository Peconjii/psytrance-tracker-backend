package com.psytrance.psytrance_tracker_backend.service;

import com.psytrance.psytrance_tracker_backend.client.GoogleIdTokens.GoogleAccount;
import com.psytrance.psytrance_tracker_backend.model.User;
import com.psytrance.psytrance_tracker_backend.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Comparator;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Which account a Google sign-in logs into: the one already linked to that Google account; else the oldest account
 * whose email is confirmed and the same as the Google one (it gets linked); else a new account with the username the
 * person chose. An account whose email nobody confirmed is never taken over, because anyone could have typed that
 * email when registering.
 */
@Service
public class GoogleSignIn {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public GoogleSignIn(UserRepository userRepository, PasswordEncoder passwordEncoder, Clock clock) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    /** The account to log in, or empty when this Google account has none yet and no username was chosen. */
    @Transactional
    public Optional<User> accountFor(GoogleAccount google, String username) {
        Optional<User> linked = userRepository.findByGoogleId(google.id());
        if (linked.isPresent()) {
            return linked;
        }
        Optional<User> sameEmail = userRepository.findAllByEmailIgnoreCase(google.email()).stream()
                .filter(user -> user.isEmailVerified() && user.getGoogleId() == null)
                .min(Comparator.comparing(User::getId));
        if (sameEmail.isPresent()) {
            sameEmail.get().setGoogleId(google.id());
            return sameEmail;
        }
        if (username == null || username.isBlank()) {
            return Optional.empty();
        }
        User user = new User();
        user.setUsername(username.trim());
        user.setEmail(google.email());
        user.setEmailVerified(true); // Google has confirmed it
        user.setGoogleId(google.id());
        // A password nobody knows: the account signs in with Google, or "Forgot password" sets a real one
        user.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
        user.setCreatedAt(clock.instant());
        // Flushed here, so a taken username is a 409 from GlobalExceptionHandler and not an error at commit
        return Optional.of(userRepository.saveAndFlush(user));
    }

    /** A free username from the Google name (or the email before the @), for the person to keep or change */
    public String suggestUsername(GoogleAccount google) {
        String from = google.name() != null && !google.name().isBlank() ? google.name() : google.email().split("@")[0];
        String base = from.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
        if (base.length() > 30) {
            base = base.substring(0, 30);
        }
        if (base.length() < 3) {
            base = "raver";
        }
        String suggestion = base;
        for (int n = 2; userRepository.existsByUsername(suggestion); n++) {
            suggestion = base + "_" + n;
        }
        return suggestion;
    }
}
