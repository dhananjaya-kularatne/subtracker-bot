package com.subtracker.bot.repository;

import com.subtracker.bot.model.Subscription;
import com.subtracker.bot.model.SubscriptionStatus;
import com.subtracker.bot.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    // All subscriptions belonging to one user — used by /list and /summary
    List<Subscription> findByUser(User user);

    // One subscription by id, but only if it belongs to the given user —
    // used by the bot's /delete so a user can't touch someone else's row
    Optional<Subscription> findByIdAndUser(Long id, User user);

    // All subscriptions belonging to one user, filtered by status —
    // e.g. only ACTIVE ones for the spend summary
    List<Subscription> findByUserAndStatus(User user, SubscriptionStatus status);

    // Used by the daily scheduled job: find every subscription of a given
    // status whose next_renewal_date falls on or before the target date.
    // Covers both "ACTIVE subs due for a reminder" and "TRIAL subs due to convert".
    List<Subscription> findByStatusAndNextRenewalDateLessThanEqual(
            SubscriptionStatus status, LocalDate date);
}