package com.expensetracker.util;

/**
 * Week 8 — GENERIC class (the "Box&lt;T&gt;" idea from the syllabus).
 * Carries either a successful value or a failure message, so service
 * methods can report problems without throwing for expected cases
 * (e.g. "email already registered").
 *
 * @param <T> the type of the success value
 */
public final class Result<T> {

    private final boolean ok;
    private final T value;      // autoboxed/unboxed naturally at call sites
    private final String error;

    private Result(boolean ok, T value, String error) {
        this.ok = ok;
        this.value = value;
        this.error = error;
    }

    /** Successful result wrapping a value. */
    public static <T> Result<T> ok(T value) {
        return new Result<>(true, value, null);
    }

    /** Failed result carrying a user-friendly message. */
    public static <T> Result<T> fail(String error) {
        return new Result<>(false, null, error);
    }

    public boolean isOk() { return ok; }
    public T getValue() { return value; }
    public String getError() { return error; }
}
