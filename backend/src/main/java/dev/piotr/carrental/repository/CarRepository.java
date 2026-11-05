package dev.piotr.carrental.repository;

import dev.piotr.carrental.domain.Car;
import dev.piotr.carrental.domain.CarType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface CarRepository extends JpaRepository<Car, Long> {

    /**
     * Cars of a type that are free for the whole requested (half-open) window.
     *
     * PESSIMISTIC_WRITE locks the candidate car rows: two concurrent
     * reservations (also from other app instances) that target the same car
     * serialize on those rows; the second transaction re-evaluates overlap
     * after the first one commits, so it never double-books. The exclusion
     * constraint in the DB remains as the safety net for anything the lock
     * cannot cover.
     *
     * Deterministic pick: lowest id first (stable, explainable; a
     * least-recently-returned heuristic would be the "smarter" pick).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT c FROM Car c
            WHERE c.type = :type
              AND NOT EXISTS (
                    SELECT r FROM Reservation r
                    WHERE r.car = c
                      AND r.pickupAt < :returnAt
                      AND r.returnAt > :pickupAt)
            ORDER BY c.id
            """)
    List<Car> findAvailableFor(@Param("type") CarType type,
                               @Param("pickupAt") Instant pickupAt,
                               @Param("returnAt") Instant returnAt);

    List<Car> findAllByOrderByTypeAscIdAsc();
}
