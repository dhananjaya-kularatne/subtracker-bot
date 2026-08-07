package com.subtracker.bot.service;

import com.subtracker.bot.dto.TelegramUpdate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Parses incoming Telegram messages into commands and routes them to the appropriate handler. This is the "brain" behind the bot's chat interface —
 * it never touches HTTP directly, and never touches Telegram's API directly except through TelegramApiClient.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TelegramCommandService {

    private final TelegramApiClient telegramApiClient;

    public void handleUpdate(TelegramUpdate update) {

        // Ignore updates that aren't a plain text message
        if (update.getMessage() == null || update.getMessage().getText() == null) {
            return;
        }

        Long chatId = update.getMessage().getChat().getId();
        String text = update.getMessage().getText().trim();

        log.info("Received message from chatId {}: {}", chatId, text);

        // Route based on the first word of the message
        String command = text.split("\\s+")[0].toLowerCase();

        switch (command) {
            case "/start" -> handleStart(chatId);
            default -> handleUnknownCommand(chatId);
        }
    }

    private void handleStart(Long chatId) {
        String welcomeMessage = """
                Hi! I'll help you track your subscriptions and remind you before you get charged.

                Commands coming soon:
                /add - add a subscription
                /list - see your subscriptions
                /delete - remove a subscription
                """;
        telegramApiClient.sendMessage(chatId, welcomeMessage);
    }

    private void handleUnknownCommand(Long chatId) {
        telegramApiClient.sendMessage(chatId,
                "Sorry, I don't recognize that command yet. Try /start to see what I can do.");
    }
}