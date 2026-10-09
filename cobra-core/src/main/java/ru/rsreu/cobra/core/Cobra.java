package ru.rsreu.cobra.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Cobra {
    private final long totalBudget;
    private final AllocationStrategy strategy;
    private final List<Region<?, ?>> regions = new ArrayList<>();

    public Cobra(long totalBudget, AllocationStrategy strategy) {
        this.totalBudget = totalBudget;
        this.strategy = strategy;
    }

    public synchronized void registerRegion(Region<?, ?> region) {
        regions.add(region);
    }

    public synchronized void rebalance(double windowSeconds) {
        strategy.rebalance(regions, totalBudget, windowSeconds);
    }

    public List<Region<?, ?>> getRegions() {
        return Collections.unmodifiableList(regions);
    }
}