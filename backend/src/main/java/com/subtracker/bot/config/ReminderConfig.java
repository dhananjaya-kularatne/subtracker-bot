package com.subtracker.bot.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
public class ReminderConfig {

    @Bean
    public Clock reminderClock() {
        return Clock.system(ZoneId.of("Asia/Colombo"));
    }
}
