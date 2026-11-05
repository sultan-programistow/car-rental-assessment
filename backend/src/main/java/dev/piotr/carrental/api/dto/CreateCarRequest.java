package dev.piotr.carrental.api.dto;

import dev.piotr.carrental.domain.CarType;
import jakarta.validation.constraints.NotNull;

/** Manager adds a car to the fleet. */
public record CreateCarRequest(@NotNull CarType type) {
}
