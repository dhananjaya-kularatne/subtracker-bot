package com.subtracker.bot.service;

import com.subtracker.bot.model.BillingCycle;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

final class MessageFormatting {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM uuuu", Locale.ENGLISH);

    private MessageFormatting() {}

    static String date(LocalDate date) {
        return DATE.format(date);
    }

    static String money(String currency, BigDecimal amount) {
        DecimalFormat format = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.ENGLISH));
        return currency + " " + format.format(amount);
    }

    static String period(BillingCycle cycle) {
        return cycle == BillingCycle.MONTHLY ? "month" : "year";
    }
}
