package ru.rsreu.cobra.workload;

public record WorkloadRequest(
        String regionName,
        int key,
        int sizeBytes,
        long costNanos
) {}