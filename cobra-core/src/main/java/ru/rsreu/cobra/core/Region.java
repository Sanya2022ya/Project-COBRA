package ru.rsreu.cobra.core;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Логический регион кэша с LRU-вытеснением по объему байт,
 * O(1) оценкой попаданий в хвост (loss), теневым списком (gain)
 * и расчетом стоимости промахов c_i с затуханием по времени через Ticker.
 */
public class Region<K, V> {
    private static final int DEFAULT_ENTRY_OVERHEAD = 64;
    private static final double LN_2 = Math.log(2.0);

    private final String name;
    private final Weigher<K, V> weigher;
    private final Ticker ticker;
    private final long minQuota;
    private final long maxQuota;
    private final long deltaBytes;
    private final long halfLifeNanos; // Полупериод затухания H

    private long currentQuota;
    private long currentWeight = 0;

    private static class Node<K, V> {
        final K key;
        V value;
        long weight;
        boolean inTailZone = false;
        Node<K, V> prev;
        Node<K, V> next;

        Node(K key, V value, long weight) {
            this.key = key;
            this.value = value;
            this.weight = weight;
        }
    }

    private final Map<K, Node<K, V>> entries = new HashMap<>();
    private Node<K, V> head;
    private Node<K, V> tail;

    private Node<K, V> tailBoundary;
    private long tailZoneWeight = 0;

    private final LinkedHashMap<K, Long> shadowEntries = new LinkedHashMap<>();
    private long shadowWeight = 0;

    private long shadowHits = 0; // S+ (gain)
    private long tailHits = 0;   // S- (loss)
    private long totalHits = 0;
    private long totalMisses = 0;

    // EWMA стоимости промаха c_i с затуханием по времени
    private double averageCostNanos = 1_000_000.0;
    private long lastMissTimeNanos = -1;

    public Region(String name, long initialQuota, long minQuota, long maxQuota,
                  long deltaBytes, long halfLifeNanos, Weigher<K, V> weigher, Ticker ticker) {
        this.name = name;
        this.currentQuota = initialQuota;
        this.minQuota = minQuota;
        this.maxQuota = maxQuota;
        this.deltaBytes = deltaBytes;
        this.halfLifeNanos = halfLifeNanos;
        this.weigher = weigher;
        this.ticker = ticker;
    }

    public synchronized V get(K key, Function<K, V> loader) {
        Node<K, V> node = entries.get(key);

        if (node != null) {
            totalHits++;
            if (node.inTailZone) {
                tailHits++;
            }
            moveToHead(node);
            return node.value;
        }

        // Промах
        totalMisses++;

        if (shadowEntries.containsKey(key)) {
            shadowHits++;
            Long removedW = shadowEntries.remove(key);
            if (removedW != null) {
                shadowWeight -= removedW;
            }
        }

        long start = ticker.read();
        V value = loader.apply(key);
        long now = ticker.read();
        long duration = now - start;

        if (value == null) {
            return null;
        }

        // Обновление стоимости с непрерывным затуханием по времени
        updateCostEstimate(duration, now);

        long weight = weigher.weigh(key, value) + DEFAULT_ENTRY_OVERHEAD;
        Node<K, V> newNode = new Node<>(key, value, weight);
        entries.put(key, newNode);
        addToHead(newNode);
        currentWeight += weight;

        maintainTailBoundary();
        evictIfNeeded();

        return value;
    }

    private void updateCostEstimate(long duration, long now) {
        if (lastMissTimeNanos < 0) {
            averageCostNanos = duration;
        } else {
            long deltaT = Math.max(0, now - lastMissTimeNanos);
            double decayWeight = Math.exp(-((double) deltaT * LN_2) / (double) halfLifeNanos);
            averageCostNanos = decayWeight * averageCostNanos + (1.0 - decayWeight) * duration;
        }
        lastMissTimeNanos = now;
    }

    private void evictIfNeeded() {
        while (currentWeight > currentQuota && tail != null) {
            Node<K, V> victim = tail;
            detachNode(victim);
            entries.remove(victim.key);
            currentWeight -= victim.weight;

            shadowEntries.put(victim.key, victim.weight);
            shadowWeight += victim.weight;

            while (shadowWeight > deltaBytes && !shadowEntries.isEmpty()) {
                K oldestKey = shadowEntries.keySet().iterator().next();
                Long w = shadowEntries.remove(oldestKey);
                if (w != null) {
                    shadowWeight -= w;
                }
            }

            maintainTailBoundary();
        }
    }

    private void maintainTailBoundary() {
        if (tail == null) {
            tailBoundary = null;
            tailZoneWeight = 0;
            return;
        }

        if (tailBoundary == null) {
            tailBoundary = tail;
            tailBoundary.inTailZone = true;
            tailZoneWeight = tail.weight;
        }

        while (tailZoneWeight < deltaBytes && tailBoundary.prev != null) {
            tailBoundary = tailBoundary.prev;
            tailBoundary.inTailZone = true;
            tailZoneWeight += tailBoundary.weight;
        }

        while (tailBoundary != tail && (tailZoneWeight - tailBoundary.weight) >= deltaBytes) {
            tailBoundary.inTailZone = false;
            tailZoneWeight -= tailBoundary.weight;
            tailBoundary = tailBoundary.next;
        }
    }

    private void detachNode(Node<K, V> node) {
        if (node.inTailZone) {
            tailZoneWeight -= node.weight;
            node.inTailZone = false;
        }
        if (node == tailBoundary) {
            tailBoundary = node.next;
        }

        if (node.prev != null) node.prev.next = node.next;
        else head = node.next;

        if (node.next != null) node.next.prev = node.prev;
        else tail = node.prev;

        node.prev = null;
        node.next = null;
    }

    private void addToHead(Node<K, V> node) {
        node.next = head;
        node.prev = null;
        if (head != null) head.prev = node;
        head = node;
        if (tail == null) tail = node;
    }

    private void moveToHead(Node<K, V> node) {
        if (node == head) {
            maintainTailBoundary();
            return;
        }
        detachNode(node);
        addToHead(node);
        maintainTailBoundary();
    }

    public synchronized void resize(long newQuota) {
        this.currentQuota = Math.max(minQuota, Math.min(maxQuota, newQuota));
        evictIfNeeded();
    }

    public synchronized double computeGain(double windowSeconds) {
        if (windowSeconds <= 0) return 0;
        double costMs = averageCostNanos / 1_000_000.0;
        return (shadowHits / windowSeconds) * costMs;
    }

    public synchronized double computeLoss(double windowSeconds) {
        if (windowSeconds <= 0) return 0;
        double costMs = averageCostNanos / 1_000_000.0;
        return (tailHits / windowSeconds) * costMs;
    }

    public synchronized void resetWindow() {
        this.shadowHits = 0;
        this.tailHits = 0;
    }

    public String getName() { return name; }
    public synchronized long getCurrentQuota() { return currentQuota; }
    public synchronized long getCurrentWeight() { return currentWeight; }
    public long getMinQuota() { return minQuota; }
    public long getMaxQuota() { return maxQuota; }
    public long getDeltaBytes() { return deltaBytes; }
    public synchronized long getTotalHits() { return totalHits; }
    public synchronized long getTotalMisses() { return totalMisses; }
    public synchronized double getAverageCostMs() { return averageCostNanos / 1_000_000.0; }
    public synchronized double getHitRatio() {
        long total = totalHits + totalMisses;
        return total == 0 ? 0.0 : (double) totalHits / total;
    }
}