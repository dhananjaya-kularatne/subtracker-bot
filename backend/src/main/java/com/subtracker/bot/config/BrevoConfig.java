package com.subtracker.bot.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
@Getter
public class BrevoConfig {

    @Value("${brevo.enabled:false}")
    private boolean enabled;

    @Value("${brevo.api-key:}")
    private String apiKey;

    @Value("${brevo.sender-email:}")
    private String senderEmail;

    @Value("${brevo.sender-name:Subtracker Bot}")
    private String senderName;

    public boolean isConfigured() {
        return enabled && apiKey != null && !apiKey.isBlank()
                && senderEmail != null && !senderEmail.isBlank();
    }
}
