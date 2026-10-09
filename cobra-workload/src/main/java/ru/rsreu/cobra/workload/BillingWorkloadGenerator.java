package ru.rsreu.cobra.workload;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Генератор двухфазной нагрузки «Биллинг»:
 * Фаза 1 («День»): доминируют справочники/тарифы.
 * Фаза 2 («Ночной расчет»): всплеск тяжелых отчетов и начислений.
 */
public class BillingWorkloadGenerator {

    public record Phase(String name, int requestsCount, double tariffsRatio) {}

    public static List<WorkloadRequest> generateBillingTrace(long seed) {
        List<WorkloadRequest> trace = new ArrayList<>();
        Random rnd = new Random(seed);

        // Генераторы ключей по закону Ципфа (alpha = 0.85 — реалистичная скошенность)
        ZipfianGenerator tariffKeys = new ZipfianGenerator(1000, 0.85, seed + 1);
        ZipfianGenerator reportKeys = new ZipfianGenerator(200, 0.85, seed + 2);

        // 4 фазы по 25 000 запросов: День -> Ночной расчет -> День -> Ночной расчет
        List<Phase> phases = List.of(
                new Phase("День 1 (Online)", 25_000, 0.90),
                new Phase("Ночь 1 (Пакетный расчет)", 25_000, 0.30),
                new Phase("День 2 (Online)", 25_000, 0.90),
                new Phase("Ночь 2 (Пакетный расчет)", 25_000, 0.30)
        );

        for (Phase phase : phases) {
            for (int i = 0; i < phase.requestsCount(); i++) {
                if (rnd.nextDouble() < phase.tariffsRatio()) {
                    // Запрос к тарифам: 256 байт, стоимость 1 мс
                    int key = tariffKeys.nextKey();
                    trace.add(new WorkloadRequest("tariffs", key, 256, 1_000_000L));
                } else {
                    // Запрос к отчетам: 4 КБ, стоимость 200 мс
                    int key = reportKeys.nextKey();
                    trace.add(new WorkloadRequest("reports", key, 4096, 200_000_000L));
                }
            }
        }

        return trace;
    }
}