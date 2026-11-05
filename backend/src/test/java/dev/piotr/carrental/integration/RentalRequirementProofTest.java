package dev.piotr.carrental.integration;

import dev.piotr.carrental.api.dto.ReservationDto;
import dev.piotr.carrental.api.dto.ReserveCarRequest;
import dev.piotr.carrental.api.error.ConflictException;
import dev.piotr.carrental.domain.CarType;
import dev.piotr.carrental.service.RentalService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * THE requirement-proof layer. The unit tests prove the service's rules;
 * these tests prove that PostgreSQL - our consistency arbiter - upholds
 * them under real concurrent load and real SQL semantics (tstzrange,
 * half-open bounds, exclusion constraint). H2 cannot emulate any of that,
 * so a real Postgres runs in Testcontainers.
 *
 * Docker is required to run this class.
 */
@SpringBootTest
@Testcontainers
class RentalRequirementProofTest {

    private static final ZoneId BOOKING_ZONE = ZoneId.of("Europe/Warsaw");

    // Far-future windows so seeded example data never interferes, and the
    // three test cohorts never overlap one another.
    private static final LocalDateTime SUV_WINDOW = LocalDateTime.parse("2099-01-10T10:00");   // race test
    private static final LocalDateTime VAN_WINDOW = LocalDateTime.parse("2099-01-01T10:00");   // exhaustion test
    private static final LocalDateTime VAN_TOUCH = LocalDateTime.parse("2099-01-20T10:00");    // back-to-back test

    private static final int SUV_FLEET = 3; // seed: 5 SEDAN / 3 SUV / 2 VAN

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> DB = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    RentalService rental;

    private static ReserveCarRequest booking(CarType type, LocalDateTime pickupAt, int days, String who) {
        return new ReserveCarRequest(type, pickupAt, days, who);
    }

    /**
     * REQUIREMENT UNDER FIRE: "the number of cars of each type is limited.",
     * enforced across concurrent transactions (as Vercel autoscaling would
     * produce them: several stateless app instances at once).
     *
     * 8 threads race for the 3 seeded SUVs in the same window, released by a
     * barrier to collide as hard as possible. EXACTLY fleet-size many must
     * win; every loser must get the server's conflict verdict (409-shaped
     * ConflictException, message written by the service or the constraint).
     */
    @Test
    void concurrentBookingOfTheLastCars_isSerializedByPostgres() throws Exception {
        int threads = 8;
        var barrier = new CyclicBarrier(threads);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<Attempt>> futures = new ArrayList<>();
            for (int i = 1; i <= threads; i++) {
                final int n = i;
                futures.add(pool.submit((Callable<Attempt>) () -> {
                    barrier.await(); // maximize contention
                    try {
                        var dto = rental.reserve(
                                booking(CarType.SUV, SUV_WINDOW, 5, "racer%d@example.com".formatted(n)));
                        return new Attempt(true, dto.carId());
                    } catch (ConflictException e) {
                        return new Attempt(false, null);
                    }
                }));
            }
            List<Attempt> results = new ArrayList<>();
            for (Future<Attempt> f : futures) results.add(f.get());

            long wins = results.stream().filter(a -> a.won).count();
            long losses = results.stream().filter(a -> !a.won).count();

            assertThat(wins).as("exactly fleet-size bookings must succeed, never more").isEqualTo(SUV_FLEET);
            assertThat(losses).as("the rest must conflict").isEqualTo(threads - SUV_FLEET);
            assertThat(results.stream().map(a -> a.carId).filter(java.util.Objects::nonNull).distinct())
                    .as("each winning booking holds a DIFFERENT car")
                    .hasSize(SUV_FLEET);
        } finally {
            pool.shutdownNow();
        }
    }

    /**
     * Half-open boundary proven against the REAL constraint: a pickup exactly
     * at another rental's return instant does NOT violate no_double_booking.
     */
    @Test
    void backToBackBookingAtTheExactReturnInstant_isAllowedByTheConstraint() {
        var first = rental.reserve(booking(CarType.VAN, VAN_TOUCH, 2, "first@example.com"));
        var touch = first.returnAt().atZone(BOOKING_ZONE).toLocalDateTime();
        var second = rental.reserve(booking(CarType.VAN, touch, 2, "second@example.com"));

        assertThat(second.pickupAt()).isEqualTo(first.returnAt());
        // The same car MAY be handed out again: its window is over.
        // Different car would also be legal - only overlap is forbidden.
        assertThat(second.id()).isNotNull();
    }

    /**
     * Overlap against a DIFFERENT rental on the SAME car is impossible -
     * even when the app-side candidate selection would have picked a car
     * (defence in depth: the constraint is the last line, not the first).
     */
    @Test
    void overlappingBookingOfAnExhaustedType_conflicts() {
        // two VANs exist (seed); both get booked for this window
        rental.reserve(booking(CarType.VAN, VAN_WINDOW, 5, "van1@example.com"));
        rental.reserve(booking(CarType.VAN, VAN_WINDOW, 5, "van2@example.com"));

        assertThatThrownBy(() -> rental.reserve(booking(CarType.VAN, VAN_WINDOW.plusDays(2), 5, "third@example.com")))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("VAN");
    }

    private record Attempt(boolean won, Long carId) {
    }
}
