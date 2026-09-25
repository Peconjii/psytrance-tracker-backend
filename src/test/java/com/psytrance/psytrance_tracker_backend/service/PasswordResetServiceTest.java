package com.psytrance.psytrance_tracker_backend.service;

import com.psytrance.psytrance_tracker_backend.exception.InvalidResetTokenException;
import com.psytrance.psytrance_tracker_backend.model.PasswordResetToken;
import com.psytrance.psytrance_tracker_backend.model.User;
import com.psytrance.psytrance_tracker_backend.repository.PasswordResetTokenRepository;
import com.psytrance.psytrance_tracker_backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PasswordResetServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-25T12:00:00Z");
    private static final Duration TTL = Duration.ofMinutes(30);

    private UserRepository userRepository;
    private PasswordResetTokenRepository tokenRepository;
    private PasswordResetMailer mailer;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private PasswordResetService service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        tokenRepository = mock(PasswordResetTokenRepository.class);
        mailer = mock(PasswordResetMailer.class);
        service = new PasswordResetService(userRepository, tokenRepository, passwordEncoder, mailer,
                Clock.fixed(NOW, ZoneOffset.UTC), TTL, "http://localhost:5173");
    }

    @Test
    void unknownEmailSendsNothingAndDoesNotFail() {
        when(userRepository.findAllByEmailIgnoreCase("nobody@example.com")).thenReturn(List.of());

        service.requestReset("nobody@example.com");

        verify(tokenRepository, never()).save(any());
        verify(mailer, never()).sendResetLink(any(), anyString(), any());
    }

    @Test
    void knownEmailGetsLinkAndOnlyTheHashIsStored() {
        User user = user("milos", "milos@example.com", "old-password");
        when(userRepository.findAllByEmailIgnoreCase("milos@example.com")).thenReturn(List.of(user));

        service.requestReset("  milos@example.com ");

        ArgumentCaptor<String> link = ArgumentCaptor.forClass(String.class);
        verify(mailer).sendResetLink(eq(user), link.capture(), eq(TTL));
        assertThat(link.getValue()).startsWith("http://localhost:5173/reset-password?token=");
        String rawToken = link.getValue().substring(link.getValue().indexOf("token=") + 6);

        ArgumentCaptor<PasswordResetToken> saved = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(tokenRepository).save(saved.capture());
        assertThat(saved.getValue().getTokenHash())
                .isEqualTo(PasswordResetService.sha256(rawToken))
                .isNotEqualTo(rawToken);
        assertThat(saved.getValue().getExpiresAt()).isEqualTo(NOW.plus(TTL));
        verify(tokenRepository).deleteByUser(user); // older links stop working
    }

    @Test
    void validLinkChangesPasswordAndCanOnlyBeUsedOnce() {
        User user = user("milos", "milos@example.com", "old-password");
        PasswordResetToken token = new PasswordResetToken(PasswordResetService.sha256("raw-token"), user, NOW.plus(TTL));
        when(tokenRepository.findByTokenHash(PasswordResetService.sha256("raw-token"))).thenReturn(Optional.of(token));

        service.resetPassword("raw-token", "brand-new-password");

        assertThat(passwordEncoder.matches("brand-new-password", user.getPassword())).isTrue();
        verify(userRepository).save(user);
        verify(tokenRepository).delete(token);
    }

    @Test
    void expiredLinkIsRejectedAndPasswordStaysTheSame() {
        User user = user("milos", "milos@example.com", "old-password");
        String oldHash = user.getPassword();
        PasswordResetToken token = new PasswordResetToken(PasswordResetService.sha256("raw-token"), user, NOW.minusSeconds(1));
        when(tokenRepository.findByTokenHash(PasswordResetService.sha256("raw-token"))).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> service.resetPassword("raw-token", "brand-new-password"))
                .isInstanceOf(InvalidResetTokenException.class);
        assertThat(user.getPassword()).isEqualTo(oldHash);
        verify(userRepository, never()).save(any());
    }

    @Test
    void unknownLinkIsRejected() {
        when(tokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resetPassword("made-up", "brand-new-password"))
                .isInstanceOf(InvalidResetTokenException.class);
    }

    private User user(String username, String email, String password) {
        User user = new User();
        user.setId(1L);
        user.setUsername(username);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(password));
        return user;
    }
}
