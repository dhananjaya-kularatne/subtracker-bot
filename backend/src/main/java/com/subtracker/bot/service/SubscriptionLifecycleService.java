package com.subtracker.bot.service;

import com.subtracker.bot.model.SubscriptionStatus;
import com.subtracker.bot.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class SubscriptionLifecycleService {

    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionRenewalService subscriptionRenewalService;
    private final Clock reminderClock;

    public synchronized void processDueSubscriptions() {
        List<Long> ids = subscriptionRepository.findDueOrPendingConversionIds(
                List.of(SubscriptionStatus.TRIAL, SubscriptionStatus.ACTIVE), LocalDate.now(reminderClock));
        for (Long id : ids) {
            try {
                subscriptionRenewalService.processSubscription(id);
            } catch (Exception exception) {
                log.error("Failed to process renewal for subscription {}", id, exception);
            }
        }
    }
}
