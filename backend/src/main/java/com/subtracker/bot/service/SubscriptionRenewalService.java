package com.subtracker.bot.service;

import com.subtracker.bot.model.*;
import com.subtracker.bot.repository.ReminderLogRepository;
import com.subtracker.bot.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class SubscriptionRenewalService {

    private final SubscriptionRepository subscriptionRepository;
    private final ReminderLogRepository reminderLogRepository;
    private final NotificationDeliveryService notificationDeliveryService;
    private final Clock reminderClock;

    // Called through a separate service so every subscription gets its own transaction.
    @Transactional
    public void processSubscription(Long id) {
        Subscription subscription = subscriptionRepository.findByIdForUpdate(id).orElse(null);
        if (subscription == null || subscription.getStatus() == SubscriptionStatus.CANCELLED) {
            return;
        }

        LocalDate today = LocalDate.now(reminderClock);
        if (!subscription.getNextRenewalDate().isAfter(today)) {
            if (subscription.getStatus() == SubscriptionStatus.TRIAL) {
                subscription.setPendingTrialConversionDate(subscription.getNextRenewalDate());
                subscription.setStatus(SubscriptionStatus.ACTIVE);
            }
            advanceRenewalDate(subscription, today);
        }

        sendPendingConversion(subscription);
    }

    void advanceRenewalDate(Subscription subscription, LocalDate today) {
        LocalDate date = subscription.getNextRenewalDate();
        while (!date.isAfter(today)) {
            // Java clamps month-end and leap-day dates. No billing-day anchor in v1.
            date = switch (subscription.getBillingCycle()) {
                case MONTHLY -> date.plusMonths(1);
                case YEARLY -> date.plusYears(1);
            };
        }
        subscription.setNextRenewalDate(date);
    }

    private void sendPendingConversion(Subscription subscription) {
        LocalDate conversionDate = subscription.getPendingTrialConversionDate();
        if (conversionDate == null) {
            return;
        }

        if (reminderLogRepository.existsBySubscriptionAndTypeAndRenewalDate(
                subscription, ReminderType.TRIAL_CONVERSION, conversionDate)) {
            subscription.setPendingTrialConversionDate(null);
            return;
        }

        String currency = subscription.getCurrency() != null
                ? subscription.getCurrency() : subscription.getUser().getDefaultCurrency();
        String message = "✅ Your trial has ended\n\n%s · #%d\nStatus: Active\nTrial ended: %s\nPrice: %s / %s\nNext renewal: %s\n\nWe'll keep tracking it and remind you before the next renewal.\n/list - manage your subscriptions"
                .formatted(subscription.getName(), subscription.getId(), MessageFormatting.date(conversionDate),
                        MessageFormatting.money(currency, subscription.getAmount()),
                        MessageFormatting.period(subscription.getBillingCycle()),
                        MessageFormatting.date(subscription.getNextRenewalDate()));

        var channel = notificationDeliveryService.send(subscription.getUser(),
                "Trial conversion: " + subscription.getName(), message);
        if (channel.isPresent()) {
            reminderLogRepository.save(ReminderLog.builder()
                    .subscription(subscription).type(ReminderType.TRIAL_CONVERSION)
                    .channel(channel.get()).renewalDate(conversionDate).build());
            subscription.setPendingTrialConversionDate(null);
        }
        // Managed entity changes commit even if Telegram fails. The pending date
        // survives restart and is retried on the next run without advancing twice.
    }
}
