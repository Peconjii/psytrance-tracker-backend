package com.psytrance.psytrance_tracker_backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Tells someone what an admin decided about what they sent: a page claim, a new page request, an added event.
 * The profile shows it too, so a mail that can't be sent is only logged and never undoes the decision.
 * Without mail configured (local development) the mail is logged.
 */
@Component
public class DecisionMailer {

    private static final Logger log = LoggerFactory.getLogger(DecisionMailer.class);

    private final ObjectProvider<JavaMailSender> mailSender;
    private final String from;
    private final String frontendUrl;

    public DecisionMailer(ObjectProvider<JavaMailSender> mailSender, @Value("${app.mail.from}") String from,
                          @Value("${app.frontend-url}") String frontendUrl) {
        this.mailSender = mailSender;
        this.from = from;
        this.frontendUrl = frontendUrl.replaceAll("/+$", "");
    }

    /**
     * {@code what}: what they sent, as in "Your claim of Ajja"; {@code path}: the page to open ("/artists/ajja"),
     * or null; {@code reason}: why not, when rejected (may be null).
     */
    public void send(String to, String what, boolean approved, String approvedNote, String path, String reason) {
        if (to == null || to.isBlank()) {
            return;
        }
        String text = approved
                ? "%s on Map of Psy was approved. %s".formatted(what, approvedNote)
                : "%s on Map of Psy was not accepted.%s".formatted(what,
                        reason == null || reason.isBlank() ? "" : "\n\nThe admin wrote: " + reason.strip());
        if (path != null) {
            text += "\n\n" + frontendUrl + path;
        }
        text += "\n\nYou see this on your profile too: " + frontendUrl + "/profile";
        String subject = approved ? what + ": approved" : what + ": not accepted";
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null) {
            log.info("Mail is not configured - decision mail to {} ({}):\n{}", to, subject, text);
            return;
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(text);
        try {
            sender.send(message);
        } catch (MailException e) {
            log.warn("Could not send the decision mail to {} ({})", to, subject, e);
        }
    }
}
