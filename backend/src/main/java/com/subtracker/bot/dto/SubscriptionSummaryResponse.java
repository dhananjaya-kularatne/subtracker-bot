package com.subtracker.bot.dto;

import com.subtracker.bot.model.BillingCycle;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record SubscriptionSummaryResponse(
        List<Entry> activeSubscriptions,
        List<Entry> trialSubscriptions,
        Map<String, BigDecimal> monthlyTotals) {

    public record Entry(
            Long id,
            String name,
            String currency,
            BigDecimal amount,
            BillingCycle billingCycle,
            BigDecimal monthlyEquivalent,
            LocalDate nextRenewalDate) {
    }
}
