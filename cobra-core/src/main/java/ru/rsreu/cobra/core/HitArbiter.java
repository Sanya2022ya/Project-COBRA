package ru.rsreu.cobra.core;

import java.util.List;

/**
 * Арбитр по числу попаданий (аналог моделей Cliffhanger / Memshare):
 * перераспределяет память исключительно по частоте попаданий, полагая c_i = 1.0.
 * Максимизирует интегральный Hit Ratio, игнорируя реальную стоимость промахов в хранилище.
 */
public class HitArbiter implements AllocationStrategy {
    private final double epsilon;

    public HitArbiter(double epsilon) {
        this.epsilon = epsilon;
    }

    public HitArbiter() {
        this(0.1);
    }

    @Override
    public void rebalance(List<Region<?, ?>> regions, long totalBudget, double windowSeconds) {
        if (regions.size() < 2) return;

        Region<?, ?> bestGainRegion = null;
        double maxGainHits = -1.0;

        Region<?, ?> minLossRegion = null;
        double minLossHits = Double.MAX_VALUE;

        for (Region<?, ?> region : regions) {
            // Вычисляем выигрыш/потери чисто в попаданиях (нормируя c_i обратно к 1.0)
            double costMs = Math.max(0.001, region.getAverageCostMs());
            double gainHits = region.computeGain(windowSeconds) / costMs;
            double lossHits = region.computeLoss(windowSeconds) / costMs;

            // Кандидат на получение памяти
            if (region.getCurrentQuota() < region.getMaxQuota()) {
                if (gainHits > maxGainHits) {
                    maxGainHits = gainHits;
                    bestGainRegion = region;
                }
            }

            // Кандидат на отдачу памяти
            if (region.getCurrentQuota() > region.getMinQuota()) {
                if (lossHits < minLossHits) {
                    minLossHits = lossHits;
                    minLossRegion = region;
                }
            }
        }

        if (bestGainRegion != null && minLossRegion != null && bestGainRegion != minLossRegion) {
            if (maxGainHits > minLossHits * (1.0 + epsilon)) {
                long step = Math.min(bestGainRegion.getDeltaBytes(), minLossRegion.getDeltaBytes());
                step = Math.min(step, bestGainRegion.getMaxQuota() - bestGainRegion.getCurrentQuota());
                step = Math.min(step, minLossRegion.getCurrentQuota() - minLossRegion.getMinQuota());

                if (step > 0) {
                    minLossRegion.resize(minLossRegion.getCurrentQuota() - step);
                    bestGainRegion.resize(bestGainRegion.getCurrentQuota() + step);
                }
            }
        }

        for (Region<?, ?> region : regions) {
            region.resetWindow();
        }
    }
}