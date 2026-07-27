package com.subtracker.bot.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Represents a single tracked subscription belonging to a User.
 * Holds the renewal schedule and current status. 
 */
@Entity
@Table(name = "subscriptions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Subscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // The user this subscription belongs to
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // Display name of the subscription, e.g. "Netflix", "Spotify"
    @Column(name = "name", nullable = false, length = 100)
    private String name;

    // The charge amount per billing cycle (already the post-trial price, if TRIAL)
    @Column(name = "amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    // Optional per-subscription currency override; falls back to user's default if null
    @Column(name = "currency", length = 10)
    private String currency;

    // MONTHLY or YEARLY — drives how next_renewal_date advances
    @Enumerated(EnumType.STRING)
    @Column(name = "billing_cycle", nullable = false, length = 20)
    private BillingCycle billingCycle;

    // TRIAL, ACTIVE, or CANCELLED — drives reminder/conversion behavior
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SubscriptionStatus status;

    // The next date this subscription renews or converts (trial -> active)
    @Column(name = "next_renewal_date", nullable = false)
    private LocalDate nextRenewalDate;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // Updates automatically every time this row changes (status flips, date advances, etc.)
    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}