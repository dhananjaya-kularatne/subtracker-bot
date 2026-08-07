package com.subtracker.bot.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * Binds the Telegram bot's credentials from application properties.
 */
@Configuration
@Getter
public class TelegramConfig {

    @Value("${telegram.bot.token}")
    private String botToken;

    @Value("${telegram.bot.username}")
    private String botUsername;

    // Base URL for all Telegram Bot API calls — the token is embedded in the path itself
    public String getApiBaseUrl() {
        return "https://api.telegram.org/bot" + botToken;
    }
}