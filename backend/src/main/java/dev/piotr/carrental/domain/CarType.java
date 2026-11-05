package dev.piotr.carrental.domain;

/**
 * The three car classes the assignment speaks about. Carries no behaviour yet -
 * per the assignment there is no per-type pricing or rental rule, only a
 * limited fleet size per type (which lives in the {@code car} table as the
 * count of existing rows, so admins reshaping the fleet instantly reshapes
 * the limit).
 */
public enum CarType {
    SEDAN,
    SUV,
    VAN
}
