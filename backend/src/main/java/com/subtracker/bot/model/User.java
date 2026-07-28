package com.subtracker.bot.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Represents a Telegram user of the bot.
 *
 * The Telegram chatId is the user's identity in this system
 * a User record is created for them automatically.
 */
@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Telegram's chat ID — uniquely identifies this user, acts as their login
    @Column(name = "telegram_chat_id", nullable = false, unique = true)
    private Long telegramChatId;

    // How many days before renewal to send a reminder (user-configurable later)
    @Column(name = "reminder_days_before", nullable = false)
    private Integer reminderDaysBefore;

    // Default currency for this user's subscriptions unless overridden per-subscription
    @Column(name = "default_currency", nullable = false, length = 10)
    private String defaultCurrency;

    // Auto-set on INSERT only — Hibernate stamps this, never touched afterward
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}