package com.subtracker.bot.service;

import com.subtracker.bot.model.*;
import com.subtracker.bot.repository.ReminderLogRepository;
import com.subtracker.bot.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class SubscriptionRenewalService {

    private final SubscriptionRepository subscriptionRepository;
    private final ReminderLogRepository reminderLogRepository;
    private final TelegramApiClient telegramApiClient;
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
        String message = "Your %s trial ended on %s and is now ACTIVE. The paid price is %s %s / %s. Next renewal: %s."
                .formatted(subscription.getName(), conversionDate, subscription.getAmount().toPlainString(),
                        currency, subscription.getBillingCycle().name().toLowerCase(Locale.ROOT),
                        subscription.getNextRenewalDate());

        if (telegramApiClient.sendMessage(subscription.getUser().getTelegramChatId(), message)) {
            reminderLogRepository.save(ReminderLog.builder()
                    .subscription(subscription).type(ReminderType.TRIAL_CONVERSION)
                    .channel(ReminderChannel.TELEGRAM).renewalDate(conversionDate).build());
            subscription.setPendingTrialConversionDate(null);
        }
        // Managed entity changes commit even if Telegram fails. The pending date
        // survives restart and is retried on the next run without advancing twice.
    }
}
