package com.psytrance.psytrance_tracker_backend.client;

import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.springframework.http.MediaType;
import org.springframework.mail.MailException;
import org.springframework.mail.MailParseException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.InputStream;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Sends the site's emails through Brevo's HTTP API (port 443) instead of SMTP: Render blocks outgoing SMTP ports, so
 * smtp.gmail.com:587 can't be reached from there. It stands in for Spring's {@link JavaMailSender}, so every mailer
 * works unchanged (they only send {@link SimpleMailMessage}s). The sender in "From" must be one verified in Brevo.
 * Set up in {@code config.MailConfig} when BREVO_API_KEY is set.
 */
public class BrevoMailSender implements JavaMailSender {

    private final RestClient restClient;

    /** builder: with the timeouts set (tests bind a mock server to it); baseUrl: https://api.brevo.com */
    public BrevoMailSender(RestClient.Builder builder, String baseUrl, String apiKey) {
        this.restClient = builder.baseUrl(baseUrl).defaultHeader("api-key", apiKey).build();
    }

    @Override
    public void send(SimpleMailMessage... messages) throws MailException {
        for (SimpleMailMessage message : messages) {
            try {
                restClient.post()
                        .uri("/v3/smtp/email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .body(body(message))
                        .retrieve()
                        .toBodilessEntity();
            } catch (RestClientException e) {
                // A MailException, so each mailer logs it like an SMTP failure and the user still gets their answer
                throw new MailSendException("Brevo did not send the email: " + e.getMessage(), e);
            }
        }
    }

    // {"sender": {"name", "email"}, "to": [{"email"}], "subject", "textContent", "replyTo"?}
    private static Map<String, Object> body(SimpleMailMessage message) {
        if (message.getFrom() == null || message.getTo() == null || message.getTo().length == 0) {
            throw new MailParseException("An email needs a sender and a recipient");
        }
        InternetAddress from = address(message.getFrom());
        Map<String, Object> sender = new LinkedHashMap<>();
        sender.put("email", from.getAddress());
        if (from.getPersonal() != null) {
            sender.put("name", from.getPersonal());
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sender", sender);
        body.put("to", Arrays.stream(message.getTo()).map(to -> Map.of("email", address(to).getAddress())).toList());
        body.put("subject", message.getSubject() == null ? "" : message.getSubject());
        body.put("textContent", message.getText() == null ? "" : message.getText());
        if (message.getReplyTo() != null) {
            body.put("replyTo", Map.of("email", address(message.getReplyTo()).getAddress()));
        }
        return body;
    }

    // "Map of Psy <psyatlasapp@gmail.com>" or a bare address
    private static InternetAddress address(String value) {
        try {
            return new InternetAddress(value, true);
        } catch (AddressException e) {
            throw new MailParseException("Not an email address: " + value, e);
        }
    }

    // ---- The mailers send plain text only; MIME messages are not needed ----

    @Override
    public MimeMessage createMimeMessage() {
        throw new UnsupportedOperationException("Brevo mail sends plain text messages only");
    }

    @Override
    public MimeMessage createMimeMessage(InputStream contentStream) throws MailException {
        throw new UnsupportedOperationException("Brevo mail sends plain text messages only");
    }

    @Override
    public void send(MimeMessage... mimeMessages) throws MailException {
        throw new UnsupportedOperationException("Brevo mail sends plain text messages only");
    }
}
