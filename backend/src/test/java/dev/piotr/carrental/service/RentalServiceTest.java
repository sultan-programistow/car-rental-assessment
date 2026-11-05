package dev.piotr.carrental.service;

import dev.piotr.carrental.api.dto.ReserveCarRequest;
import dev.piotr.carrental.api.dto.ReservationDto;
import dev.piotr.carrental.api.error.BadRequestException;
import dev.piotr.carrental.api.error.ConflictException;
import dev.piotr.carrental.api.error.NotFoundException;
import dev.piotr.carrental.domain.Car;
import dev.piotr.carrental.domain.CarType;
import dev.piotr.carrental.domain.Reservation;
import dev.piotr.carrental.domain.TimeRange;
import dev.piotr.carrental.repository.CarRepository;
import dev.piotr.carrental.repository.ReservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Service logic without Spring, without Docker. The DB constraint story is
 * covered separately (Testcontainers tests); here we prove the rules the
 * service owns: validation, candidate arbitration, conflict translation,
 * cancel policy. These tests fail at unit level even if persistence were
 * replaced - the rules do not belong to the database.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RentalServiceTest {

    static final ZoneId BOOKING_ZONE = ZoneId.of("Europe/Warsaw");
    static final Instant NOW = Instant.parse("2026-11-05T09:00:00Z");
    static final Clock FIXED_CLOCK = Clock.fixed(NOW, BOOKING_ZONE);

    @Mock
    ReservationRepository reservations;
    @Mock
    CarRepository cars;

    RentalService rental;

    @BeforeEach
    void setUp() {
        rental = new RentalService(reservations, cars, FIXED_CLOCK, BOOKING_ZONE);
    }

    ReserveCarRequest request(LocalDateTime pickupAt, int days) {
        return new ReserveCarRequest(CarType.SEDAN, pickupAt, days, "tom@example.com");
    }

    /*
     * TOMORROW_10 as utc; the frozen clock NOW is 2026-11-05T09:00:00Z which
     * is 10:00 Europe/Warsaw. And "yesterday 09:00 Warsaw" is 08:00Z —
     * exactly one hour inside the past.
     */
    private static final LocalDateTime TOMORROW_10 = LocalDateTime.parse("2026-11-06T10:00");
    private static final LocalDateTime PAST_YESTERDAY_9 = LocalDateTime.parse("2026-11-05T09:00");

    /** Car identity in production is DB-generated - stubbing it is honest. */
    private Car sedanWithId(long id) {
        Car sedan = mock(Car.class);
        when(sedan.getId()).thenReturn(id);
        when(sedan.getType()).thenReturn(CarType.SEDAN);
        return sedan;
    }

    @Test
    void rejects_pickupInThePast() {
        assertThatThrownBy(() -> rental.reserve(request(PAST_YESTERDAY_9, 3)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("past");
    }

    @Test
    void rejects_nonPositiveDuration() {
        assertThatThrownBy(() -> rental.reserve(request(TOMORROW_10, 0)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("durationDays");
    }

    @Test
    void conflict_whenTypeHasNoFreeCar() {
        TimeRange window = TimeRange.forDaysEx(TOMORROW_10, BOOKING_ZONE, 3);
        when(cars.findAvailableFor(CarType.SEDAN, window.pickupAt(), window.returnAt()))
                .thenReturn(List.of());
        assertThatThrownBy(() -> rental.reserve(request(TOMORROW_10, 3)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("SEDAN");
    }

    @Test
    void books_theFreeCar_storingInstantsDerivedFromCivilDays() {
        Car sedan = sedanWithId(3);
        TimeRange window = TimeRange.forDaysEx(TOMORROW_10, BOOKING_ZONE, 3);
        when(cars.findAvailableFor(CarType.SEDAN, window.pickupAt(), window.returnAt()))
                .thenReturn(List.of(sedan));

        ReservationDto result = rental.reserve(request(TOMORROW_10, 3));

        assertThat(result.carId()).isEqualTo(3L);
        assertThat(result.carType()).isEqualTo(CarType.SEDAN); // reached through the car
        assertThat(result.pickupAt()).isEqualTo(TOMORROW_10.atZone(BOOKING_ZONE).toInstant());
        assertThat(result.returnAt())
                .isEqualTo(TOMORROW_10.plusDays(3).atZone(BOOKING_ZONE).toInstant()); // calendar days
        assertThat(result.borrowedBy()).isEqualTo("tom@example.com");
        verify(reservations).saveAndFlush(any(Reservation.class));
    }

    @Test
    void constraintViolation_translatesTo409Conflict() {
        Car sedan = sedanWithId(3);
        when(cars.findAvailableFor(any(), any(), any())).thenReturn(List.of(sedan));
        when(reservations.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("no_double_booking"));

        assertThatThrownBy(() -> rental.reserve(request(TOMORROW_10, 3)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("reload");
    }

    @Test
    void cancel_removesFutureReservation() {
        Reservation future = reservation(NOW.plusSeconds(3600));
        when(reservations.findById(5L)).thenReturn(Optional.of(future));

        rental.cancel(5L);

        verify(reservations).delete(future);
    }

    @Test
    void cancel_refusesToRewriteHistory() {
        Reservation past = reservation(NOW.minusSeconds(3600));
        when(reservations.findById(7L)).thenReturn(Optional.of(past));

        assertThatThrownBy(() -> rental.cancel(7L))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("history");

        verify(reservations, never()).delete(any());
    }

    @Test
    void cancel_unknownReservationIs404() {
        when(reservations.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> rental.cancel(99L)).isInstanceOf(NotFoundException.class);
    }

    private Reservation reservation(Instant pickupAt) {
        return new Reservation(new Car(CarType.SEDAN), pickupAt, pickupAt.plusSeconds(36 * 3600), "x@example.com");
    }
}
