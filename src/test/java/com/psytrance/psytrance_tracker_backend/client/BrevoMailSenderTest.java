package com.psytrance.psytrance_tracker_backend.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** What goes to Brevo's API for one of the site's emails, and how a refusal comes back */
class BrevoMailSenderTest {

    private MockRestServiceServer brevo;
    private BrevoMailSender sender;

    @BeforeEach
    void aMockBrevo() {
        RestClient.Builder builder = RestClient.builder();
        brevo = MockRestServiceServer.bindTo(builder).build();
        sender = new BrevoMailSender(builder, "https://api.brevo.com", "test-key");
    }

    private static SimpleMailMessage message() {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom("Map of Psy <psyatlasapp@gmail.com>");
        message.setTo("someone@example.com");
        message.setSubject("Confirm your Map of Psy email");
        message.setText("Open this link: https://psyatlas-one.vercel.app/verify-email?token=abc");
        return message;
    }

    @Test
    void sendsTheMailAsBrevoJson() {
        brevo.expect(requestTo("https://api.brevo.com/v3/smtp/email"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("api-key", "test-key"))
                .andExpect(content().json("""
                        {"sender": {"name": "Map of Psy", "email": "psyatlasapp@gmail.com"},
                         "to": [{"email": "someone@example.com"}],
                         "subject": "Confirm your Map of Psy email",
                         "textContent": "Open this link: https://psyatlas-one.vercel.app/verify-email?token=abc"}
                        """))
                .andRespond(withSuccess("{\"messageId\": \"<1@smtp-relay.brevo.com>\"}", MediaType.APPLICATION_JSON));

        sender.send(message());

        brevo.verify();
    }

    @Test
    void aRefusalIsAMailExceptionTheMailersLog() {
        // E.g. a sender Brevo hasn't verified
        brevo.expect(requestTo("https://api.brevo.com/v3/smtp/email"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).body("{\"code\": \"invalid_parameter\"}").contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> sender.send(message())).isInstanceOf(MailException.class);
    }

    @Test
    void anEmailWithoutAnAddressIsRefusedBeforeSending() {
        SimpleMailMessage noRecipient = message();
        noRecipient.setTo(new String[0]);

        assertThatThrownBy(() -> sender.send(noRecipient)).isInstanceOf(MailException.class);
        brevo.verify();
    }
}
