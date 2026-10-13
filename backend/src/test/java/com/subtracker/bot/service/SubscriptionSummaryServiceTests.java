package com.subtracker.bot.service;

import com.subtracker.bot.model.*;
import com.subtracker.bot.repository.SubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SubscriptionSummaryServiceTests {

    private SubscriptionRepository repository;
    private SubscriptionSummaryService service;
    private User user;

    @BeforeEach
    void setUp() {
        repository = mock(SubscriptionRepository.class);
        service = new SubscriptionSummaryService(repository);
        user = User.builder().id(1L).defaultCurrency("LKR").build();
    }

    @Test
    void normalizesAnnualCostAndKeepsMonthlyCost() {
        when(repository.findByUser(user)).thenReturn(List.of(
                subscription(1L, "1500", BillingCycle.MONTHLY, SubscriptionStatus.ACTIVE, "LKR"),
                subscription(2L, "12000", BillingCycle.YEARLY, SubscriptionStatus.ACTIVE, "LKR")));

        var summary = service.getSummary(user);

        assertEquals(new BigDecimal("1500.00"), summary.activeSubscriptions().get(0).monthlyEquivalent());
        assertEquals(new BigDecimal("1000.00"), summary.activeSubscriptions().get(1).monthlyEquivalent());
        assertEquals(Map.of("LKR", new BigDecimal("2500.00")), summary.monthlyTotals());
        verify(repository).findByUser(user);
    }

    @Test
    void roundsAnnualEquivalentsHalfUpAndSumsDisplayedAmounts() {
        when(repository.findByUser(user)).thenReturn(List.of(
                subscription(1L, "100", BillingCycle.YEARLY, SubscriptionStatus.ACTIVE, "LKR"),
                subscription(2L, "100", BillingCycle.YEARLY, SubscriptionStatus.ACTIVE, "LKR"),
                subscription(3L, "0.06", BillingCycle.YEARLY, SubscriptionStatus.ACTIVE, "LKR")));

        var summary = service.getSummary(user);

        assertEquals(new BigDecimal("8.33"), summary.activeSubscriptions().get(0).monthlyEquivalent());
        assertEquals(new BigDecimal("0.01"), summary.activeSubscriptions().get(2).monthlyEquivalent());
        assertEquals(new BigDecimal("16.67"), summary.monthlyTotals().get("LKR"));
    }

    @Test
    void groupsEffectiveCurrencyWithoutConvertingOrCombiningDifferentCurrencies() {
        when(repository.findByUser(user)).thenReturn(List.of(
                subscription(1L, "100", BillingCycle.MONTHLY, SubscriptionStatus.ACTIVE, null),
                subscription(2L, "200", BillingCycle.MONTHLY, SubscriptionStatus.ACTIVE, " lkr "),
                subscription(3L, "12", BillingCycle.YEARLY, SubscriptionStatus.ACTIVE, "usd"),
                subscription(4L, "50", BillingCycle.MONTHLY, SubscriptionStatus.ACTIVE, " ")));

        var summary = service.getSummary(user);

        assertEquals(Map.of("LKR", new BigDecimal("350.00"), "USD", new BigDecimal("1.00")),
                summary.monthlyTotals());
        assertEquals("LKR", summary.activeSubscriptions().get(0).currency());
        assertEquals("USD", summary.activeSubscriptions().get(2).currency());
    }

    @Test
    void listsTrialsSeparatelyAndExcludesTrialsAndCancelledFromTotals() {
        when(repository.findByUser(user)).thenReturn(List.of(
                subscription(1L, "100", BillingCycle.MONTHLY, SubscriptionStatus.ACTIVE, "LKR"),
                subscription(2L, "200", BillingCycle.MONTHLY, SubscriptionStatus.TRIAL, "LKR"),
                subscription(3L, "300", BillingCycle.MONTHLY, SubscriptionStatus.CANCELLED, "LKR")));

        var summary = service.getSummary(user);

        assertEquals(1, summary.activeSubscriptions().size());
        assertEquals(1, summary.trialSubscriptions().size());
        assertEquals(2L, summary.trialSubscriptions().get(0).id());
        assertEquals(Map.of("LKR", new BigDecimal("100.00")), summary.monthlyTotals());
    }

    @Test
    void trialsOnlyHaveNoActiveTotals() {
        when(repository.findByUser(user)).thenReturn(List.of(
                subscription(1L, "100", BillingCycle.MONTHLY, SubscriptionStatus.TRIAL, "LKR")));
        var summary = service.getSummary(user);
        assertTrue(summary.activeSubscriptions().isEmpty());
        assertTrue(summary.monthlyTotals().isEmpty());
        assertEquals(1, summary.trialSubscriptions().size());
    }

    @Test
    void emptyAndCancelledOnlyListsProduceEmptySummary() {
        when(repository.findByUser(user)).thenReturn(List.of()).thenReturn(List.of(
                subscription(1L, "100", BillingCycle.MONTHLY, SubscriptionStatus.CANCELLED, "LKR")));
        for (int i = 0; i < 2; i++) {
            var summary = service.getSummary(user);
            assertTrue(summary.activeSubscriptions().isEmpty());
            assertTrue(summary.trialSubscriptions().isEmpty());
            assertTrue(summary.monthlyTotals().isEmpty());
        }
    }

    @Test
    void breakdownIsOrderedById() {
        when(repository.findByUser(user)).thenReturn(List.of(
                subscription(2L, "200", BillingCycle.MONTHLY, SubscriptionStatus.ACTIVE, "LKR"),
                subscription(1L, "100", BillingCycle.MONTHLY, SubscriptionStatus.ACTIVE, "LKR")));
        assertEquals(List.of(1L, 2L), service.getSummary(user).activeSubscriptions().stream()
                .map(entry -> entry.id()).toList());
    }

    private Subscription subscription(long id, String amount, BillingCycle cycle,
                                      SubscriptionStatus status, String currency) {
        return Subscription.builder().id(id).user(user).name("Subscription " + id)
                .amount(new BigDecimal(amount)).billingCycle(cycle).status(status).currency(currency)
                .nextRenewalDate(LocalDate.of(2026, 11, 15)).build();
    }
}
