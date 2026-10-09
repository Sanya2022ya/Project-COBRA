package ru.rsreu.cobra.core;

@FunctionalInterface
public interface Weigher<K, V> {
    int weigh(K key, V value);
}