package com.subtracker.bot.service;

import com.subtracker.bot.model.*;
import com.subtracker.bot.repository.ReminderLogRepository;
import com.subtracker.bot.repository.SubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.*;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SubscriptionRenewalServiceTests {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 12);
    private SubscriptionRepository subscriptions;
    private ReminderLogRepository logs;
    private TelegramApiClient telegram;
    private SubscriptionRenewalService service;

    @BeforeEach
    void setUp() {
        subscriptions = mock(SubscriptionRepository.class);
        logs = mock(ReminderLogRepository.class);
        telegram = mock(TelegramApiClient.class);
        service = new SubscriptionRenewalService(subscriptions, logs, telegram,
                Clock.fixed(Instant.parse("2026-10-11T18:45:00Z"), ZoneId.of("Asia/Colombo")));
    }

    @ParameterizedTest
    @CsvSource({
            "MONTHLY,2026-10-12,2026-10-12,2026-11-12",
            "YEARLY,2026-10-12,2026-10-12,2027-10-12",
            "MONTHLY,2026-01-31,2026-01-31,2026-02-28",
            "MONTHLY,2026-01-31,2026-03-01,2026-03-28",
            "YEARLY,2024-02-29,2024-02-29,2025-02-28",
            "YEARLY,2024-02-29,2026-10-12,2027-02-28",
            "MONTHLY,2026-05-12,2026-10-12,2026-11-12",
            "MONTHLY,2026-10-15,2026-10-12,2026-10-15"
    })
    void advancesUntilFutureWithJavaMonthEndClamping(
            BillingCycle cycle, LocalDate date, LocalDate today, LocalDate expected) {
        Subscription subscription = subscription(SubscriptionStatus.ACTIVE, date);
        subscription.setBillingCycle(cycle);
        service.advanceRenewalDate(subscription, today);
        assertEquals(expected, subscription.getNextRenewalDate());
    }

    @Test
    void dueTrialConvertsAutomaticallyAdvancesAndLogsOriginalConversionDateOnce() {
        Subscription subscription = subscription(SubscriptionStatus.TRIAL, TODAY);
        when(subscriptions.findByIdForUpdate(1L)).thenReturn(Optional.of(subscription));
        when(telegram.sendMessage(anyLong(), anyString())).thenReturn(true);

        service.processSubscription(1L);
        service.processSubscription(1L);

        assertEquals(SubscriptionStatus.ACTIVE, subscription.getStatus());
        assertEquals(TODAY.plusMonths(1), subscription.getNextRenewalDate());
        assertNull(subscription.getPendingTrialConversionDate());
        verify(telegram, times(1)).sendMessage(eq(123L), contains("is now ACTIVE"));
        ArgumentCaptor<ReminderLog> saved = ArgumentCaptor.forClass(ReminderLog.class);
        verify(logs).save(saved.capture());
        assertEquals(ReminderType.TRIAL_CONVERSION, saved.getValue().getType());
        assertEquals(ReminderChannel.TELEGRAM, saved.getValue().getChannel());
        assertEquals(TODAY, saved.getValue().getRenewalDate());
    }

    @Test
    void failedNoticeStillConvertsAndRetainsPendingDateForRetryWithoutDoubleRollover() {
        Subscription subscription = subscription(SubscriptionStatus.TRIAL, TODAY);
        when(subscriptions.findByIdForUpdate(1L)).thenReturn(Optional.of(subscription));
        when(telegram.sendMessage(anyLong(), anyString())).thenReturn(false, true);

        service.processSubscription(1L);
        assertEquals(SubscriptionStatus.ACTIVE, subscription.getStatus());
        assertEquals(TODAY.plusMonths(1), subscription.getNextRenewalDate());
        assertEquals(TODAY, subscription.getPendingTrialConversionDate());
        verify(logs, never()).save(any());

        service.processSubscription(1L);
        service.processSubscription(1L);
        assertNull(subscription.getPendingTrialConversionDate());
        assertEquals(TODAY.plusMonths(1), subscription.getNextRenewalDate());
        verify(telegram, times(2)).sendMessage(eq(123L), anyString());
        verify(logs, times(1)).save(any(ReminderLog.class));
    }

    @Test
    void activeRenewalCatchesUpWithoutSendingConversionNotice() {
        Subscription subscription = subscription(SubscriptionStatus.ACTIVE, TODAY.minusMonths(4));
        when(subscriptions.findByIdForUpdate(1L)).thenReturn(Optional.of(subscription));

        service.processSubscription(1L);

        assertEquals(TODAY.plusMonths(1), subscription.getNextRenewalDate());
        assertEquals(SubscriptionStatus.ACTIVE, subscription.getStatus());
        verifyNoInteractions(telegram, logs);
    }

    @Test
    void futureTrialIsUnchanged() {
        Subscription subscription = subscription(SubscriptionStatus.TRIAL, TODAY.plusDays(3));
        when(subscriptions.findByIdForUpdate(1L)).thenReturn(Optional.of(subscription));
        service.processSubscription(1L);
        assertEquals(SubscriptionStatus.TRIAL, subscription.getStatus());
        assertEquals(TODAY.plusDays(3), subscription.getNextRenewalDate());
        verifyNoInteractions(telegram, logs);
    }

    @Test
    void cancelledSubscriptionDoesNotRollOverOrSendPendingNotice() {
        Subscription subscription = subscription(SubscriptionStatus.CANCELLED, TODAY);
        subscription.setPendingTrialConversionDate(TODAY);
        when(subscriptions.findByIdForUpdate(1L)).thenReturn(Optional.of(subscription));
        service.processSubscription(1L);
        assertEquals(TODAY, subscription.getNextRenewalDate());
        assertEquals(SubscriptionStatus.CANCELLED, subscription.getStatus());
        verifyNoInteractions(telegram, logs);
    }

    @Test
    void alreadyLoggedPendingNoticeIsClearedWithoutResending() {
        Subscription subscription = subscription(SubscriptionStatus.ACTIVE, TODAY.plusMonths(1));
        subscription.setPendingTrialConversionDate(TODAY);
        when(subscriptions.findByIdForUpdate(1L)).thenReturn(Optional.of(subscription));
        when(logs.existsBySubscriptionAndTypeAndRenewalDate(subscription, ReminderType.TRIAL_CONVERSION, TODAY))
                .thenReturn(true);
        service.processSubscription(1L);
        assertNull(subscription.getPendingTrialConversionDate());
        verifyNoInteractions(telegram);
        verify(logs, never()).save(any());
    }

    @Test
    void deletedSubscriptionIsSkipped() {
        when(subscriptions.findByIdForUpdate(1L)).thenReturn(Optional.empty());
        assertDoesNotThrow(() -> service.processSubscription(1L));
        verifyNoInteractions(telegram, logs);
    }

    private Subscription subscription(SubscriptionStatus status, LocalDate date) {
        return Subscription.builder().id(1L).name("Spotify").status(status)
                .user(User.builder().id(1L).telegramChatId(123L).defaultCurrency("LKR").build())
                .amount(new BigDecimal("100.00")).billingCycle(BillingCycle.MONTHLY)
                .nextRenewalDate(date).build();
    }
}
