package com.psytrance.psytrance_tracker_backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/** Emails an admin's answer to a feedback message; without mail configured (local development) it is logged. */
@Component
public class FeedbackReplyMailer {

    private static final Logger log = LoggerFactory.getLogger(FeedbackReplyMailer.class);

    private final ObjectProvider<JavaMailSender> mailSender;
    private final String from;

    public FeedbackReplyMailer(ObjectProvider<JavaMailSender> mailSender, @Value("${app.mail.from}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    /** Sends the answer, quoting the message. A failure is thrown (MailException), so the admin hears of it. */
    public void send(String to, String reply, String originalMessage) {
        JavaMailSender sender = mailSender.getIfAvailable();
        String text = """
                %s

                ---
                You wrote to Map of Psy:
                > %s
                """.formatted(reply, originalMessage.replace("\n", "\n> "));
        if (sender == null) {
            log.info("Mail is not configured - feedback reply to {}:\n{}", to, text);
            return;
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject("Re: your message to Map of Psy");
        message.setText(text);
        sender.send(message);
    }
}
