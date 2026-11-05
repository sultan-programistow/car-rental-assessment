package dev.piotr.carrental.repository;

import dev.piotr.carrental.domain.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    List<Reservation> findByCarIdOrderByPickupAtAsc(Long carId);

    List<Reservation> findAllByOrderByPickupAtDesc();

    /** Everything still open as of 'now' - running or upcoming rentals. */
    List<Reservation> findByReturnAtAfter(Instant now);

    /**
     * Overlap check for the per-car booking loop, evaluated AFTER the car
     * row lock was acquired (fresh, committed state). Half-open semantics:
     * pickup_at < window.returnAt AND return_at > window.pickupAt.
     */
    boolean existsByCarIdAndPickupAtBeforeAndReturnAtAfter(Long carId,
                                                           Instant windowReturnAt,
                                                           Instant windowPickupAt);

    /**
     * Per-type availability in ONE query. Evaluates overlap straight in SQL
     * (car type reached through the car table: car is the single owner of
     * 'type' - reservation is normalized and carries no type copy).
     */
    @Query(value = """
            SELECT c.type AS type,
                   COUNT(*) AS total,
                   COUNT(*) FILTER (WHERE NOT EXISTS (
                       SELECT 1 FROM reservation r
                       WHERE r.car_id = c.id
                         AND r.pickup_at < :returnAt
                         AND r.return_at > :pickupAt)) AS available
            FROM car c
            GROUP BY c.type
            """, nativeQuery = true)
    List<TypeAvailabilityRow> typeAvailability(@Param("pickupAt") Instant pickupAt,
                                               @Param("returnAt") Instant returnAt);
}
