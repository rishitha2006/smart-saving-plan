package com.salarywise.service;

import org.springframework.stereotype.Component;
import java.util.Locale;

@Component("moneyFormatter")
public class MoneyFormatter {
    public String format(double value) {
        return String.format(Locale.US, "%,.2f", value);
    }
    public String formatWhole(double value) {
        return String.format(Locale.US, "%,.0f", value);
    }
}
