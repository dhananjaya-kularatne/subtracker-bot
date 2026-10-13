package com.subtracker.bot.service;

import com.subtracker.bot.dto.UserResponse;
import com.subtracker.bot.exception.ResourceNotFoundException;
import com.subtracker.bot.model.User;
import com.subtracker.bot.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Currency;
import java.util.Locale;

/**
 * Business logic for User lookup and auto-registration.
 * There is no separate "sign up" flow — a User is created the first time their Telegram chat ID is seen.
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    // Default values applied to every newly auto-created user.
    private static final int DEFAULT_REMINDER_DAYS_BEFORE = 3;
    private static final String DEFAULT_CURRENCY = "LKR";

    /**
     * Finds the User for a given Telegram chat ID, or creates on with default settings if this chat ID has never messaged before.
     * This is the entry point every incoming bot message will call first.
     */
    @Transactional
    public User findOrCreateByTelegramChatId(Long telegramChatId) {
        return userRepository.findByTelegramChatId(telegramChatId)
                .orElseGet(() -> createUser(telegramChatId));
    }

    // Get a user's response DTO by their internal id, throws if not found
    @Transactional(readOnly = true)
    public UserResponse getUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
        return mapToResponse(user);
    }

    @Transactional
    public UserResponse getSettings(Long chatId) {
        return mapToResponse(findOrCreateByTelegramChatId(chatId));
    }

    @Transactional
    public UserResponse updateReminderDaysBefore(Long chatId, int days) {
        if (days < 1 || days > 30) {
            throw new IllegalArgumentException("Reminder days must be between 1 and 30.");
        }
        User user = findOrCreateByTelegramChatId(chatId);
        user.setReminderDaysBefore(days);
        return mapToResponse(user);
    }

    @Transactional
    public UserResponse updateDefaultCurrency(Long chatId, String raw) {
        String currency = raw.trim().toUpperCase(Locale.ROOT);
        try {
            Currency.getInstance(currency);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Use a valid three-letter currency code, such as LKR or USD.");
        }
        User user = findOrCreateByTelegramChatId(chatId);
        user.setDefaultCurrency(currency);
        return mapToResponse(user);
    }

    @Transactional
    public UserResponse updateEmail(Long chatId, String raw) {
        String email = raw.trim();
        boolean remove = email.equalsIgnoreCase("off");
        if (!remove && (email.length() > 254 || !email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))) {
            throw new IllegalArgumentException("Use a valid email address, or off to remove it.");
        }
        User user = findOrCreateByTelegramChatId(chatId);
        user.setEmail(remove ? null : email);
        return mapToResponse(user);
    }

    // Creates a brand-new user with default settings
    private User createUser(Long telegramChatId) {
        User user = User.builder()
                .telegramChatId(telegramChatId)
                .reminderDaysBefore(DEFAULT_REMINDER_DAYS_BEFORE)
                .defaultCurrency(DEFAULT_CURRENCY)
                .build();

        return userRepository.save(user);
    }

    private UserResponse mapToResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .telegramChatId(user.getTelegramChatId())
                .reminderDaysBefore(user.getReminderDaysBefore())
                .defaultCurrency(user.getDefaultCurrency())
                .email(user.getEmail())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
