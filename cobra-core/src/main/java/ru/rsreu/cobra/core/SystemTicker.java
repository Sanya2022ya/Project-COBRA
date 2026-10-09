package ru.rsreu.cobra.core;

public final class SystemTicker implements Ticker {
    public static final SystemTicker INSTANCE = new SystemTicker();

    private SystemTicker() {}

    @Override
    public long read() {
        return System.nanoTime();
    }
}