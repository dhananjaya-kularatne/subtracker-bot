package com.subtracker.bot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.subtracker.bot.config.TelegramConfig;
import com.subtracker.bot.dto.TelegramUpdate;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Continuously polls Telegram for new updates using long polling.
 *
 * Runs on its own background thread so it never blocks the main application.
 * Each update pulled here is fed into the exact same TelegramCommandService used by the webhook controller polling and webhook are just two
 * different ways of feeding the same pipeline.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TelegramPollingService {

    private final RestClient restClient;
    private final TelegramConfig telegramConfig;
    private final TelegramCommandService telegramCommandService;
    private final ObjectMapper objectMapper;

    // Tracks the highest update_id we've already processed, so getUpdates knows not to send it to us again
    private long lastUpdateId = 0;

    /**
     * Starts the polling loop on a dedicated background thread the moment the application finishes starting up.
     */
    @PostConstruct
    public void startPolling() {
        Thread pollingThread = new Thread(this::pollLoop);
        pollingThread.setDaemon(true); // don't block JVM shutdown
        pollingThread.setName("telegram-polling-thread");
        pollingThread.start();
        log.info("Telegram long polling started.");
    }

    private void pollLoop() {
        while (true) {
            try {
                fetchAndProcessUpdates();
            } catch (Exception exception) {
                log.error("Error during Telegram polling: {}", exception.getMessage());
                // Avoid a tight error loop hammering Telegram's API on repeated failures
                sleep(5000);
            }
        }
    }

    private void fetchAndProcessUpdates() {
        String url = telegramConfig.getApiBaseUrl()
                + "/getUpdates?offset=" + (lastUpdateId + 1) + "&timeout=30";

        String response = restClient.get()
                .uri(url)
                .retrieve()
                .body(String.class);

        JsonNode root = parseResponse(response);
        if (root == null || !root.path("ok").asBoolean(false)) {
            return;
        }

        for (JsonNode updateNode : root.path("result")) {
            TelegramUpdate update = objectMapper.convertValue(updateNode, TelegramUpdate.class);

            if (update.getUpdateId() != null) {
                lastUpdateId = Math.max(lastUpdateId, update.getUpdateId());
            }

            telegramCommandService.handleUpdate(update);
        }
    }

    private JsonNode parseResponse(String response) {
        try {
            return objectMapper.readTree(response);
        } catch (Exception exception) {
            log.error("Failed to parse Telegram getUpdates response: {}", exception.getMessage());
            return null;
        }
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
        }
    }
}