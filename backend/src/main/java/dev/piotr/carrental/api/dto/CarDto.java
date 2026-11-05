package dev.piotr.carrental.api.dto;

import dev.piotr.carrental.domain.CarType;

import java.time.Instant;

/**
 * Fleet view row. nextRental is null when the car is free from now on;
 * when set, it is the earliest still-open (running or future) rental, which
 * is what the manager UI shows as the car's current status. Timestamps are
 * instants (ISO-8601 with zone) - presentation belongs to the frontend.
 */
public record CarDto(
        Long id,
        CarType type,
        Instant createdAt,
        Instant nextPickupAt,
        Instant nextReturnAt,
        String nextBorrowedBy) {
}
