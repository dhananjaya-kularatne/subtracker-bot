package com.subtracker.bot.service;

import com.subtracker.bot.exception.ResourceNotFoundException;
import com.subtracker.bot.model.*;
import com.subtracker.bot.repository.ReminderLogRepository;
import com.subtracker.bot.repository.SubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SubscriptionDecisionTests {

    private SubscriptionRepository repository;
    private SubscriptionService service;
    private User user;

    @BeforeEach
    void setUp() {
        repository = mock(SubscriptionRepository.class);
        service = new SubscriptionService(repository, mock(ReminderLogRepository.class));
        user = User.builder().id(1L).build();
    }

    @Test
    void cancellationStopsTrackingAndClearsPendingNoticeWithoutDeleting() {
        Subscription subscription = subscription(SubscriptionStatus.TRIAL);
        subscription.setPendingTrialConversionDate(LocalDate.of(2026, 10, 12));
        when(repository.findByIdAndUser(7L, user)).thenReturn(Optional.of(subscription));
        var response = service.cancelSubscription(user, 7L);
        assertEquals(SubscriptionStatus.CANCELLED, response.getStatus());
        assertEquals(SubscriptionStatus.CANCELLED, subscription.getStatus());
        assertNull(subscription.getPendingTrialConversionDate());
        verify(repository, never()).delete(any());
    }

    @Test
    void keepPreservesStatusDateAndPendingNotice() {
        Subscription subscription = subscription(SubscriptionStatus.ACTIVE);
        LocalDate pending = LocalDate.of(2026, 10, 12);
        subscription.setPendingTrialConversionDate(pending);
        when(repository.findByIdAndUser(7L, user)).thenReturn(Optional.of(subscription));
        service.keepSubscription(user, 7L);
        assertEquals(SubscriptionStatus.ACTIVE, subscription.getStatus());
        assertEquals(LocalDate.of(2026, 11, 15), subscription.getNextRenewalDate());
        assertEquals(pending, subscription.getPendingTrialConversionDate());
        verify(repository, never()).save(any());
    }

    @Test
    void decisionsRequireOwnershipAndNeverFallBackToIdOnlyLookup() {
        when(repository.findByIdAndUser(7L, user)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.cancelSubscription(user, 7L));
        assertThrows(ResourceNotFoundException.class, () -> service.keepSubscription(user, 7L));
        verify(repository, never()).findById(anyLong());
        verify(repository, never()).save(any());
    }

    private Subscription subscription(SubscriptionStatus status) {
        return Subscription.builder().id(7L).user(user).name("Netflix").status(status)
                .nextRenewalDate(LocalDate.of(2026, 11, 15)).build();
    }
}
