package com.expensetracker.service;

/** Week 3 — US Dollar implementation (demo rate: 1 USD = 83.20 INR). */
public class UsdConverter implements CurrencyConverter {

    private static final double USD_TO_INR = 83.20;

    @Override
    public String code() {
        return "USD";
    }

    @Override
    public String symbol() {
        return "$";
    }

    @Override
    public double toInr(double amount) {
        return amount * USD_TO_INR;
    }

    @Override
    public double fromInr(double inrAmount) {
        return inrAmount / USD_TO_INR;
    }
}
