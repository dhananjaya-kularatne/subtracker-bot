package com.subtracker.bot.service;

import com.subtracker.bot.dto.CreateSubscriptionRequest;
import com.subtracker.bot.dto.SubscriptionResponse;
import com.subtracker.bot.dto.TelegramUpdate;
import com.subtracker.bot.model.BillingCycle;
import com.subtracker.bot.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;

/**
 * Parses incoming Telegram messages into commands and routes them to the appropriate handler. This is the "brain" behind the bot's chat interface —
 * it never touches HTTP directly, and never touches Telegram's API directly except through TelegramApiClient.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TelegramCommandService {

    private final TelegramApiClient telegramApiClient;
    private final UserService userService;
    private final SubscriptionService subscriptionService;

    private static final String ADD_USAGE = """
            Usage: /add <name> <amount> <monthly|yearly> <renewal date>
            Example: /add Netflix 1500 monthly 2026-08-15""";

    public void handleUpdate(TelegramUpdate update) {

        // Ignore updates that aren't a plain text message
        if (update.getMessage() == null || update.getMessage().getText() == null) {
            return;
        }

        Long chatId = update.getMessage().getChat().getId();
        String text = update.getMessage().getText().trim();

        log.info("Received message from chatId {}: {}", chatId, text);

        // Route based on the first word of the message
        String command = text.split("\\s+")[0].toLowerCase();

        switch (command) {
            case "/start" -> handleStart(chatId);
            case "/add" -> handleAdd(chatId, text);
            case "/list" -> handleList(chatId);
            default -> handleUnknownCommand(chatId);
        }
    }

    /**
     * Handles /list — shows every subscription belonging to the caller, each
     * prefixed with its id so it can be passed straight to /delete.
     */
    private void handleList(Long chatId) {
        User user = userService.findOrCreateByTelegramChatId(chatId);
        List<SubscriptionResponse> subscriptions = subscriptionService.getSubscriptionsForUser(user);

        if (subscriptions.isEmpty()) {
            telegramApiClient.sendMessage(chatId,
                    "You have no subscriptions yet. Add one with /add.");
            return;
        }

        telegramApiClient.sendMessage(chatId, formatSubscriptionList(subscriptions));
    }

    private String formatSubscriptionList(List<SubscriptionResponse> subscriptions) {
        StringBuilder sb = new StringBuilder("Your subscriptions:\n");
        for (SubscriptionResponse sub : subscriptions) {
            sb.append("\n#%d  %s — %s %s / %s\n     renews %s · %s\n".formatted(
                    sub.getId(),
                    sub.getName(),
                    sub.getCurrency(),
                    sub.getAmount().toPlainString(),
                    sub.getBillingCycle().name().toLowerCase(),
                    sub.getNextRenewalDate(),
                    sub.getStatus().name().toLowerCase()));
        }
        sb.append("\nRemove one with /delete <id>, e.g. /delete ").append(subscriptions.get(0).getId());
        return sb.toString();
    }

    private void handleStart(Long chatId) {
        String welcomeMessage = """
                Hi! I'll help you track your subscriptions and remind you before you get charged.

                /add - add a subscription
                  e.g. /add Netflix 1500 monthly 2026-08-15
                /list - see your subscriptions
                /delete - remove a subscription
                """;
        telegramApiClient.sendMessage(chatId, welcomeMessage);
    }

    /**
     * Handles /add &lt;name&gt; &lt;amount&gt; &lt;monthly|yearly&gt; &lt;yyyy-MM-dd&gt;.
     * The name may be several words: the last three tokens are always the amount,
     * billing cycle and renewal date, so everything between the command and those
     * three is joined back together as the name (no quoting needed for "Amazon Prime").
     */
    private void handleAdd(Long chatId, String text) {
        String[] tokens = text.split("\\s+");

        // /add + at least a one-word name + amount + cycle + date = 5 tokens minimum
        if (tokens.length < 5) {
            telegramApiClient.sendMessage(chatId, "That command looks incomplete.\n\n" + ADD_USAGE);
            return;
        }

        int amountIndex = tokens.length - 3;
        int cycleIndex = tokens.length - 2;
        int dateIndex = tokens.length - 1;

        String name = String.join(" ", Arrays.copyOfRange(tokens, 1, amountIndex));

        try {
            CreateSubscriptionRequest request = CreateSubscriptionRequest.builder()
                    .name(name)
                    .amount(parseAmount(tokens[amountIndex]))
                    .billingCycle(parseCycle(tokens[cycleIndex]))
                    .nextRenewalDate(parseDate(tokens[dateIndex]))
                    .build();

            User user = userService.findOrCreateByTelegramChatId(chatId);
            SubscriptionResponse saved = subscriptionService.createSubscription(user, request);

            telegramApiClient.sendMessage(chatId, formatAddConfirmation(saved));

        } catch (CommandFormatException ex) {
            // Malformed argument — tell the user what was wrong and how to fix it,
            // rather than letting the exception bubble up and drop the message silently.
            telegramApiClient.sendMessage(chatId, ex.getMessage() + "\n\n" + ADD_USAGE);
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
            return BillingCycle.valueOf(raw.toUpperCase());
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
        return "✅ Added %s — %s %s / %s, renews %s".formatted(
                sub.getName(),
                sub.getCurrency(),
                sub.getAmount().toPlainString(),
                sub.getBillingCycle().name().toLowerCase(),
                sub.getNextRenewalDate());
    }

    private void handleUnknownCommand(Long chatId) {
        telegramApiClient.sendMessage(chatId,
                "Sorry, I don't recognize that command yet. Try /start to see what I can do.");
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
