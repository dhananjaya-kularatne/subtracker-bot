package com.subtracker.bot.service;

import com.subtracker.bot.dto.CreateSubscriptionRequest;
import com.subtracker.bot.dto.SubscriptionResponse;
import com.subtracker.bot.exception.ResourceNotFoundException;
import com.subtracker.bot.model.*;
import com.subtracker.bot.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Business logic for creating, reading, and deleting Subscriptions.
 */
@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;

    // Create a new subscription for a given user
    @Transactional
    public SubscriptionResponse createSubscription(User user, CreateSubscriptionRequest request) {

        // currency falls back to the user's default if not given
        String currency = request.getCurrency() != null
                ? request.getCurrency()
                : user.getDefaultCurrency();

        // isTrial flag decides the starting status
        SubscriptionStatus status = request.isTrial()
                ? SubscriptionStatus.TRIAL
                : SubscriptionStatus.ACTIVE;

        Subscription subscription = Subscription.builder()
                .user(user)
                .name(request.getName().trim())
                .amount(request.getAmount())
                .currency(currency)
                .billingCycle(request.getBillingCycle())
                .status(status)
                .nextRenewalDate(request.getNextRenewalDate())
                .build();

        Subscription saved = subscriptionRepository.save(subscription);
        return mapToResponse(saved);
    }

    // Get all subscriptions belonging to a user
    @Transactional(readOnly = true)
    public List<SubscriptionResponse> getSubscriptionsForUser(User user) {
        return subscriptionRepository.findByUser(user)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    // Get a single subscription by id, throws if not found
    @Transactional(readOnly = true)
    public SubscriptionResponse getSubscriptionById(Long id) {
        Subscription subscription = subscriptionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Subscription not found with id: " + id));
        return mapToResponse(subscription);
    }

    // Permanently delete a subscription
    @Transactional
    public void deleteSubscription(Long id) {
        if (!subscriptionRepository.existsById(id)) {
            throw new ResourceNotFoundException("Subscription not found with id: " + id);
        }
        subscriptionRepository.deleteById(id);
    }

    // Maps an entity to its client facing response shape
    private SubscriptionResponse mapToResponse(Subscription subscription) {
        return SubscriptionResponse.builder()
                .id(subscription.getId())
                .name(subscription.getName())
                .amount(subscription.getAmount())
                .currency(subscription.getCurrency())
                .billingCycle(subscription.getBillingCycle())
                .status(subscription.getStatus())
                .nextRenewalDate(subscription.getNextRenewalDate())
                .createdAt(subscription.getCreatedAt())
                .build();
    }
}