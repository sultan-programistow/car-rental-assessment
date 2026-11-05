package dev.piotr.carrental.service;

import dev.piotr.carrental.api.dto.AvailabilityDto;
import dev.piotr.carrental.api.dto.ReservationDto;
import dev.piotr.carrental.api.dto.ReserveCarRequest;
import dev.piotr.carrental.api.error.BadRequestException;
import dev.piotr.carrental.api.error.ConflictException;
import dev.piotr.carrental.api.error.NotFoundException;
import dev.piotr.carrental.domain.Car;
import dev.piotr.carrental.domain.CarType;
import dev.piotr.carrental.domain.Reservation;
import dev.piotr.carrental.domain.TimeRange;
import dev.piotr.carrental.repository.CarRepository;
import dev.piotr.carrental.repository.ReservationRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * The heart of the assignment's requirements.
 *
 * Time model: the manager enters wall-clock ("2027-01-10 10:00"); the
 * backend immediately interprets it in ONE configurable booking zone and
 * persists precise instants (timestamptz). Presentation of those instants is
 * the frontend's concern.
 *
 * Consistency strategy: application code takes NO locks of its own beyond
 * selecting candidate cars FOR UPDATE inside the booking transaction; the
 * ultimate double-booking arbiter is the PostgreSQL exclusion constraint
 * (see V1__schema.sql). This keeps the service horizontally scalable - each
 * container instance is stateless by nature and every reservation decision is
 * still serialized by the database.
 */
@Service
public class RentalService {

    private final ReservationRepository reservations;
    private final CarRepository cars;
    private final Clock clock;
    private final ZoneId bookingZone;

    public RentalService(ReservationRepository reservations, CarRepository cars, Clock clock, ZoneId bookingZone) {
        this.reservations = reservations;
        this.cars = cars;
        this.clock = clock;
        this.bookingZone = bookingZone;
    }

    /** Availability per type for the requested window (read-only). */
    public List<AvailabilityDto> availability(LocalDateTime from, Integer days) {
        if (days == null || days < 1) {
            throw new BadRequestException("days must be a positive number");
        }
        TimeRange window = validWindow(from == null ? LocalDateTime.now(clock) : from, days, false);
        return reservations.typeAvailability(window.pickupAt(), window.returnAt()).stream()
                .map(row -> new AvailabilityDto(
                        CarType.valueOf(row.getType()),
                        row.getTotal(),
                        row.getAvailable()))
                .toList();
    }

    /**
     * Books a car of a given type. Assigns the lowest-id free car inside a
     * FOR UPDATE transaction; the DB constraint is the film of safety net.
     * saveAndFlush so a constraint violation surfaces HERE (at flush) and can
     * be translated to 409 instead of dying at commit outside our hands.
     */
    @Transactional
    public ReservationDto reserve(ReserveCarRequest request) {
        TimeRange window = validWindow(request.pickupAt(), request.durationDays(), true);
        List<Car> candidates = cars.findAvailableFor(request.carType(), window.pickupAt(), window.returnAt());
        if (candidates.isEmpty()) {
            throw new ConflictException(
                    "No %s available for the requested time window - the type's fleet is exhausted"
                            .formatted(request.carType()));
        }
        Car car = candidates.get(0);
        Reservation reservation = new Reservation(
                car, window.pickupAt(), window.returnAt(), request.borrowedBy());
        try {
            reservations.saveAndFlush(reservation);
        } catch (DataIntegrityViolationException e) {
            // Lost a race the row lock could not serialize (e.g. bookable car
            // picked by another node in the same instant). The constraint won.
            throw new ConflictException(
                    "The car was booked by someone else before you - reload and retry");
        }
        return ReservationDto.from(reservation);
    }

    /** Cancel a future reservation. History is immutable on purpose. */
    @Transactional
    public void cancel(Long id) {
        Reservation reservation = reservations.findById(id)
                .orElseThrow(() -> new NotFoundException("Reservation %d does not exist".formatted(id)));
        if (reservation.getPickupAt().isBefore(clock.instant())) {
            throw new ConflictException("Rental already started or completed - history is immutable");
        }
        reservations.delete(reservation);
    }

    public List<ReservationDto> listReservations() {
        return reservations.findAllByOrderByPickupAtDesc().stream()
                .map(ReservationDto::from)
                .toList();
    }

    /**
     * Wall-clock input -> instants. Rejects past pickups for bookings
     * (appointments in the past cannot be made); availability being a
     * read-only query stays forgiving (history counts out of the window).
     */
    private TimeRange validWindow(LocalDateTime pickupAt, Integer days, boolean rejectPast) {
        if (days == null || days < 1) {
            throw new BadRequestException("durationDays must be a positive number");
        }
        Instant instantCandidate = pickupAt.atZone(bookingZone).toInstant();
        if (rejectPast && instantCandidate.isBefore(clock.instant())) {
            throw new BadRequestException("pickupAt cannot be in the past");
        }
        try {
            return TimeRange.forDaysEx(pickupAt, bookingZone, days);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage());
        }
    }
}
