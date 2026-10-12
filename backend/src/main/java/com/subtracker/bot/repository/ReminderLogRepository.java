package com.subtracker.bot.repository;

import com.subtracker.bot.model.ReminderLog;
import com.subtracker.bot.model.ReminderType;
import com.subtracker.bot.model.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;

@Repository
public interface ReminderLogRepository extends JpaRepository<ReminderLog, Long> {

    // Check for successful delivery in the same billing cycle before sending.
    boolean existsBySubscriptionAndTypeAndRenewalDate(
            Subscription subscription, ReminderType type, LocalDate renewalDate);

    void deleteBySubscription(Subscription subscription);

}
