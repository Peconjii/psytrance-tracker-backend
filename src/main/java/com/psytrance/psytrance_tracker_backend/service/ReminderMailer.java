package com.psytrance.psytrance_tracker_backend.service;

import com.psytrance.psytrance_tracker_backend.dto.EventDto;
import com.psytrance.psytrance_tracker_backend.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Emails "your events are coming up" reminders. Like {@link PasswordResetMailer}, it only logs
 * the email when no mail server is configured (local development).
 */
@Component
public class ReminderMailer {

    private static final Logger log = LoggerFactory.getLogger(ReminderMailer.class);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH);

    private final ObjectProvider<JavaMailSender> mailSender;
    private final String from;
    private final String frontendUrl;

    public ReminderMailer(ObjectProvider<JavaMailSender> mailSender,
                          @Value("${app.mail.from}") String from,
                          @Value("${app.frontend-url}") String frontendUrl) {
        this.mailSender = mailSender;
        this.from = from;
        this.frontendUrl = frontendUrl.replaceAll("/+$", "");
    }

    /**
     * One email listing all of the user's upcoming events. Returns false only when sending failed,
     * so the reminder job tries again on its next run; a logged email counts as delivered.
     */
    public boolean sendReminder(User user, List<EventDto> events) {
        String subject = events.size() == 1
                ? events.get(0).nameParty() + " is coming up"
                : events.size() + " of your events are coming up";
        String text = """
                Hi %s,

                Starting in the next few days:

                %s

                Have a great time!

                You get this email because you saved these events or marked that you are going.
                You can turn reminders off on your profile: %s/profile
                """.formatted(user.getUsername(), eventList(events), frontendUrl);

        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null) {
            log.info("Mail is not configured - reminder for user '{}':\n{}", user.getUsername(), text);
            return true;
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(user.getEmail());
        message.setSubject(subject);
        message.setText(text);
        try {
            sender.send(message);
            return true;
        } catch (MailException e) {
            log.error("Could not send reminder email to user '{}'", user.getUsername(), e);
            return false;
        }
    }

    private String eventList(List<EventDto> events) {
        return events.stream()
                .map(event -> "- %s%n  %s, %s, %s%n  %s/events/%d".formatted(
                        event.nameParty(),
                        DATE.format(event.dateStart()),
                        event.nameTown(),
                        event.nameCountry(),
                        frontendUrl,
                        event.id()))
                .collect(Collectors.joining("\n\n"));
    }
}
