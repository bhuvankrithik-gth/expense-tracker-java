package com.expensetracker.service;

/** Week 3 — Japanese Yen implementation (demo rate: 1 JPY = 0.55 INR). */
public class JpyConverter implements CurrencyConverter {

    private static final double JPY_TO_INR = 0.55;

    @Override
    public String code() {
        return "JPY";
    }

    @Override
    public String symbol() {
        return "¥";
    }

    @Override
    public double toInr(double amount) {
        return amount * JPY_TO_INR;
    }

    @Override
    public double fromInr(double inrAmount) {
        return inrAmount / JPY_TO_INR;
    }
}
