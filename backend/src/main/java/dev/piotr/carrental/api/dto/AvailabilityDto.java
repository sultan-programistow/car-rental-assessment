package dev.piotr.carrental.api.dto;

import dev.piotr.carrental.domain.CarType;

import java.time.LocalDateTime;

/** One row of the availability picker: how many of a type can be booked. */
public record AvailabilityDto(
        CarType type,
        long fleetSize,
        long available) {
}
