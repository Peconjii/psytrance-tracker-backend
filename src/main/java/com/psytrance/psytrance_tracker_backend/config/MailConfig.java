package com.psytrance.psytrance_tracker_backend.config;

import com.psytrance.psytrance_tracker_backend.client.BrevoMailSender;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Which way the site's emails go out. With BREVO_API_KEY set: Brevo's HTTP API (Render blocks SMTP). Without it:
 * Spring's SMTP sender when SPRING_MAIL_HOST is set, else nothing and the mailers write the links to the log.
 */
@Configuration
public class MailConfig {

    @Bean
    @Primary
    @ConditionalOnExpression("!'${app.mail.brevo-api-key:}'.isBlank()")
    public JavaMailSender brevoMailSender(@Value("${app.mail.brevo-api-key}") String apiKey,
                                          @Value("${app.mail.brevo-url:https://api.brevo.com}") String baseUrl) {
        // A mail service that doesn't answer gives up after 10 s, like the SMTP settings
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(10));
        return new BrevoMailSender(RestClient.builder().requestFactory(requestFactory), baseUrl, apiKey.strip());
    }
}
