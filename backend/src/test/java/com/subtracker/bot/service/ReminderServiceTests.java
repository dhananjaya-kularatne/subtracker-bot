package com.subtracker.bot.service;

import com.subtracker.bot.model.*;
import com.subtracker.bot.repository.ReminderLogRepository;
import com.subtracker.bot.repository.SubscriptionRepository;
import com.subtracker.bot.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReminderServiceTests {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 12);
    private SubscriptionRepository subscriptions;
    private UserRepository users;
    private ReminderLogRepository logs;
    private TelegramApiClient telegram;
    private ReminderService service;

    @BeforeEach
    void setUp() {
        subscriptions = mock(SubscriptionRepository.class);
        users = mock(UserRepository.class);
        logs = mock(ReminderLogRepository.class);
        telegram = mock(TelegramApiClient.class);
        // It is still October 11 in UTC, but October 12 in Colombo.
        Clock clock = Clock.fixed(Instant.parse("2026-10-11T18:45:00Z"),
                ZoneId.of("Asia/Colombo"));
        service = new ReminderService(subscriptions, users, logs, telegram, clock);
        when(users.findMaxReminderDaysBefore()).thenReturn(7);
    }

    @Test
    void usesColomboDateAndQueriesOnlyEligibleStatusesWithinMaximumWindow() {
        candidates();

        service.sendDueReminders();

        verify(subscriptions).findByStatusInAndNextRenewalDateBetween(
                List.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.TRIAL),
                TODAY, TODAY.plusDays(7));
        verifyNoInteractions(telegram, logs);
    }

    @Test
    void skipsSubscriptionOutsideItsOwnersWindowEvenInsideAnotherUsersWindow() {
        candidates(subscription(1L, SubscriptionStatus.ACTIVE, TODAY.plusDays(4), 3));

        service.sendDueReminders();

        verifyNoInteractions(telegram, logs);
    }

    @Test
    void sendsAtWindowBoundaryAndRecordsSuccessfulDeliveryForThatCycle() {
        Subscription subscription = subscription(1L, SubscriptionStatus.ACTIVE, TODAY.plusDays(3), 3);
        candidates(subscription);
        when(telegram.sendMessage(anyLong(), anyString())).thenReturn(true);

        service.sendDueReminders();

        verify(telegram).sendMessage(101L,
                "Netflix renews in 3 days (2026-10-15). 100.00 LKR will be charged.");
        ArgumentCaptor<ReminderLog> saved = ArgumentCaptor.forClass(ReminderLog.class);
        verify(logs).save(saved.capture());
        assertSame(subscription, saved.getValue().getSubscription());
        assertEquals(ReminderType.RENEWAL_REMINDER, saved.getValue().getType());
        assertEquals(ReminderChannel.TELEGRAM, saved.getValue().getChannel());
        assertEquals(TODAY.plusDays(3), saved.getValue().getRenewalDate());
    }

    @Test
    void trialReminderWarnsAboutChargeWithoutConvertingOrAdvancingSubscription() {
        Subscription trial = subscription(1L, SubscriptionStatus.TRIAL, TODAY.plusDays(3), 3);
        trial.setName("Spotify");
        candidates(trial);
        when(telegram.sendMessage(anyLong(), anyString())).thenReturn(true);

        service.sendDueReminders();

        verify(telegram).sendMessage(101L,
                "Your Spotify trial ends in 3 days (2026-10-15). You'll be charged 100.00 LKR unless you cancel.");
        assertEquals(SubscriptionStatus.TRIAL, trial.getStatus());
        assertEquals(TODAY.plusDays(3), trial.getNextRenewalDate());
        verify(subscriptions, never()).save(any());
        verify(logs).save(any(ReminderLog.class));
    }

    @Test
    void failedSendCreatesNoLogAndNextRunRetriesThenStopsAfterSuccessfulLogging() {
        Subscription subscription = subscription(1L, SubscriptionStatus.ACTIVE, TODAY.plusDays(3), 3);
        candidates(subscription);
        AtomicBoolean delivered = new AtomicBoolean();
        when(logs.existsBySubscriptionAndTypeAndRenewalDate(
                subscription, ReminderType.RENEWAL_REMINDER, TODAY.plusDays(3)))
                .thenAnswer(invocation -> delivered.get());
        when(logs.save(any(ReminderLog.class))).thenAnswer(invocation -> {
            delivered.set(true);
            return invocation.getArgument(0);
        });
        when(telegram.sendMessage(anyLong(), anyString())).thenReturn(false, true);

        service.sendDueReminders();
        verify(logs, never()).save(any());
        service.sendDueReminders();
        service.sendDueReminders();

        verify(telegram, times(2)).sendMessage(eq(101L), anyString());
        verify(logs, times(1)).save(any(ReminderLog.class));
    }

    @Test
    void existingCycleLogPreventsDuplicateAfterReminderWindowChanges() {
        Subscription subscription = subscription(1L, SubscriptionStatus.ACTIVE, TODAY.plusDays(3), 7);
        candidates(subscription);
        when(logs.existsBySubscriptionAndTypeAndRenewalDate(
                subscription, ReminderType.RENEWAL_REMINDER, TODAY.plusDays(3))).thenReturn(true);

        service.sendDueReminders();
        subscription.getUser().setReminderDaysBefore(3);
        service.sendDueReminders();

        verifyNoInteractions(telegram);
        verify(logs, never()).save(any());
    }

    @Test
    void oneSubscriptionFailureDoesNotPreventAnotherReminder() {
        Subscription broken = subscription(1L, SubscriptionStatus.ACTIVE, TODAY.plusDays(3), 3);
        Subscription healthy = subscription(2L, SubscriptionStatus.ACTIVE, TODAY.plusDays(3), 3);
        candidates(broken, healthy);
        when(logs.existsBySubscriptionAndTypeAndRenewalDate(
                broken, ReminderType.RENEWAL_REMINDER, TODAY.plusDays(3)))
                .thenThrow(new IllegalStateException("Simulated lookup failure"));
        when(telegram.sendMessage(eq(102L), anyString())).thenReturn(true);

        assertDoesNotThrow(service::sendDueReminders);

        verify(telegram).sendMessage(eq(102L), anyString());
        verify(telegram, never()).sendMessage(eq(101L), anyString());
        verify(logs).save(argThat(entry -> entry.getSubscription() == healthy));
    }

    @Test
    void failedLogInsertDoesNotPreventAnotherReminder() {
        Subscription broken = subscription(1L, SubscriptionStatus.ACTIVE, TODAY.plusDays(3), 3);
        Subscription healthy = subscription(2L, SubscriptionStatus.ACTIVE, TODAY.plusDays(3), 3);
        candidates(broken, healthy);
        when(telegram.sendMessage(anyLong(), anyString())).thenReturn(true);
        when(logs.save(argThat(entry -> entry != null && entry.getSubscription() == broken)))
                .thenThrow(new IllegalStateException("Simulated insert failure"));

        assertDoesNotThrow(service::sendDueReminders);

        verify(telegram).sendMessage(eq(102L), anyString());
        verify(logs).save(argThat(entry -> entry.getSubscription() == healthy));
    }

    @Test
    void usesSubscriptionCurrencyOverrideForTomorrowMessage() {
        Subscription subscription = subscription(1L, SubscriptionStatus.ACTIVE, TODAY.plusDays(1), 3);
        subscription.setCurrency("USD");
        candidates(subscription);

        service.sendDueReminders();

        verify(telegram).sendMessage(101L,
                "Netflix renews tomorrow (2026-10-13). 100.00 USD will be charged.");
    }

    @Test
    void zeroDayWindowSendsTodayAndFallsBackToUsersCurrency() {
        Subscription subscription = subscription(1L, SubscriptionStatus.ACTIVE, TODAY, 0);
        subscription.setCurrency(null);
        candidates(subscription);

        service.sendDueReminders();

        verify(telegram).sendMessage(101L,
                "Netflix renews today (2026-10-12). 100.00 LKR will be charged.");
    }

    private void candidates(Subscription... entries) {
        when(subscriptions.findByStatusInAndNextRenewalDateBetween(
                List.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.TRIAL), TODAY, TODAY.plusDays(7)))
                .thenReturn(List.of(entries));
    }

    private Subscription subscription(long id, SubscriptionStatus status, LocalDate renewalDate, int window) {
        User user = User.builder().id(id).telegramChatId(100L + id)
                .reminderDaysBefore(window).defaultCurrency("LKR").build();
        return Subscription.builder().id(id).user(user).name("Netflix")
                .amount(new BigDecimal("100.00")).currency("LKR")
                .billingCycle(BillingCycle.MONTHLY).status(status)
                .nextRenewalDate(renewalDate).build();
    }
}
