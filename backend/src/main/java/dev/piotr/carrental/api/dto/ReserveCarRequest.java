package dev.piotr.carrental.api.dto;

import dev.piotr.carrental.domain.CarType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * The API speaks the assignment's language: reserve a car of a given type at
 * a desired date/time for a given number of days. pickupAt arrives WITHOUT a
 * zone on purpose - it is the manager's wall-clock label, which the backend
 * interprets in the configured booking zone and stores as an instant
 * (timestamptz). durationDays is NOT persisted directly - the service derives
 * returnAt as N CALENDAR days later in the booking zone, as instants.
 */
public record ReserveCarRequest(
        @NotNull CarType carType,
        @NotNull LocalDateTime pickupAt,
        @NotNull Integer durationDays,
        @NotBlank @Email @Size(max = 255) String borrowedBy) {
}
