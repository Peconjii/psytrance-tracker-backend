package com.psytrance.psytrance_tracker_backend.config;

import com.psytrance.psytrance_tracker_backend.client.BrevoMailSender;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.mail.autoconfigure.MailSenderAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import static org.assertj.core.api.Assertions.assertThat;

/** Which mail sender the app gets: Brevo whenever its key is set (even with the old SMTP settings still there) */
class MailConfigTest {

    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(MailSenderAutoConfiguration.class))
            .withUserConfiguration(MailConfig.class);

    @Test
    void brevoWinsOverTheOldSmtpSettings() {
        context.withPropertyValues("app.mail.brevo-api-key=test-key", "spring.mail.host=smtp.gmail.com",
                        "app.mail.from=Map of Psy <psyatlasapp@gmail.com>")
                .run(app -> assertThat(app).getBean(JavaMailSender.class).isInstanceOf(BrevoMailSender.class));
    }

    @Test
    void withoutTheKeyItIsSmtpOrNothing() {
        context.withPropertyValues("app.mail.brevo-api-key=", "spring.mail.host=smtp.gmail.com")
                .run(app -> assertThat(app).getBean(JavaMailSender.class).isInstanceOf(JavaMailSenderImpl.class));
        context.withPropertyValues("app.mail.brevo-api-key=")
                .run(app -> assertThat(app).doesNotHaveBean(JavaMailSender.class));
    }
}
