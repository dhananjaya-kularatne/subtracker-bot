package com.subtracker.bot.service;

import com.subtracker.bot.config.TelegramConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Thin wrapper around the Telegram Bot API's HTTP endpoints.
 * This is the only class in the app that knows how to actually talk to Telegram — everything else just calls sendMessage() and doesn't care
 * about URLs, request shapes, or the RestClient underneath.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TelegramApiClient {

    private final RestClient restClient;
    private final TelegramConfig telegramConfig;

    /**
     * Sends a plain text message to a given Telegram chat.
     * Used for command replies (/add confirmations, /list output) and, later, scheduled reminders.
     */
    public void sendMessage(Long chatId, String text) {
        String url = telegramConfig.getApiBaseUrl() + "/sendMessage";

        try {
            restClient.post()
                    .uri(url)
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .body(new SendMessageRequest(chatId, text))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception exception) {
            // A failed send (e.g. user blocked the bot) should never crash the caller — log it and move on. Retry/fallback logic can be
            // layered on top of this later if needed.
            log.error("Failed to send Telegram message to chatId {}: {}", chatId, exception.getMessage());
        }
    }

    /**
     * Request body shape Telegram's sendMessage endpoint expects.
     */
    private record SendMessageRequest(Long chat_id, String text) {
    }
}