package ru.rsreu.cobra.core;

import java.util.List;

public interface AllocationStrategy {
    void rebalance(List<Region<?, ?>> regions, long totalBudget, double windowSeconds);
}