package com.subtracker.bot.service;

import com.subtracker.bot.model.ReminderChannel;
import com.subtracker.bot.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class NotificationDeliveryService {

    private final TelegramApiClient telegramApiClient;
    private final BrevoEmailClient brevoEmailClient;

    public Optional<ReminderChannel> send(User user, String subject, String message) {
        if (telegramApiClient.sendMessage(user.getTelegramChatId(), message)) {
            return Optional.of(ReminderChannel.TELEGRAM);
        }
        if (user.getEmail() != null && !user.getEmail().isBlank()) {
            String emailText = message.replace("\nReply /", "\nIn Telegram, send /")
                    + "\n\nUse the commands above in Telegram to manage your subscription.";
            if (brevoEmailClient.sendEmail(user.getEmail(), subject, emailText)) {
                return Optional.of(ReminderChannel.EMAIL);
            }
        }
        return Optional.empty();
    }
}
