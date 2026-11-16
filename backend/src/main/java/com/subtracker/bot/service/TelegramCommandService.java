package com.subtracker.bot.service;

import com.subtracker.bot.dto.CreateSubscriptionRequest;
import com.subtracker.bot.dto.SubscriptionResponse;
import com.subtracker.bot.dto.TelegramUpdate;
import com.subtracker.bot.dto.SubscriptionSummaryResponse;
import com.subtracker.bot.dto.UserResponse;
import com.subtracker.bot.exception.ResourceNotFoundException;
import com.subtracker.bot.model.BillingCycle;
import com.subtracker.bot.model.User;
import com.subtracker.bot.model.SubscriptionStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Clock;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Parses incoming Telegram messages into commands and routes them to the appropriate handler. This is the "brain" behind the bot's chat interface -
 * it never touches HTTP directly, and never touches Telegram's API directly except through TelegramApiClient.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TelegramCommandService {

    private final TelegramApiClient telegramApiClient;
    private final UserService userService;
    private final SubscriptionService subscriptionService;
    private final SubscriptionSummaryService subscriptionSummaryService;
    private final Clock reminderClock;

    private String addUsage() {
        LocalDate today = LocalDate.now(reminderClock);
        return """
                Add a subscription:
                /add <name> <amount> <monthly|yearly> <yyyy-MM-dd> [trial]

                Monthly example:
                /add Netflix 1500 monthly %s

                Free trial example:
                /add Spotify 590 monthly %s trial

                Send one command per message. Names can include spaces.
                """.formatted(today.plusMonths(1), today.plusDays(3)).stripTrailing();
    }

    private static final String DELETE_USAGE = """
            Usage: /delete <id>
            Example: /delete 3
            Run /list to see each subscription's id.""";

    public void handleUpdate(TelegramUpdate update) {

        // Ignore updates that aren't a plain text message
        if (update.getMessage() == null || update.getMessage().getText() == null) {
            return;
        }

        Long chatId = update.getMessage().getChat().getId();
        String text = update.getMessage().getText().trim();

        log.info("Received message from chatId {}: {}", chatId, text);

        // Route based on the first word of the message
        String command = text.split("\\s+")[0].toLowerCase(Locale.ROOT);

        if (text.lines().skip(1).anyMatch(line -> line.stripLeading().startsWith("/"))) {
            telegramApiClient.sendMessage(chatId,
                    "Let's do one command at a time. Send each command as a separate message, and I'll confirm each one.");
            return;
        }

        switch (command) {
            case "/start", "/help" -> handleStart(chatId);
            case "/add" -> handleAdd(chatId, text);
            case "/list" -> handleList(chatId);
            case "/summary" -> handleSummary(chatId);
            case "/settings" -> handleSettings(chatId, text);
            case "/delete" -> handleDelete(chatId, text);
            case "/cancel" -> handleDecision(chatId, text, true);
            case "/keep" -> handleDecision(chatId, text, false);
            default -> handleUnknownCommand(chatId);
        }
    }

    /**
     * Handles /list - shows every subscription belonging to the caller, each
     * prefixed with its id so it can be passed straight to /delete.
     */
    private void handleList(Long chatId) {
        User user = userService.findOrCreateByTelegramChatId(chatId);
        List<SubscriptionResponse> subscriptions = subscriptionService.getSubscriptionsForUser(user);

        if (subscriptions.isEmpty()) {
            telegramApiClient.sendMessage(chatId,
                    "Your subscription list is empty. Let's add your first one.\n\n" + addUsage());
            return;
        }

        telegramApiClient.sendMessage(chatId, formatSubscriptionList(subscriptions));
    }

    private String formatSubscriptionList(List<SubscriptionResponse> subscriptions) {
        StringBuilder sb = new StringBuilder("📋 Your subscriptions\n");
        for (SubscriptionResponse sub : subscriptions) {
            String status = switch (sub.getStatus()) {
                case ACTIVE -> "Active";
                case TRIAL -> "Free trial";
                case CANCELLED -> "Cancelled - reminders stopped";
            };
            String dateLabel = sub.getStatus() == SubscriptionStatus.TRIAL ? "Trial ends" : "Next renewal";
            sb.append("\n%s · #%d\n%s / %s\nStatus: %s\n".formatted(
                    sub.getName(), sub.getId(), MessageFormatting.money(sub.getCurrency(), sub.getAmount()),
                    MessageFormatting.period(sub.getBillingCycle()), status));
            if (sub.getStatus() != SubscriptionStatus.CANCELLED) {
                sb.append("%s: %s\n".formatted(dateLabel, MessageFormatting.date(sub.getNextRenewalDate())));
            }
        }
        sb.append("\n/summary - monthly spending\n/cancel <id> - stop tracking reminders\n/delete <id> - permanently remove\n\nExample: /delete ")
                .append(subscriptions.get(0).getId());
        return sb.toString();
    }

    private void handleSummary(Long chatId) {
        User user = userService.findOrCreateByTelegramChatId(chatId);
        SubscriptionSummaryResponse summary = subscriptionSummaryService.getSummary(user);
        if (summary.activeSubscriptions().isEmpty() && summary.trialSubscriptions().isEmpty()) {
            telegramApiClient.sendMessage(chatId, "No active subscriptions or trials to total yet.\n\n" + addUsage());
            return;
        }

        StringBuilder message = new StringBuilder("💰 Your monthly spending\n");
        if (summary.activeSubscriptions().isEmpty()) {
            message.append("\nNo active subscriptions.\n");
        } else {
            message.append("\nMonthly totals (active only):\n");
            summary.monthlyTotals().forEach((currency, total) ->
                    message.append("%s / month\n".formatted(MessageFormatting.money(currency, total))));
            message.append("\nActive subscriptions\n");
            for (SubscriptionSummaryResponse.Entry entry : summary.activeSubscriptions()) {
                message.append("\n%s · #%d\n%s / month\n".formatted(
                        entry.name(), entry.id(), MessageFormatting.money(entry.currency(), entry.monthlyEquivalent())));
                if (entry.billingCycle() == BillingCycle.YEARLY) {
                    message.append("Billed %s / year\n".formatted(MessageFormatting.money(entry.currency(), entry.amount())));
                }
            }
            message.append("\nYearly plans are shown as monthly equivalents. Currencies are totaled separately.\n");
        }

        if (!summary.trialSubscriptions().isEmpty()) {
            message.append("\n⏳ Free trials (not included in totals)\n");
            for (SubscriptionSummaryResponse.Entry entry : summary.trialSubscriptions()) {
                message.append("\n%s · #%d\nAfter trial: %s / %s\nTrial ends: %s\n".formatted(
                        entry.name(), entry.id(), MessageFormatting.money(entry.currency(), entry.amount()),
                        MessageFormatting.period(entry.billingCycle()), MessageFormatting.date(entry.nextRenewalDate())));
            }
        }
        message.append("\n/list - manage your subscriptions");
        telegramApiClient.sendMessage(chatId, message.toString().stripTrailing());
    }

    /**
     * Handles /delete &lt;id&gt;. The id comes from an untrusted chat message, so the
     * delete goes through the ownership-checked service path - an id that isn't the
     * caller's own is reported the same as one that doesn't exist.
     */
    private void handleDelete(Long chatId, String text) {
        String[] tokens = text.split("\\s+");

        if (tokens.length != 2) {
            telegramApiClient.sendMessage(chatId, "Which one? I need an id.\n\n" + DELETE_USAGE);
            return;
        }

        long id;
        try {
            id = Long.parseLong(tokens[1]);
            if (id <= 0) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException ex) {
            telegramApiClient.sendMessage(chatId,
                    "\"" + tokens[1] + "\" isn't a valid id.\n\n" + DELETE_USAGE);
            return;
        }

        User user = userService.findOrCreateByTelegramChatId(chatId);
        try {
            SubscriptionResponse deleted = subscriptionService.deleteSubscription(user, id);
            telegramApiClient.sendMessage(chatId, "🗑️ Deleted " + deleted.getName()
                    + ".\nIt's been removed from your tracker.\n\n/list - see what's left");
        } catch (ResourceNotFoundException ex) {
            telegramApiClient.sendMessage(chatId,
                    "You don't have a subscription with id " + id + ". Run /list to see yours.");
        }
    }

    private void handleStart(Long chatId) {
        String welcomeMessage = """
                👋 Welcome to Subtracker
                Keep your subscriptions in one place and get a heads-up before renewal.

                Start here
                /list - see your subscriptions and their IDs
                /summary - see monthly spending totals per currency
                /settings - set reminder days, currency, and email backup

                Manage a subscription
                /keep <id> - keep tracking it
                /cancel <id> - stop reminders in your tracker
                /delete <id> - permanently remove it

                """ + addUsage() + "\n\nNeed a hand? Send /help anytime.";
        telegramApiClient.sendMessage(chatId, welcomeMessage);
    }

    private void handleDecision(Long chatId, String text, boolean cancel) {
        String command = cancel ? "/cancel" : "/keep";
        String[] tokens = text.split("\\s+");
        if (tokens.length != 2) {
            telegramApiClient.sendMessage(chatId, "Usage: " + command + " <id>. Run /list to see your ids.");
            return;
        }

        long id;
        try {
            id = Long.parseLong(tokens[1]);
            if (id <= 0) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException exception) {
            telegramApiClient.sendMessage(chatId, "I need a positive numeric id. Usage: " + command + " <id>.");
            return;
        }

        User user = userService.findOrCreateByTelegramChatId(chatId);
        try {
            SubscriptionResponse subscription = cancel
                    ? subscriptionService.cancelSubscription(user, id)
                    : subscriptionService.keepSubscription(user, id);
            String reply;
            if (cancel) {
                reply = "✅ Reminders stopped\n\n%s · #%d\nStatus: Cancelled\n\nTo stop actual charges, cancel with the provider too.\n\n/list - manage your subscriptions"
                        .formatted(subscription.getName(), id);
            } else if (subscription.getStatus() == SubscriptionStatus.CANCELLED) {
                reply = "%s is already cancelled in your tracker. /keep does not reactivate it.\n\nTo track it again, add a new subscription with /add."
                        .formatted(subscription.getName());
            } else {
                String dateLabel = subscription.getStatus() == SubscriptionStatus.TRIAL ? "Trial ends" : "Next renewal";
                reply = "👍 Got it. I'll keep tracking %s.\n%s: %s\n\nYou're all set. /list shows your subscriptions."
                        .formatted(subscription.getName(), dateLabel, MessageFormatting.date(subscription.getNextRenewalDate()));
            }
            telegramApiClient.sendMessage(chatId, reply);
        } catch (ResourceNotFoundException exception) {
            telegramApiClient.sendMessage(chatId,
                    "You don't have a subscription with id " + id + ". Run /list to see yours.");
        }
    }

    private void handleSettings(Long chatId, String text) {
        String[] tokens = text.split("\\s+");
        if (tokens.length == 1) {
            UserResponse settings = userService.getSettings(chatId);
            telegramApiClient.sendMessage(chatId, """
                    ⚙️ Your settings

                    Remind me: %d days before renewal
                    Default currency: %s
                    Email backup: %s

                    Change a setting
                    /settings reminder <1-30>
                    /settings currency <code>, e.g. USD
                    /settings email <address>, or off to remove it

                    Email backup is used if Telegram can't deliver a reminder and email delivery is configured.
                    """.formatted(settings.getReminderDaysBefore(), settings.getDefaultCurrency(),
                    settings.getEmail() == null ? "not set" : settings.getEmail()).stripTrailing());
            return;
        }
        if (tokens.length != 3) {
            telegramApiClient.sendMessage(chatId, "Usage: /settings reminder <1-30>, currency <code>, or email <address|off>.");
            return;
        }
        try {
            switch (tokens[1].toLowerCase(Locale.ROOT)) {
                case "reminder" -> {
                    int days;
                    try {
                        days = Integer.parseInt(tokens[2]);
                    } catch (NumberFormatException exception) {
                        throw new IllegalArgumentException("Reminder days must be a whole number between 1 and 30.");
                    }
                    UserResponse settings = userService.updateReminderDaysBefore(chatId, days);
                    telegramApiClient.sendMessage(chatId, "Reminders set to " + settings.getReminderDaysBefore() + " days before renewal.");
                }
                case "currency" -> {
                    UserResponse settings = userService.updateDefaultCurrency(chatId, tokens[2]);
                    telegramApiClient.sendMessage(chatId, "Default currency set to " + settings.getDefaultCurrency()
                            + " for new subscriptions. Existing subscriptions keep their currency.");
                }
                case "email" -> {
                    UserResponse settings = userService.updateEmail(chatId, tokens[2]);
                    telegramApiClient.sendMessage(chatId, settings.getEmail() == null ? "Email backup removed. You'll still get Telegram reminders."
                            : "✅ Email backup saved.\nWe'll try this address if Telegram can't deliver a reminder and email delivery is configured.\n\n/settings - check your preferences");
                }
                default -> telegramApiClient.sendMessage(chatId, "Unknown setting. Use /settings to see the options.");
            }
        } catch (IllegalArgumentException exception) {
            telegramApiClient.sendMessage(chatId, exception.getMessage());
        }
    }

    /**
     * Handles /add with an optional trailing trial token.
     * The name may be several words: after removing the trial token, the last three tokens are the amount,
     * billing cycle and renewal date, so everything between the command and those
     * three is joined back together as the name (no quoting needed for "Amazon Prime").
     */
    private void handleAdd(Long chatId, String text) {
        String[] tokens = text.split("\\s+");

        boolean isTrial = tokens[tokens.length - 1].equalsIgnoreCase("trial");
        int argumentEnd = tokens.length - (isTrial ? 1 : 0);

        // /add + at least a one-word name + amount + cycle + date = 5 tokens minimum
        if (argumentEnd < 5) {
            telegramApiClient.sendMessage(chatId, "That command looks incomplete. Here's how to add one.\n\n" + addUsage());
            return;
        }

        int amountIndex = argumentEnd - 3;
        int cycleIndex = argumentEnd - 2;
        int dateIndex = argumentEnd - 1;

        String name = String.join(" ", Arrays.copyOfRange(tokens, 1, amountIndex));

        try {
            CreateSubscriptionRequest request = CreateSubscriptionRequest.builder()
                    .name(name)
                    .amount(parseAmount(tokens[amountIndex]))
                    .billingCycle(parseCycle(tokens[cycleIndex]))
                    .nextRenewalDate(parseDate(tokens[dateIndex]))
                    .isTrial(isTrial)
                    .build();

            User user = userService.findOrCreateByTelegramChatId(chatId);
            SubscriptionResponse saved = subscriptionService.createSubscription(user, request);

            telegramApiClient.sendMessage(chatId, formatAddConfirmation(saved));

        } catch (CommandFormatException ex) {
            // Malformed argument - tell the user what was wrong and how to fix it,
            // rather than letting the exception bubble up and drop the message silently.
            telegramApiClient.sendMessage(chatId, ex.getMessage() + "\n\n" + addUsage());
        }
    }

    private BigDecimal parseAmount(String raw) {
        BigDecimal amount;
        try {
            amount = new BigDecimal(raw);
        } catch (NumberFormatException ex) {
            throw new CommandFormatException("I couldn't read \"" + raw + "\" as an amount.");
        }
        if (amount.signum() <= 0) {
            throw new CommandFormatException("The amount needs to be greater than 0.");
        }
        return amount;
    }

    private BillingCycle parseCycle(String raw) {
        try {
            return BillingCycle.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new CommandFormatException(
                    "The billing cycle should be \"monthly\" or \"yearly\", not \"" + raw + "\".");
        }
    }

    private LocalDate parseDate(String raw) {
        try {
            return LocalDate.parse(raw);
        } catch (DateTimeParseException ex) {
            throw new CommandFormatException(
                    "I couldn't read \"" + raw + "\" as a date. Use the format yyyy-MM-dd.");
        }
    }

    private String formatAddConfirmation(SubscriptionResponse sub) {
        boolean trial = sub.getStatus() == SubscriptionStatus.TRIAL;
        return "✅ Added %s\n\nSubscription #%d\n%s: %s / %s\n%s: %s\nStatus: %s\n\n/list - see all subscriptions\n/summary - check your monthly spending"
                .formatted(sub.getName(), sub.getId(), trial ? "After trial" : "Price",
                        MessageFormatting.money(sub.getCurrency(), sub.getAmount()),
                        MessageFormatting.period(sub.getBillingCycle()), trial ? "Trial ends" : "Next renewal",
                        MessageFormatting.date(sub.getNextRenewalDate()), trial ? "Free trial" : "Active");
    }

    private void handleUnknownCommand(Long chatId) {
        telegramApiClient.sendMessage(chatId,
                "I couldn't match that command. Start it with / and send one command per message.\n\n/list - your subscriptions\n/add - add a subscription\n/help - all commands");
    }

    /**
     * Thrown by the parse helpers when a command's arguments are malformed.
     * Carries a user-facing explanation; the command handler turns it into a chat reply.
     */
    private static class CommandFormatException extends RuntimeException {
        CommandFormatException(String message) {
            super(message);
        }
    }
}
