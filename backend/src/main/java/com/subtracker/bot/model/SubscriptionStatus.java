package com.subtracker.bot.model;

/**
 * Lifecycle state of a Subscription.
 * TRIAL   -> not yet charged; auto-converts to ACTIVE on next_renewal_date
 * ACTIVE  -> being charged on schedule
 * CANCELLED -> no longer tracked for renewals or reminders
 */
public enum SubscriptionStatus {
    TRIAL,
    ACTIVE,
    CANCELLED
}