package ru.rsreu.cobra.core;

/**
 * Виртуальные часы для детерминированных тестов и симулятора.
 */
public final class ManualTicker implements Ticker {
    private long nanos;

    public ManualTicker(long initialNanos) {
        this.nanos = initialNanos;
    }

    public ManualTicker() {
        this(0);
    }

    @Override
    public long read() {
        return nanos;
    }

    public void advance(long deltaNanos) {
        this.nanos += deltaNanos;
    }
}