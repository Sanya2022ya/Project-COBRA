package ru.rsreu.cobra.core;

/**
 * Источник времени для замера задержек и симуляции.
 */
public interface Ticker {
    long read();
}