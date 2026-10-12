package com.subtracker.bot.service;

import com.subtracker.bot.model.SubscriptionStatus;
import com.subtracker.bot.repository.SubscriptionRepository;
import org.junit.jupiter.api.Test;

import java.time.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SubscriptionLifecycleServiceTests {

    @Test
    void dueProcessingContinuesAfterOneFailureAndIncludesOnlyEligibleStatuses() {
        SubscriptionRepository repository = mock(SubscriptionRepository.class);
        SubscriptionRenewalService renewals = mock(SubscriptionRenewalService.class);
        Clock clock = Clock.fixed(Instant.parse("2026-10-11T18:45:00Z"), ZoneId.of("Asia/Colombo"));
        when(repository.findDueOrPendingConversionIds(
                List.of(SubscriptionStatus.TRIAL, SubscriptionStatus.ACTIVE), LocalDate.of(2026, 10, 12)))
                .thenReturn(List.of(1L, 2L));
        doThrow(new IllegalStateException("Simulated renewal failure")).when(renewals).processSubscription(1L);

        assertDoesNotThrow(() -> new SubscriptionLifecycleService(repository, renewals, clock).processDueSubscriptions());

        verify(renewals).processSubscription(2L);
    }

    @Test
    void schedulerProcessesConversionsAndRolloverBeforeReminders() {
        ReminderService reminders = mock(ReminderService.class);
        SubscriptionLifecycleService lifecycle = mock(SubscriptionLifecycleService.class);

        new ReminderScheduler(reminders, lifecycle).sendDueReminders();

        var order = inOrder(lifecycle, reminders);
        order.verify(lifecycle).processDueSubscriptions();
        order.verify(reminders).sendDueReminders();
    }
}
