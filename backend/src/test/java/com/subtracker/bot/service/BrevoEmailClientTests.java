package com.subtracker.bot.service;

import com.subtracker.bot.config.BrevoConfig;
import com.subtracker.bot.config.TelegramConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class BrevoEmailClientTests {
    private MockRestServiceServer server;
    private BrevoConfig config;
    private BrevoEmailClient client;

    @BeforeEach
    void setUp() throws IOException {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        config = mock(BrevoConfig.class);
        when(config.isConfigured()).thenReturn(true);
        when(config.getApiKey()).thenReturn("test-key");
        when(config.getSenderEmail()).thenReturn("sender@example.com");
        when(config.getSenderName()).thenReturn("Subtracker Bot");
        TelegramConfig telegram = mock(TelegramConfig.class);
        when(telegram.getBotUsername()).thenReturn("subtracker_dj_bot");
        client = new BrevoEmailClient(builder.build(), config, new NotificationEmailTemplate(telegram));
    }

    @Test
    void acceptedEmailPostsHtmlAndPlainTextWithWorkingTelegramLink() {
        server.expect(requestTo("https://api.brevo.com/v3/smtp/email"))
                .andExpect(header("api-key", "test-key"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.sender.email").value("sender@example.com"))
                .andExpect(jsonPath("$.to[0].email").value("user@example.com"))
                .andExpect(jsonPath("$.subject").value("Reminder"))
                .andExpect(jsonPath("$.textContent").value(org.hamcrest.Matchers.containsString("Renewal tomorrow")))
                .andExpect(jsonPath("$.textContent").value(org.hamcrest.Matchers.containsString("https://t.me/subtracker_dj_bot")))
                .andExpect(jsonPath("$.htmlContent").value(org.hamcrest.Matchers.containsString("Open Subtracker in Telegram")))
                .andExpect(jsonPath("$.htmlContent").value(org.hamcrest.Matchers.containsString("Renewal tomorrow")))
                .andRespond(withStatus(HttpStatus.CREATED));
        assertTrue(client.sendEmail("user@example.com", "Reminder", "Renewal tomorrow"));
        server.verify();
    }

    @Test
    void rejectedEmailReturnsFalse() {
        server.expect(requestTo("https://api.brevo.com/v3/smtp/email"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        assertFalse(client.sendEmail("user@example.com", "Reminder", "Text"));
        server.verify();
    }

    @Test
    void connectionFailureReturnsFalse() {
        server.expect(requestTo("https://api.brevo.com/v3/smtp/email"))
                .andRespond(withException(new IOException("Simulated connection failure")));
        assertFalse(client.sendEmail("user@example.com", "Reminder", "Text"));
        server.verify();
    }

    @Test
    void missingConfigurationMakesNoHttpRequest() {
        when(config.isConfigured()).thenReturn(false);
        assertFalse(client.sendEmail("user@example.com", "Reminder", "Text"));
        server.verify();
    }

    @Test
    void missingRecipientMakesNoHttpRequest() {
        assertFalse(client.sendEmail(null, "Reminder", "Text"));
        assertFalse(client.sendEmail(" ", "Reminder", "Text"));
        server.verify();
    }
}
