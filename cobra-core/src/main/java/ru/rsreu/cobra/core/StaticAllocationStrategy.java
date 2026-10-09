package ru.rsreu.cobra.core;

import java.util.List;

/**
 * Статическое распределение (фиксированные квоты, не меняющиеся во времени).
 */
public class StaticAllocationStrategy implements AllocationStrategy {
    @Override
    public void rebalance(List<Region<?, ?>> regions, long totalBudget, double windowSeconds) {
        // Доли зафиксированы при создании регионов
    }
}