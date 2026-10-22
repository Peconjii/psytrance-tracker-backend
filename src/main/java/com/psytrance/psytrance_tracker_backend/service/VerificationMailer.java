package com.psytrance.psytrance_tracker_backend.service;

import com.psytrance.psytrance_tracker_backend.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** Emails the link that confirms a new account's email; without mail configured (local development) it is logged. */
@Component
public class VerificationMailer {

    private static final Logger log = LoggerFactory.getLogger(VerificationMailer.class);

    private final ObjectProvider<JavaMailSender> mailSender;
    private final String from;

    public VerificationMailer(ObjectProvider<JavaMailSender> mailSender, @Value("${app.mail.from}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    public void sendVerificationLink(User user, String link, Duration validFor) {
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null) {
            log.info("Mail is not configured - email verification link for user '{}': {}", user.getUsername(), link);
            return;
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(user.getEmail());
        message.setSubject("Confirm your Map of Psy email");
        message.setText("""
                Hi %s,

                Welcome to Map of Psy! Open this link to confirm your email. It works once and expires in %d hours:
                %s

                Once it is confirmed, you can add events to the map.
                If you didn't make this account, you can ignore this email.
                """.formatted(user.getUsername(), validFor.toHours(), link));

        try {
            sender.send(message);
        } catch (MailException e) {
            // The account works anyway; the user can ask for a new link from the profile
            log.error("Could not send the verification email to user '{}'", user.getUsername(), e);
        }
    }
}
