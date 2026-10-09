package ru.rsreu.cobra.workload;

import java.util.Random;

/**
 * Генератор псевдослучайных ключей по распределению Ципфа (Zipfian distribution).
 */
public class ZipfianGenerator {
    private final int items;
    private final double alpha;
    private final double zetan;
    private final double eta;
    private final Random random;

    public ZipfianGenerator(int items, double alpha, long seed) {
        this.items = items;
        this.alpha = alpha;
        this.random = new Random(seed);
        this.zetan = zeta(items, alpha);
        double zeta2 = zeta(2, alpha);
        this.eta = (1.0 - Math.pow(2.0 / items, 1.0 - alpha)) / (1.0 - zeta2 / zetan);
    }

    public ZipfianGenerator(int items, double alpha) {
        this(items, alpha, 42L);
    }

    private static double zeta(int n, double theta) {
        double sum = 0.0;
        for (int i = 1; i <= n; i++) {
            sum += 1.0 / Math.pow(i, theta);
        }
        return sum;
    }

    /**
     * Возвращает ключ от 0 до items - 1.
     */
    public int nextKey() {
        double u = random.nextDouble();
        double uz = u * zetan;
        if (uz < 1.0) {
            return 0;
        }
        if (uz < 1.0 + Math.pow(0.5, alpha)) {
            return 1;
        }
        int v = (int) (items * Math.pow(eta * u - eta + 1.0, 1.0 / (1.0 - alpha)));
        return Math.min(items - 1, Math.max(0, v));
    }
}