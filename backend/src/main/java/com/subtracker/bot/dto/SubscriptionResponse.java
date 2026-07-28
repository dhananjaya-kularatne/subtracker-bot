package com.subtracker.bot.dto;

import com.subtracker.bot.model.BillingCycle;
import com.subtracker.bot.model.SubscriptionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubscriptionResponse {

    private Long id;
    private String name;
    private BigDecimal amount;
    private String currency;
    private BillingCycle billingCycle;
    private SubscriptionStatus status;
    private LocalDate nextRenewalDate;
    private LocalDateTime createdAt;
}