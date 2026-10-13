package com.subtracker.bot.service;

import com.subtracker.bot.model.User;
import com.subtracker.bot.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UserSettingsTests {
    private UserRepository repository;
    private UserService service;
    private User user;

    @BeforeEach
    void setUp() {
        repository = mock(UserRepository.class);
        service = new UserService(repository);
        user = User.builder().id(1L).telegramChatId(123L).defaultCurrency("LKR").reminderDaysBefore(3).build();
        when(repository.findByTelegramChatId(123L)).thenReturn(Optional.of(user));
    }

    @Test
    void readsAndUpdatesReminderSettingsForTheCallingChat() {
        assertEquals(3, service.getSettings(123L).getReminderDaysBefore());
        assertEquals(1, service.updateReminderDaysBefore(123L, 1).getReminderDaysBefore());
        assertEquals(30, service.updateReminderDaysBefore(123L, 30).getReminderDaysBefore());
        assertEquals(30, user.getReminderDaysBefore());
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 31})
    void invalidReminderWindowDoesNotUpdateUser(int days) {
        assertThrows(IllegalArgumentException.class, () -> service.updateReminderDaysBefore(123L, days));
        assertEquals(3, user.getReminderDaysBefore());
    }

    @Test
    void currencyIsNormalizedAndInvalidCurrencyDoesNotReplaceIt() {
        assertEquals("USD", service.updateDefaultCurrency(123L, "usd").getDefaultCurrency());
        assertThrows(IllegalArgumentException.class, () -> service.updateDefaultCurrency(123L, "NOT_A_CODE"));
        assertEquals("USD", user.getDefaultCurrency());
    }

    @Test
    void emailCanBeSavedAndRemoved() {
        assertEquals("user@example.com", service.updateEmail(123L, " user@example.com ").getEmail());
        assertEquals("user@example.com", service.getSettings(123L).getEmail());
        assertNull(service.updateEmail(123L, "off").getEmail());
        assertNull(user.getEmail());
    }

    @ParameterizedTest
    @ValueSource(strings = {"invalid", "a@@example.com", "a b@example.com", "a@example", ""})
    void invalidEmailDoesNotUpdateUser(String email) {
        assertThrows(IllegalArgumentException.class, () -> service.updateEmail(123L, email));
        assertNull(user.getEmail());
    }
}
