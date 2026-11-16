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
        String charge = MessageFormatting.money(currency, subscription.getAmount());
        String actions = "\n\n/keep %d - keep tracking\n/cancel %d - stop reminders\n\nTo stop actual charges, cancel with the provider too."
                .formatted(subscription.getId(), subscription.getId());

        if (subscription.getStatus() == SubscriptionStatus.TRIAL) {
            return "⏳ Your trial ends %s\n\n%s · #%d\nTrial ends: %s\nAfter trial: %s / %s\n\nWant to keep it? No action is needed."
                    .formatted(when, subscription.getName(), subscription.getId(),
                            MessageFormatting.date(subscription.getNextRenewalDate()), charge,
                            MessageFormatting.period(subscription.getBillingCycle())) + actions;
        }
        return "🔔 Renewal %s\n\n%s · #%d\nAmount: %s / %s\nRenewal: %s\n\nHere's your heads-up before the next payment."
                .formatted(when, subscription.getName(), subscription.getId(), charge,
                        MessageFormatting.period(subscription.getBillingCycle()),
                        MessageFormatting.date(subscription.getNextRenewalDate())) + actions;
    }
}
