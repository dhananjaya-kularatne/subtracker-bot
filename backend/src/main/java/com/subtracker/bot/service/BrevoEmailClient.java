package com.subtracker.bot.service;

import com.subtracker.bot.config.BrevoConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class BrevoEmailClient {

    private final RestClient restClient;
    private final BrevoConfig brevoConfig;

    // True means Brevo accepted the email request, not that the inbox received it.
    public boolean sendEmail(String recipient, String subject, String text) {
        if (!brevoConfig.isConfigured() || recipient == null || recipient.isBlank()) {
            return false;
        }
        try {
            return restClient.post()
                    .uri("https://api.brevo.com/v3/smtp/email")
                    .header("api-key", brevoConfig.getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new EmailRequest(new Sender(brevoConfig.getSenderEmail(), brevoConfig.getSenderName()),
                            List.of(new Recipient(recipient)), subject, text))
                    .retrieve().toBodilessEntity().getStatusCode().is2xxSuccessful();
        } catch (Exception exception) {
            // Do not include provider bodies or headers that may expose credentials.
            log.error("Brevo email request failed: {}", exception.getClass().getSimpleName());
            return false;
        }
    }

    private record Sender(String email, String name) {}
    private record Recipient(String email) {}
    private record EmailRequest(Sender sender, List<Recipient> to, String subject, String textContent) {}
}
