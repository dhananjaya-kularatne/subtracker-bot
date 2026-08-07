package com.subtracker.bot.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * Represents the "chat" object — this is where the chat ID lives, which is the user's identity (User.telegramChatId).
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TelegramChat {

    private Long id;
}