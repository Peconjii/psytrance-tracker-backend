package com.psytrance.psytrance_tracker_backend.service;

import com.psytrance.psytrance_tracker_backend.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Emails password reset links. Spring only creates a {@link JavaMailSender} when
 * {@code spring.mail.host} is set; without it (local development) the link is logged instead.
 * Sent in the background: the "forgot password" form answers at once, whether the mail server is slow or not,
 * and as fast for an unknown address as for a known one (so the answer time doesn't tell who has an account).
 */
@Component
public class PasswordResetMailer {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetMailer.class);

    private final ObjectProvider<JavaMailSender> mailSender;
    private final String from;

    public PasswordResetMailer(ObjectProvider<JavaMailSender> mailSender, @Value("${app.mail.from}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    @Async
    public void sendResetLink(User user, String link, Duration validFor) {
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null) {
            log.info("Mail is not configured - password reset link for user '{}': {}", user.getUsername(), link);
            return;
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(user.getEmail());
        message.setSubject("Reset your Map of Psy password");
        message.setText("""
                Hi %s,

                Someone (hopefully you) asked to reset the password for your Map of Psy account.

                Open this link to choose a new password. It works once and expires in %d minutes:
                %s

                If you didn't ask for this, you can ignore this email and your password stays the same.
                """.formatted(user.getUsername(), validFor.toMinutes(), link));

        try {
            sender.send(message);
        } catch (MailException e) {
            // The user still gets the generic "check your email" answer; the failure is only logged
            log.error("Could not send password reset email to user '{}'", user.getUsername(), e);
        }
    }
}
