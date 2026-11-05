package dev.piotr.carrental.api.dto;

import dev.piotr.carrental.domain.CarType;

import java.time.Instant;

/**
 * Timestamps leave the API as ISO-8601 INSTANTS ("2027-01-10T09:00:00Z").
 * The backend owns the moment; the frontend owns the presentation (renders
 * it in the manager's browser-local zone).
 */
public record ReservationDto(
        Long id,
        Long carId,
        CarType carType,
        Instant pickupAt,
        Instant returnAt,
        String borrowedBy) {

    /** carType is reached through the car - reservation is normalized. */
    public static ReservationDto from(dev.piotr.carrental.domain.Reservation r) {
        return new ReservationDto(
                r.getId(),
                r.getCar().getId(),
                r.getCar().getType(),
                r.getPickupAt(),
                r.getReturnAt(),
                r.getBorrowedBy());
    }
}
