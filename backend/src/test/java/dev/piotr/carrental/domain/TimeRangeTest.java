package dev.piotr.carrental.domain;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The interval math in isolation - THE piece of logic whose semantics both
 * Java and SQL must agree on (the SQL side is the identical tstzrange '[)'
 * form, proven in integration tests).
 */
class TimeRangeTest {

    static final ZoneId BOOKING_ZONE = ZoneId.of("Europe/Warsaw");
    final LocalDateTime T0 = LocalDateTime.of(2026, 11, 10, 9, 0);

    @Test
    void durationInDaysYieldsTheHalfOpenReturnInstant() {
        var range = TimeRange.forDaysEx(T0, BOOKING_ZONE, 3);
        assertThat(range.pickupAt()).isEqualTo(T0.atZone(BOOKING_ZONE).toInstant());
        assertThat(range.returnAt()).isEqualTo(T0.plusDays(3).atZone(BOOKING_ZONE).toInstant());
    }

    @Test
    void strictlyOverlappingRangesDoOverlap() {
        assertThat(TimeRange.forDaysEx(T0, BOOKING_ZONE, 3)
                .overlaps(TimeRange.forDaysEx(T0.plusDays(1), BOOKING_ZONE, 2))).isTrue();
        assertThat(TimeRange.forDaysEx(T0, BOOKING_ZONE, 3)
                .overlaps(TimeRange.forDaysEx(T0, BOOKING_ZONE, 3))).isTrue(); // identical
        assertThat(TimeRange.forDaysEx(T0, BOOKING_ZONE, 4)
                .overlaps(TimeRange.forDaysEx(T0.plusDays(1), BOOKING_ZONE, 1))).isTrue(); // contained
    }

    @Test
    void backToBackRangesDoNOT_overlap_halfOpenSemantics() {
        // [T0, T0+10) then [T0+10, T0+13): car returned at T0+10 is free at T0+10
        assertThat(TimeRange.forDaysEx(T0, BOOKING_ZONE, 10)
                .overlaps(TimeRange.forDaysEx(T0.plusDays(10), BOOKING_ZONE, 3))).isFalse();
        assertThat(TimeRange.forDaysEx(T0.plusDays(10), BOOKING_ZONE, 3)
                .overlaps(TimeRange.forDaysEx(T0, BOOKING_ZONE, 10))).isFalse();
    }

    @Test
    void disjunctRangesDoNotOverlap() {
        assertThat(TimeRange.forDaysEx(T0, BOOKING_ZONE, 2)
                .overlaps(TimeRange.forDaysEx(T0.plusDays(5), BOOKING_ZONE, 2))).isFalse();
        assertThat(TimeRange.forDaysEx(T0.plusDays(5), BOOKING_ZONE, 2)
                .overlaps(TimeRange.forDaysEx(T0, BOOKING_ZONE, 2))).isFalse();
    }

    /**
     * The point of storing instants with civil-day arithmetic: "3 days" is
     * 3 CALENDAR days (like the paper contract), not blindly 72h. Spring DST
     * shift (2027-03-28, Europe/Warsaw +02:00 after the jump) SHORTENS the
     * exact span to 71h while the return stays "10:00 local". Converse would
     * happen at the autumn changeover (25h days) - same logic, no case test.
     */
    @Test
    void rentalSpanningTheSpringDSTShiftIsCalendarDaysNot72hours() {
        var range = TimeRange.forDaysEx(LocalDateTime.parse("2027-03-27T10:00"), BOOKING_ZONE, 3);
        // Fri 2027-03-27 10:00 CET(+01) -> Mon 2027-03-30 10:00 CEST(+02)
        assertThat(range.exactDuration()).isEqualTo(Duration.ofHours(71));
        assertThat(Duration.between(range.pickupAt(), range.returnAt()))
                .isEqualTo(Duration.ofHours(71));
        assertThat(range.pickupAt())
                .isEqualTo(Instant.parse("2027-03-27T09:00:00Z")); // CET 10:00
        assertThat(range.returnAt())
                .isEqualTo(Instant.parse("2027-03-30T08:00:00Z")); // CEST 10:00
    }

    @Test
    void nonPositiveDurationsAreRejected() {
        assertThatThrownBy(() -> TimeRange.forDaysEx(T0, BOOKING_ZONE, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TimeRange.forDaysEx(T0, BOOKING_ZONE, -3))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void invertedRangesAreRejected() {
        assertThatThrownBy(() -> new TimeRange(Instant.EPOCH, Instant.EPOCH))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
