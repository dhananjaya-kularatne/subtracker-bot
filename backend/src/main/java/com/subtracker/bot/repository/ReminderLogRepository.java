package com.subtracker.bot.repository;

import com.subtracker.bot.model.ReminderLog;
import com.subtracker.bot.model.ReminderType;
import com.subtracker.bot.model.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface ReminderLogRepository extends JpaRepository<ReminderLog, Long> {

    // Idempotency check: has a notification of this type already been sent
    // for this subscription since the given cutoff (e.g. start of today)?
    // The scheduled job calls this BEFORE sending, to avoid double-notifying
    // on a cron overlap or job re-run.
    boolean existsBySubscriptionAndTypeAndSentAtAfter(
            Subscription subscription, ReminderType type, LocalDateTime cutoff);
}