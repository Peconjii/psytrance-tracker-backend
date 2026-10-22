package com.psytrance.psytrance_tracker_backend.service;

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

/** The weekly email (see WeeklyDigest); without mail configured (local development) it is logged */
@Component
public class DigestMailer {

    private static final Logger log = LoggerFactory.getLogger(DigestMailer.class);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH);

    private final ObjectProvider<JavaMailSender> mailSender;
    private final String from;
    private final String frontendUrl;

    public DigestMailer(ObjectProvider<JavaMailSender> mailSender, @Value("${app.mail.from}") String from,
                        @Value("${app.frontend-url}") String frontendUrl) {
        this.mailSender = mailSender;
        this.from = from;
        this.frontendUrl = frontendUrl.replaceAll("/+$", "");
    }

    /** Returns false only when sending failed, so the job tries again; a logged email counts as sent */
    public boolean send(String username, String to, List<WeeklyDigest.Gig> gigs) {
        String list = gigs.stream()
                .map(gig -> "%s · %s%n  %s · %s%n  %s/events/%d".formatted(
                        gig.dateStart().format(DATE), gig.name(), String.join(", ", gig.artists()),
                        List.of(gig.town(), gig.country()).stream().filter(x -> x != null && !x.isBlank())
                                .collect(Collectors.joining(", ")),
                        frontendUrl, gig.eventId()))
                .collect(Collectors.joining("\n\n"));
        String text = """
                Hi %s,

                Where the artists and labels you follow play in the next two weeks:

                %s

                See you on the dancefloor!

                You get this email on Mondays because you follow artists or labels on Map of Psy.
                Turn it off on your profile: %s/profile
                """.formatted(username, list, frontendUrl);
        String subject = gigs.size() == 1 ? "This week on Map of Psy: 1 gig of who you follow"
                : "This week on Map of Psy: " + gigs.size() + " gigs of who you follow";
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null) {
            log.info("Mail is not configured - weekly email to {} ({}):\n{}", to, subject, text);
            return true;
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(text);
        try {
            sender.send(message);
            return true;
        } catch (MailException e) {
            log.warn("Could not send the weekly email to {}", to, e);
            return false;
        }
    }
}
