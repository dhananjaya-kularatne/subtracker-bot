package com.subtracker.bot.service;

import com.subtracker.bot.dto.UserResponse;
import com.subtracker.bot.exception.ResourceNotFoundException;
import com.subtracker.bot.model.User;
import com.subtracker.bot.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
                .createdAt(user.getCreatedAt())
                .build();
    }
}