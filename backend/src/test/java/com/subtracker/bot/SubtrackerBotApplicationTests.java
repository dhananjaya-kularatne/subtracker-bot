package com.subtracker.bot;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.subtracker.bot.service.TelegramPollingService;
import com.subtracker.bot.service.TelegramApiClient;
import com.subtracker.bot.service.BrevoEmailClient;
import com.subtracker.bot.service.UserService;
import com.subtracker.bot.service.SubscriptionRenewalService;
import com.subtracker.bot.service.SubscriptionService;
import com.subtracker.bot.repository.SubscriptionRepository;
import com.subtracker.bot.repository.ReminderLogRepository;
import com.subtracker.bot.repository.UserRepository;
import com.subtracker.bot.model.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = "reminder.cron=-")
class SubtrackerBotApplicationTests {

    // Context verification must not start a second Telegram polling connection.
    @MockitoBean
    private TelegramPollingService telegramPollingService;

    @MockitoBean
    private TelegramApiClient telegramApiClient;

    @MockitoBean
    private BrevoEmailClient brevoEmailClient;

    @Autowired
    private UserService userService;

    @Autowired
    private SubscriptionRenewalService renewals;

    @Autowired
    private SubscriptionService subscriptionService;

    @Autowired
    private SubscriptionRepository subscriptions;

    @Autowired
    private ReminderLogRepository logs;

    @Autowired
    private UserRepository users;

    @Autowired
    private Clock reminderClock;

	@Test
	void contextLoads() {
	}

    @Test
    void conversionAndFailedNoticeRetryPersistAcrossSeparateTransactions() {
        LocalDate today = LocalDate.now(reminderClock);
        User user = users.save(User.builder()
                .telegramChatId(-ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE))
                .defaultCurrency("LKR").reminderDaysBefore(3).build());
        Subscription trial = null;
        try {
            trial = subscriptions.save(Subscription.builder().user(user).name("Integration Trial")
                    .amount(new BigDecimal("100.00")).currency("LKR").billingCycle(BillingCycle.MONTHLY)
                    .status(SubscriptionStatus.TRIAL).nextRenewalDate(today).build());
            when(telegramApiClient.sendMessage(eq(user.getTelegramChatId()), anyString())).thenReturn(false, true);

            renewals.processSubscription(trial.getId());
            Subscription failedSend = subscriptions.findById(trial.getId()).orElseThrow();
            assertEquals(SubscriptionStatus.ACTIVE, failedSend.getStatus());
            assertEquals(today.plusMonths(1), failedSend.getNextRenewalDate());
            assertEquals(today, failedSend.getPendingTrialConversionDate());
            assertFalse(logs.existsBySubscriptionAndTypeAndRenewalDate(trial, ReminderType.TRIAL_CONVERSION, today));

            assertTrue(subscriptions.findDueOrPendingConversionIds(
                    java.util.List.of(SubscriptionStatus.TRIAL, SubscriptionStatus.ACTIVE), today).contains(trial.getId()));
            renewals.processSubscription(trial.getId());
            renewals.processSubscription(trial.getId());
            Subscription delivered = subscriptions.findById(trial.getId()).orElseThrow();
            assertNull(delivered.getPendingTrialConversionDate());
            assertEquals(today.plusMonths(1), delivered.getNextRenewalDate());
            assertTrue(logs.existsBySubscriptionAndTypeAndRenewalDate(trial, ReminderType.TRIAL_CONVERSION, today));
            verify(telegramApiClient, times(2)).sendMessage(eq(user.getTelegramChatId()), anyString());

            subscriptionService.cancelSubscription(user, trial.getId());
            assertEquals(SubscriptionStatus.CANCELLED, subscriptions.findById(trial.getId()).orElseThrow().getStatus());
        } finally {
            if (trial != null) {
                subscriptionService.deleteSubscription(user, trial.getId());
            }
            users.delete(user);
        }
    }
    @Test
    void settingsPersistAcrossTransactions() {
        long chatId = -ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);
        User user = userService.findOrCreateByTelegramChatId(chatId);
        try {
            userService.updateReminderDaysBefore(chatId, 7);
            userService.updateDefaultCurrency(chatId, "usd");
            userService.updateEmail(chatId, "user@example.com");
            User stored = users.findByTelegramChatId(chatId).orElseThrow();
            assertEquals(7, stored.getReminderDaysBefore());
            assertEquals("USD", stored.getDefaultCurrency());
            assertEquals("user@example.com", stored.getEmail());
            userService.updateEmail(chatId, "off");
            assertNull(users.findByTelegramChatId(chatId).orElseThrow().getEmail());
        } finally {
            users.delete(user);
        }
    }

}
