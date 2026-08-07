package com.subtracker.bot.controller;

import com.subtracker.bot.dto.TelegramUpdate;
import com.subtracker.bot.service.TelegramCommandService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Receives incoming updates from Telegram whenever a user messages the bot.
 *
 * Always returns 200 OK regardless of what happened internally — Telegram only cares that we acknowledged receipt. Any real error handling (failed
 * sends, bad commands) is handled inside TelegramCommandService and replied to the user directly as a chat message, not as an HTTP error.
 */
@RestController
@RequestMapping("/api/v1/telegram")
@RequiredArgsConstructor
public class TelegramWebhookController {

    private final TelegramCommandService telegramCommandService;

    @PostMapping("/webhook")
    public ResponseEntity<Void> receiveUpdate(@RequestBody TelegramUpdate update) {
        telegramCommandService.handleUpdate(update);
        return ResponseEntity.ok().build();
    }
}