package com.expensetracker.service;

/** Week 3 — Euro implementation (demo rate: 1 EUR = 91.50 INR). */
public class EurConverter implements CurrencyConverter {

    private static final double EUR_TO_INR = 91.50;

    @Override
    public String code() {
        return "EUR";
    }

    @Override
    public String symbol() {
        return "€";
    }

    @Override
    public double toInr(double amount) {
        return amount * EUR_TO_INR;
    }

    @Override
    public double fromInr(double inrAmount) {
        return inrAmount / EUR_TO_INR;
    }
}
