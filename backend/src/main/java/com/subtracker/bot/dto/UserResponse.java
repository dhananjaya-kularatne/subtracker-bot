package com.subtracker.bot.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Fields the client is allowed to see about a User.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserResponse {

    private Long id;
    private Long telegramChatId;
    private Integer reminderDaysBefore;
    private String defaultCurrency;
    private LocalDateTime createdAt;
}