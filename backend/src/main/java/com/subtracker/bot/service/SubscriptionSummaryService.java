package com.subtracker.bot.service;

import com.subtracker.bot.dto.SubscriptionSummaryResponse;
import com.subtracker.bot.model.BillingCycle;
import com.subtracker.bot.model.Subscription;
import com.subtracker.bot.model.SubscriptionStatus;
import com.subtracker.bot.model.User;
import com.subtracker.bot.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

@Service
@RequiredArgsConstructor
public class SubscriptionSummaryService {

    private final SubscriptionRepository subscriptionRepository;

    @Transactional(readOnly = true)
    public SubscriptionSummaryResponse getSummary(User user) {
        var active = new ArrayList<SubscriptionSummaryResponse.Entry>();
        var trials = new ArrayList<SubscriptionSummaryResponse.Entry>();
        Map<String, BigDecimal> totals = new TreeMap<>();

        subscriptionRepository.findByUser(user).stream()
                .filter(subscription -> subscription.getStatus() != SubscriptionStatus.CANCELLED)
                .sorted(Comparator.comparing(Subscription::getId))
                .forEach(subscription -> {
                    SubscriptionSummaryResponse.Entry entry = toEntry(subscription, user);
                    if (subscription.getStatus() == SubscriptionStatus.ACTIVE) {
                        active.add(entry);
                        totals.merge(entry.currency(), entry.monthlyEquivalent(), BigDecimal::add);
                    } else if (subscription.getStatus() == SubscriptionStatus.TRIAL) {
                        trials.add(entry);
                    }
                });

        return new SubscriptionSummaryResponse(java.util.List.copyOf(active), java.util.List.copyOf(trials),
                Collections.unmodifiableMap(totals));
    }

    private SubscriptionSummaryResponse.Entry toEntry(Subscription subscription, User user) {
        String currency = subscription.getCurrency();
        if (currency == null || currency.isBlank()) {
            currency = user.getDefaultCurrency();
        }
        currency = currency.trim().toUpperCase(Locale.ROOT);
        BigDecimal amount = subscription.getAmount().setScale(2, RoundingMode.HALF_UP);
        // Sum the displayed two-decimal equivalents so the breakdown matches the total.
        BigDecimal monthly = subscription.getBillingCycle() == BillingCycle.YEARLY
                ? amount.divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP) : amount;
        return new SubscriptionSummaryResponse.Entry(subscription.getId(), subscription.getName(), currency,
                amount, subscription.getBillingCycle(), monthly, subscription.getNextRenewalDate());
    }
}
