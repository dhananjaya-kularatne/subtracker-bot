package com.subtracker.bot.model;

/**
 * The kind of notification a ReminderLog entry represents.
 * RENEWAL_REMINDER  -> sent N days before an ACTIVE subscription renews
 * TRIAL_CONVERSION  -> sent the moment a TRIAL subscription converts to ACTIVE
 */
public enum ReminderType {
    RENEWAL_REMINDER,
    TRIAL_CONVERSION
}