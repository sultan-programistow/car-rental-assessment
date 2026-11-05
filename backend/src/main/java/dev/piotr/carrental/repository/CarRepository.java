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
import java.util.Optional;

public interface CarRepository extends JpaRepository<Car, Long> {

    /**
     * Candidate car ids of a type that were free for the whole requested
     * (half-open) window AT SCAN TIME - without locking. Locking happens
     * per car in {@link #lockById}; only then is freshness really decided.
     *
     * Concurrency model (see RentalService.reserve): several app instances
     * may scan the same candidates simultaneously BY DESIGN - contention is
     * resolved on ONE car row at a time, never on the whole candidate set.
     * Locking the full candidate set (the intuitive first fix) makes every
     * loser queue up on candidate[0] and starves the rest of the fleet:
     * proven by the Testcontainers race test.
     */
    @Query("""
            SELECT c.id FROM Car c
            WHERE c.type = :type
              AND NOT EXISTS (
                    SELECT r FROM Reservation r
                    WHERE r.car = c
                      AND r.pickupAt < :returnAt
                      AND r.returnAt > :pickupAt)
            ORDER BY c.id
            """)
    List<Long> findFreeCarIds(@Param("type") CarType type,
                              @Param("pickupAt") Instant pickupAt,
                              @Param("returnAt") Instant returnAt);

    /**
     * Row lock on a single car (HQL cannot express SKIP LOCKED, and a full
     * candidate-set lock starves the fleet - see above). WAITING (not skip)
     * is deliberate: skipping a locked-but-still-maybe-free car produces the
     * constraint-violation path instead of booking the next car.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Car c WHERE c.id = :id")
    Optional<Car> lockById(@Param("id") Long id);

    List<Car> findAllByOrderByTypeAscIdAsc();
}
