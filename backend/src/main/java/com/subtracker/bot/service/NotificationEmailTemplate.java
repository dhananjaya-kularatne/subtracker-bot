package com.subtracker.bot.service;

import com.subtracker.bot.config.TelegramConfig;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
public class NotificationEmailTemplate {
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{(TITLE|BODY|BOT_URL)\\}\\}");
    private final String template;
    private final String botUrl;

    public NotificationEmailTemplate(TelegramConfig config) throws IOException {
        String username = config.getBotUsername().replaceFirst("^@", "");
        if (!username.matches("[A-Za-z0-9_]+")) {
            throw new IllegalArgumentException("Telegram bot username must contain only letters, numbers, and underscores.");
        }
        botUrl = "https://t.me/" + username;
        template = new ClassPathResource("templates/notification-email.html").getContentAsString(StandardCharsets.UTF_8);
    }

    public String render(String subject, String text) {
        String body = Arrays.stream(text.replace("\r\n", "\n").split("\n\\s*\n"))
                .map(block -> "<p style=\"margin:0 0 14px;font-size:15px;line-height:1.7;\">"
                        + HtmlUtils.htmlEscape(block, "UTF-8").replace("\n", "<br>") + "</p>")
                .collect(Collectors.joining());
        // Replace only placeholders from the trusted template, never placeholders in user text.
        return PLACEHOLDER.matcher(template).replaceAll(match -> Matcher.quoteReplacement(switch (match.group(1)) {
            case "TITLE" -> HtmlUtils.htmlEscape(subject, "UTF-8");
            case "BODY" -> body;
            default -> botUrl;
        }));
    }

    public String plainText(String text) {
        return text + "\n\nOpen Subtracker in Telegram: " + botUrl
                + "\nTo remove email backup, send /settings email off in Telegram.";
    }
}
