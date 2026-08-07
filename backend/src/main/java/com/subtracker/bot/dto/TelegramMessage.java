package com.subtracker.bot.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * Represents the "message" portion of a Telegram Update — the actual text sent, and which chat it came from.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TelegramMessage {

    private TelegramChat chat;

    private String text;
}