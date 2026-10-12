package com.subtracker.bot.service;

import com.subtracker.bot.dto.*;
import com.subtracker.bot.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import com.subtracker.bot.exception.ResourceNotFoundException;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TelegramCommandServiceTests {

    private TelegramApiClient telegram;
    private UserService users;
    private SubscriptionService subscriptions;
    private TelegramCommandService commands;
    private User user;

    @BeforeEach
    void setUp() {
        telegram = mock(TelegramApiClient.class);
        users = mock(UserService.class);
        subscriptions = mock(SubscriptionService.class);
        commands = new TelegramCommandService(telegram, users, subscriptions);
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
}
