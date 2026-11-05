package dev.piotr.carrental.repository;

/**
 * Row projection for the native availability query - Spring Data maps
 * columns by label to the getters of this interface.
 */
public interface TypeAvailabilityRow {
    String getType();

    long getTotal();

    long getAvailable();
}
