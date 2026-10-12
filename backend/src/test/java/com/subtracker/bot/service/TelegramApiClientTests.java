package com.subtracker.bot.service;

import com.subtracker.bot.config.TelegramConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class TelegramApiClientTests {

    private MockRestServiceServer server;
    private TelegramApiClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        TelegramConfig config = mock(TelegramConfig.class);
        when(config.getApiBaseUrl()).thenReturn("https://api.telegram.org/bottest");
        client = new TelegramApiClient(builder.build(), config);
    }

    @Test
    void successfulSendPostsExpectedPayloadAndReturnsTrue() {
        server.expect(requestTo("https://api.telegram.org/bottest/sendMessage"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("{\"chat_id\":123,\"text\":\"Hello\"}"))
                .andRespond(withSuccess("{\"ok\":true}", MediaType.APPLICATION_JSON));

        assertTrue(client.sendMessage(123L, "Hello"));
        server.verify();
    }

    @Test
    void blockedChatReturnsFalseWithoutThrowing() {
        server.expect(requestTo("https://api.telegram.org/bottest/sendMessage"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertFalse(client.sendMessage(123L, "Hello"));
        server.verify();
    }

    @Test
    void connectionFailureReturnsFalseWithoutThrowing() {
        server.expect(requestTo("https://api.telegram.org/bottest/sendMessage"))
                .andRespond(withException(new IOException("Simulated connection failure")));

        assertFalse(client.sendMessage(123L, "Hello"));
        server.verify();
    }
}
