package ru.rsreu.cobra.core;

import java.util.List;

/**
 * Арбитр КОБРА: адаптивный градиентный перенос квоты памяти
 * с защитой от истощения (SLA Guard) и динамическим шагом.
 */
public class CobraArbiter implements AllocationStrategy {
    private final double epsilon;          // Порог гистерезиса (например, 0.1 = 10%)
    private final double slaMinHitRatio;   // Минимальный допустимый порог Hit Ratio для защиты

    public CobraArbiter(double epsilon, double slaMinHitRatio) {
        this.epsilon = epsilon;
        this.slaMinHitRatio = slaMinHitRatio;
    }

    public CobraArbiter(double epsilon) {
        this(epsilon, 0.30); // Защитный барьер Hit Ratio = 30%
    }

    public CobraArbiter() {
        this(0.1, 0.30);
    }

    @Override
    public void rebalance(List<Region<?, ?>> regions, long totalBudget, double windowSeconds) {
        if (regions.size() < 2) return;

        Region<?, ?> bestGainRegion = null;
        double maxGain = -1.0;

        Region<?, ?> minLossRegion = null;
        double minLoss = Double.MAX_VALUE;

        for (Region<?, ?> region : regions) {
            double gain = region.computeGain(windowSeconds);
            double loss = region.computeLoss(windowSeconds);

            // 1. Кандидат на сужение: память забирается ТОЛЬКО если SLA не нарушен
            double hr = region.getHitRatio();
            boolean violatesSla = (hr < slaMinHitRatio) && (region.getTotalHits() + region.getTotalMisses() > 1000);

            if (!violatesSla && region.getCurrentQuota() > region.getMinQuota()) {
                if (loss < minLoss) {
                    minLoss = loss;
                    minLossRegion = region;
                }
            }

            // 2. Кандидат на расширение
            if (region.getCurrentQuota() < region.getMaxQuota()) {
                if (gain > maxGain) {
                    maxGain = gain;
                    bestGainRegion = region;
                }
            }
        }

        // 3. Градиентный перенос квоты
        if (bestGainRegion != null && minLossRegion != null && bestGainRegion != minLossRegion) {
            if (maxGain > minLoss * (1.0 + epsilon)) {
                long maxStep = Math.min(bestGainRegion.getDeltaBytes(), minLossRegion.getDeltaBytes());

                // Адаптивная модуляция шага
                double margin = (maxGain - minLoss) / maxGain;
                long step = (margin > 0.4) ? maxStep : Math.max(1024, maxStep / 2);

                step = Math.min(step, bestGainRegion.getMaxQuota() - bestGainRegion.getCurrentQuota());
                step = Math.min(step, minLossRegion.getCurrentQuota() - minLossRegion.getMinQuota());

                if (step > 0) {
                    minLossRegion.resize(minLossRegion.getCurrentQuota() - step);
                    bestGainRegion.resize(bestGainRegion.getCurrentQuota() + step);
                }
            }
        }

        // Сброс оконной статистики
        for (Region<?, ?> region : regions) {
            region.resetWindow();
        }
    }
}