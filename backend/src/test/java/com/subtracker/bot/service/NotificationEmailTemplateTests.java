package com.subtracker.bot.service;

import com.subtracker.bot.config.TelegramConfig;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NotificationEmailTemplateTests {
    private NotificationEmailTemplate template(String username) throws IOException {
        TelegramConfig config = mock(TelegramConfig.class);
        when(config.getBotUsername()).thenReturn(username);
        return new NotificationEmailTemplate(config);
    }

    @Test
    void escapesSubscriptionTextAndSubjectInsteadOfInterpretingMarkup() throws IOException {
        String html = template("subtracker_dj_bot").render("Plan <script>alert(1)</script>",
                "Plan <img src=x onerror=alert(1)> & friends\nAmount: LKR 100.00\n\n/cancel 25");
        assertFalse(html.contains("<script>"));
        assertFalse(html.contains("<img"));
        assertTrue(html.contains("&lt;script&gt;"));
        assertTrue(html.contains("&lt;img"));
        assertTrue(html.contains("&amp; friends<br>Amount: LKR 100.00"));
        assertTrue(html.contains("/cancel 25"));
    }

    @Test
    void userTextCannotIntroduceTemplateSubstitutions() throws IOException {
        String html = template("subtracker_dj_bot").render("{{BODY}}", "Keep {{TITLE}} literally");
        assertTrue(html.contains("<title>{{BODY}}</title>"));
        assertTrue(html.contains("Keep {{TITLE}} literally"));
    }

    @Test
    void configuredBotLinkIsIncludedInHtmlAndPlainText() throws IOException {
        NotificationEmailTemplate template = template("@another_bot");
        assertTrue(template.render("Reminder", "Text").contains("href=\"https://t.me/another_bot\""));
        assertTrue(template.plainText("Text").contains("https://t.me/another_bot"));
        assertTrue(template.plainText("Text").contains("/settings email off"));
    }

    @Test
    void invalidBotUsernameCannotInjectLinkMarkup() {
        assertThrows(IllegalArgumentException.class, () -> template("bot\" onclick=\"alert(1)"));
    }
}
