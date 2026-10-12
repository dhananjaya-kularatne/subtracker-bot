package com.subtracker.bot.repository;

import com.subtracker.bot.model.Subscription;
import com.subtracker.bot.model.SubscriptionStatus;
import com.subtracker.bot.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Collection;

@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    // Fetch the user too because scheduled jobs do not have a web request session.
    @EntityGraph(attributePaths = "user")
    List<Subscription> findByStatusInAndNextRenewalDateBetween(
            Collection<SubscriptionStatus> statuses, LocalDate start, LocalDate end);

    // All subscriptions belonging to one user — used by /list and /summary
    List<Subscription> findByUser(User user);

    // One subscription by id, but only if it belongs to the given user —
    // used by the bot's /delete so a user can't touch someone else's row
    Optional<Subscription> findByIdAndUser(Long id, User user);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Subscription s join fetch s.user where s.id = :id")
    Optional<Subscription> findByIdForUpdate(@Param("id") Long id);

    // All subscriptions belonging to one user, filtered by status —
    // e.g. only ACTIVE ones for the spend summary
    List<Subscription> findByUserAndStatus(User user, SubscriptionStatus status);

    @Query("""
            select s.id from Subscription s
            where s.status in :statuses
              and (s.nextRenewalDate <= :today or s.pendingTrialConversionDate is not null)
            """)
    List<Long> findDueOrPendingConversionIds(
            @Param("statuses") Collection<SubscriptionStatus> statuses, @Param("today") LocalDate today);
}
