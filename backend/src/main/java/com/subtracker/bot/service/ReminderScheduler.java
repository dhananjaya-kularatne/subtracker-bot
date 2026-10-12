package com.subtracker.bot.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ReminderScheduler {

    private final ReminderService reminderService;

    @Scheduled(cron = "${reminder.cron:0 0 9 * * *}", zone = "Asia/Colombo")
    public void sendDueReminders() {
        try {
            reminderService.sendDueReminders();
        } catch (Exception exception) {
            log.error("Failed to run scheduled reminders", exception);
        }
    }
}
