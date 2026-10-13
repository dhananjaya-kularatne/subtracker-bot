package com.subtracker.bot.service;

import com.subtracker.bot.dto.*;
import com.subtracker.bot.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import com.subtracker.bot.exception.ResourceNotFoundException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TelegramCommandServiceTests {

    private TelegramApiClient telegram;
    private UserService users;
    private SubscriptionService subscriptions;
    private SubscriptionSummaryService summaries;
    private TelegramCommandService commands;
    private User user;

    @BeforeEach
    void setUp() {
        telegram = mock(TelegramApiClient.class);
        users = mock(UserService.class);
        subscriptions = mock(SubscriptionService.class);
        summaries = mock(SubscriptionSummaryService.class);
        commands = new TelegramCommandService(telegram, users, subscriptions, summaries);
        user = User.builder().id(1L).telegramChatId(123L).build();
        when(users.findOrCreateByTelegramChatId(123L)).thenReturn(user);
        when(subscriptions.createSubscription(eq(user), any())).thenAnswer(invocation -> {
            CreateSubscriptionRequest request = invocation.getArgument(1);
            return SubscriptionResponse.builder().id(7L).name(request.getName())
                    .amount(request.getAmount()).currency("LKR").billingCycle(request.getBillingCycle())
                    .nextRenewalDate(request.getNextRenewalDate())
                    .status(request.isTrial() ? SubscriptionStatus.TRIAL : SubscriptionStatus.ACTIVE).build();
        });
    }

    @Test
    void optionalTrialTokenSupportsMultiWordNamesAndCaseInsensitiveFlag() {
        commands.handleUpdate(update("/add Amazon Prime 100 monthly 2026-10-15 TRIAL"));

        ArgumentCaptor<CreateSubscriptionRequest> request = ArgumentCaptor.forClass(CreateSubscriptionRequest.class);
        verify(subscriptions).createSubscription(eq(user), request.capture());
        assertTrue(request.getValue().isTrial());
        assertEquals("Amazon Prime", request.getValue().getName());
        assertEquals(new BigDecimal("100"), request.getValue().getAmount());
        assertEquals(LocalDate.of(2026, 10, 15), request.getValue().getNextRenewalDate());
        verify(telegram).sendMessage(eq(123L), contains("(trial)"));
    }

    @Test
    void existingAddSyntaxStillCreatesActiveSubscription() {
        commands.handleUpdate(update("/add Netflix 100 monthly 2026-11-15"));

        verify(subscriptions).createSubscription(eq(user), argThat(request ->
                !request.isTrial() && request.getName().equals("Netflix")));
        verify(telegram).sendMessage(eq(123L), contains("Added Netflix"));
    }

    @Test
    void incompleteTrialCommandDoesNotCreateSubscription() {
        commands.handleUpdate(update("/add Spotify 100 monthly trial"));

        verify(subscriptions, never()).createSubscription(any(), any());
        verify(telegram).sendMessage(eq(123L), contains("incomplete"));
    }

    @Test
    void invalidTrialArgumentsDoNotCreateSubscription() {
        commands.handleUpdate(update("/add Spotify -100 monthly 2026-10-15 trial"));

        verify(subscriptions, never()).createSubscription(any(), any());
        verify(telegram).sendMessage(eq(123L), contains("greater than 0"));
    }

    private TelegramUpdate update(String text) {
        TelegramChat chat = new TelegramChat();
        chat.setId(123L);
        TelegramMessage message = new TelegramMessage();
        message.setChat(chat);
        message.setText(text);
        TelegramUpdate update = new TelegramUpdate();
        update.setMessage(message);
        return update;
    }

    @Test
    void cancelUsesTheCallingUserAndConfirmsTrackingOnlyCancellation() {
        when(subscriptions.cancelSubscription(user, 7L)).thenReturn(response(SubscriptionStatus.CANCELLED));
        commands.handleUpdate(update("/cancel 7"));
        verify(subscriptions).cancelSubscription(user, 7L);
        verify(telegram).sendMessage(eq(123L), contains("cancel with the provider too"));
    }

    @Test
    void keepAcknowledgesWithoutCallingCancel() {
        when(subscriptions.keepSubscription(user, 7L)).thenReturn(response(SubscriptionStatus.ACTIVE));
        commands.handleUpdate(update("/keep 7"));
        verify(subscriptions).keepSubscription(user, 7L);
        verify(subscriptions, never()).cancelSubscription(any(), anyLong());
        verify(telegram).sendMessage(eq(123L), contains("keep tracking"));
    }

    @Test
    void keepOnCancelledSubscriptionDoesNotPromiseReactivation() {
        when(subscriptions.keepSubscription(user, 7L)).thenReturn(response(SubscriptionStatus.CANCELLED));
        commands.handleUpdate(update("/keep 7"));
        verify(telegram).sendMessage(eq(123L), contains("does not reactivate"));
    }

    @Test
    void unownedDecisionReturnsNotFoundReply() {
        when(subscriptions.cancelSubscription(user, 7L)).thenThrow(new ResourceNotFoundException("Not found"));
        commands.handleUpdate(update("/cancel 7"));
        verify(telegram).sendMessage(eq(123L), contains("don't have a subscription with id 7"));
    }

    @Test
    void malformedDecisionCommandsDoNotChangeSubscriptions() {
        commands.handleUpdate(update("/cancel"));
        commands.handleUpdate(update("/keep abc"));
        commands.handleUpdate(update("/cancel -1"));
        commands.handleUpdate(update("/keep 7 extra"));
        verify(subscriptions, never()).cancelSubscription(any(), anyLong());
        verify(subscriptions, never()).keepSubscription(any(), anyLong());
        verify(telegram, times(4)).sendMessage(eq(123L), anyString());
    }

    private SubscriptionResponse response(SubscriptionStatus status) {
        return SubscriptionResponse.builder().id(7L).name("Netflix").status(status)
                .nextRenewalDate(LocalDate.of(2026, 11, 15)).build();
    }

    @Test
    void summaryShowsBreakdownSeparateCurrenciesAndTrialsOutsideTotals() {
        var active = new SubscriptionSummaryResponse.Entry(7L, "Annual Plan", "LKR", new BigDecimal("12000.00"),
                BillingCycle.YEARLY, new BigDecimal("1000.00"), LocalDate.of(2027, 10, 15));
        var usd = new SubscriptionSummaryResponse.Entry(8L, "USD Plan", "USD", new BigDecimal("10.00"),
                BillingCycle.MONTHLY, new BigDecimal("10.00"), LocalDate.of(2026, 11, 15));
        var trial = new SubscriptionSummaryResponse.Entry(9L, "Trial Plan", "LKR", new BigDecimal("500.00"),
                BillingCycle.MONTHLY, new BigDecimal("500.00"), LocalDate.of(2026, 10, 16));
        when(summaries.getSummary(user)).thenReturn(new SubscriptionSummaryResponse(List.of(active, usd),
                List.of(trial), Map.of("LKR", new BigDecimal("1000.00"), "USD", new BigDecimal("10.00"))));

        commands.handleUpdate(update("/summary"));

        verify(summaries).getSummary(user);
        ArgumentCaptor<String> reply = ArgumentCaptor.forClass(String.class);
        verify(telegram).sendMessage(eq(123L), reply.capture());
        assertTrue(reply.getValue().contains("#7 Annual Plan - LKR 1000.00 / month (LKR 12000.00 / yearly)"));
        assertTrue(reply.getValue().contains("Monthly totals (active only):"));
        assertTrue(reply.getValue().contains("LKR 1000.00 / month"));
        assertTrue(reply.getValue().contains("USD 10.00 / month"));
        assertTrue(reply.getValue().contains("Trials (not included in totals):"));
        assertTrue(reply.getValue().contains("#9 Trial Plan - LKR 500.00 / monthly after trial; trial ends 2026-10-16"));
    }

    @Test
    void emptySummarySuggestsAddingSubscription() {
        when(summaries.getSummary(user)).thenReturn(new SubscriptionSummaryResponse(List.of(), List.of(), Map.of()));
        commands.handleUpdate(update("/summary"));
        verify(telegram).sendMessage(123L, "You have no active subscriptions or trials. Add one with /add.");
    }

    @Test
    void trialsOnlySummaryDoesNotShowActiveSpendingTotals() {
        var trial = new SubscriptionSummaryResponse.Entry(9L, "Trial Plan", "LKR", new BigDecimal("500.00"),
                BillingCycle.MONTHLY, new BigDecimal("500.00"), LocalDate.of(2026, 10, 16));
        when(summaries.getSummary(user)).thenReturn(new SubscriptionSummaryResponse(List.of(), List.of(trial), Map.of()));
        commands.handleUpdate(update("/summary"));
        ArgumentCaptor<String> reply = ArgumentCaptor.forClass(String.class);
        verify(telegram).sendMessage(eq(123L), reply.capture());
        assertTrue(reply.getValue().contains("No active subscriptions."));
        assertTrue(reply.getValue().contains("Trial Plan"));
        assertFalse(reply.getValue().contains("Monthly totals"));
    }

    @Test
    void helpListsSummaryCommand() {
        commands.handleUpdate(update("/start"));
        verify(telegram).sendMessage(eq(123L), contains("/summary - see monthly spending totals per currency"));
    }

    @Test
    void settingsShowsCurrentValuesAndUsage() {
        when(users.getSettings(123L)).thenReturn(UserResponse.builder().reminderDaysBefore(3)
                .defaultCurrency("LKR").build());
        commands.handleUpdate(update("/settings"));
        verify(telegram).sendMessage(eq(123L), contains("Fallback email: not set"));
    }

    @Test
    void settingsUpdatesReminderWindowForCallingChat() {
        when(users.updateReminderDaysBefore(123L, 7)).thenReturn(UserResponse.builder().reminderDaysBefore(7).build());
        commands.handleUpdate(update("/settings reminder 7"));
        verify(users).updateReminderDaysBefore(123L, 7);
        verify(telegram).sendMessage(123L, "Reminders set to 7 days before renewal.");
    }

    @Test
    void settingsRejectsNonNumericAndInvalidWindows() {
        when(users.updateReminderDaysBefore(123L, 31)).thenThrow(new IllegalArgumentException("Reminder days must be between 1 and 30."));
        commands.handleUpdate(update("/settings reminder abc"));
        commands.handleUpdate(update("/settings reminder 31"));
        verify(telegram).sendMessage(eq(123L), contains("whole number"));
        verify(telegram).sendMessage(123L, "Reminder days must be between 1 and 30.");
    }

    @Test
    void settingsUpdatesCurrencyAndEmailAndCanRemoveEmail() {
        when(users.updateDefaultCurrency(123L, "usd")).thenReturn(UserResponse.builder().defaultCurrency("USD").build());
        when(users.updateEmail(123L, "user@example.com")).thenReturn(UserResponse.builder().email("user@example.com").build());
        when(users.updateEmail(123L, "off")).thenReturn(UserResponse.builder().build());
        commands.handleUpdate(update("/settings currency usd"));
        commands.handleUpdate(update("/settings email user@example.com"));
        commands.handleUpdate(update("/settings email off"));
        verify(telegram).sendMessage(eq(123L), contains("for new subscriptions"));
        verify(telegram).sendMessage(eq(123L), contains("Fallback email saved"));
        verify(telegram).sendMessage(123L, "Fallback email removed.");
    }
}
