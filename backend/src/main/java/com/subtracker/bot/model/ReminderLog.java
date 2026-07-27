package com.subtracker.bot.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Records every notification sent to a user about a Subscription, either a renewal reminder or a trial-to-paid conversion notice
 */
@Entity
@Table(name = "reminder_logs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReminderLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // The subscription this notification was about
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscription_id", nullable = false)
    private Subscription subscription;

    // Which channel the notification was sent through
    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 20)
    private ReminderChannel channel;

    // What kind of notification this was
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 30)
    private ReminderType type;

    // When this notification was actually sent, set automatically on insert
    @CreationTimestamp
    @Column(name = "sent_at", nullable = false, updatable = false)
    private LocalDateTime sentAt;
}