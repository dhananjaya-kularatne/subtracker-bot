package com.subtracker.bot.service;

import com.subtracker.bot.model.ReminderLog;
import com.subtracker.bot.model.ReminderType;
import com.subtracker.bot.model.Subscription;
import com.subtracker.bot.model.SubscriptionStatus;
import com.subtracker.bot.repository.ReminderLogRepository;
import com.subtracker.bot.repository.SubscriptionRepository;
import com.subtracker.bot.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReminderService {

    private final SubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;
    private final ReminderLogRepository reminderLogRepository;
    private final NotificationDeliveryService notificationDeliveryService;
    private final Clock reminderClock;

    // Serializes runs within this application instance. Use one instance until
    // distributed coordination is added for multiple deployment replicas.
    public synchronized void sendDueReminders() {
        LocalDate today = LocalDate.now(reminderClock);
        int maxWindow = Math.max(0, userRepository.findMaxReminderDaysBefore());
        List<Subscription> subscriptions = subscriptionRepository
                .findByStatusInAndNextRenewalDateBetween(
                        List.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.TRIAL),
                        today, today.plusDays(maxWindow));

        for (Subscription subscription : subscriptions) {
            try {
                sendReminderIfDue(subscription, today);
            } catch (Exception exception) {
                log.error("Failed to process reminder for subscription {}",
                        subscription.getId(), exception);
            }
        }
    }

    private void sendReminderIfDue(Subscription subscription, LocalDate today) {
        int window = subscription.getUser().getReminderDaysBefore();
        LocalDate renewalDate = subscription.getNextRenewalDate();
        if (window < 0 || renewalDate.isAfter(today.plusDays(window))) {
            return;
        }

        if (reminderLogRepository.existsBySubscriptionAndTypeAndRenewalDate(
                subscription, ReminderType.RENEWAL_REMINDER, renewalDate)) {
            return;
        }

        var channel = notificationDeliveryService.send(subscription.getUser(),
                "Subscription reminder: " + subscription.getName(), formatReminder(subscription, today));
        if (channel.isEmpty()) {
            return;
        }

        // Repository save has its own transaction; one failure does not roll back
        // the whole batch. Failed sends never create a successful delivery log.
        reminderLogRepository.save(ReminderLog.builder()
                .subscription(subscription)
                .type(ReminderType.RENEWAL_REMINDER)
                .channel(channel.get())
                .renewalDate(renewalDate)
                .build());
    }

    private String formatReminder(Subscription subscription, LocalDate today) {
        long days = ChronoUnit.DAYS.between(today, subscription.getNextRenewalDate());
        String when = days == 0 ? "today" : days == 1 ? "tomorrow" : "in " + days + " days";
        String currency = subscription.getCurrency() != null
                ? subscription.getCurrency() : subscription.getUser().getDefaultCurrency();
        String charge = subscription.getAmount().toPlainString() + " " + currency;
        String reply = "\nReply /cancel %d or /keep %d.".formatted(subscription.getId(), subscription.getId());

        if (subscription.getStatus() == SubscriptionStatus.TRIAL) {
            return "Your %s trial ends %s (%s). You'll be charged %s unless you cancel."
                    .formatted(subscription.getName(), when, subscription.getNextRenewalDate(), charge) + reply;
        }
        return "%s renews %s (%s). %s will be charged."
                .formatted(subscription.getName(), when, subscription.getNextRenewalDate(), charge) + reply;
    }
}
