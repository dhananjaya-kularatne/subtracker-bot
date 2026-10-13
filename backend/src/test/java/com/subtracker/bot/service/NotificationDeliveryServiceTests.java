package com.subtracker.bot.service;

import com.subtracker.bot.model.ReminderChannel;
import com.subtracker.bot.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NotificationDeliveryServiceTests {
    private TelegramApiClient telegram;
    private BrevoEmailClient email;
    private NotificationDeliveryService service;
    private User user;

    @BeforeEach
    void setUp() {
        telegram = mock(TelegramApiClient.class);
        email = mock(BrevoEmailClient.class);
        service = new NotificationDeliveryService(telegram, email);
        user = User.builder().telegramChatId(123L).email("user@example.com").build();
    }

    @Test
    void telegramSuccessSkipsEmail() {
        when(telegram.sendMessage(123L, "Text")).thenReturn(true);
        assertEquals(Optional.of(ReminderChannel.TELEGRAM), service.send(user, "Subject", "Text"));
        verifyNoInteractions(email);
    }

    @Test
    void telegramFailureUsesEmailAndClarifiesWhereToSendCommands() {
        when(email.sendEmail(anyString(), anyString(), anyString())).thenReturn(true);
        assertEquals(Optional.of(ReminderChannel.EMAIL), service.send(user, "Subject", "Text\nReply /cancel 1 or /keep 1."));
        verify(email).sendEmail(eq("user@example.com"), eq("Subject"), contains("In Telegram, send /cancel 1 or /keep 1."));
    }

    @Test
    void absentEmailLeavesDeliveryUnsuccessful() {
        user.setEmail(null);
        assertTrue(service.send(user, "Subject", "Text").isEmpty());
        verifyNoInteractions(email);
    }

    @Test
    void bothChannelsFailLeavesDeliveryUnsuccessful() {
        assertTrue(service.send(user, "Subject", "Text").isEmpty());
        verify(email).sendEmail(eq("user@example.com"), eq("Subject"), anyString());
    }
}
