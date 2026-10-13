package com.expensetracker.service;

/** Week 3 — INR implementation: the identity converter (pivot currency). */
public class InrConverter implements CurrencyConverter {

    @Override
    public String code() {
        return "INR";
    }

    @Override
    public String symbol() {
        return "₹";
    }

    @Override
    public double toInr(double amount) {
        return amount;
    }

    @Override
    public double fromInr(double inrAmount) {
        return inrAmount;
    }
}
