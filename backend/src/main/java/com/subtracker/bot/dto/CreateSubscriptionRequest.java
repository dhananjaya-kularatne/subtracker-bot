package com.subtracker.bot.dto;

import com.subtracker.bot.model.BillingCycle;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Status always  starts as either TRIAL or ACTIVE, set by the service layer based on the isTrial flag, never sent directly by the client as an enum.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateSubscriptionRequest {

    @NotBlank(message = "Subscription name is required")
    private String name;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "Amount must be greater than 0")
    private BigDecimal amount;

    // Optional — if null, service falls back to the user's defaultCurrency
    private String currency;

    @NotNull(message = "Billing cycle is required")
    private BillingCycle billingCycle;

    @NotNull(message = "Next renewal date is required")
    private LocalDate nextRenewalDate;

    // true -> subscription starts as TRIAL, false -> starts as ACTIVE
    private boolean isTrial;
}