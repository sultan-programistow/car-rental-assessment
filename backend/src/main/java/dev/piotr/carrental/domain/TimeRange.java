package dev.piotr.carrental.domain;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.util.Objects;

/**
 * A half-open time interval of INSTANTS {@code [pickupAt, returnAt)}.
 *
 * Half-openness is a correctness decision, not style: a car returned at
 * instant X is rentable again at instant X. The overlap predicate therefore
 * is {@code a.pickupAt < b.returnAt && b.pickupAt < a.returnAt} - touching
 * ranges do NOT overlap. The same semantics are enforced independently by
 * the PostgreSQL exclusion constraint (tstzrange ... '[)'), so Java and SQL
 * must never disagree about the boundary.
 *
 * Construction interprets the manager's wall-clock input ("pickup 10:00")
 * in a single configurable booking zone; "for N days" means N CALENDAR days
 * in that zone (like a rental contract on paper: Fri 10:00 -> Mon 10:00,
 * surviving DST shifts) converted to precise instants.
 */
public record TimeRange(Instant pickupAt, Instant returnAt) {

    public TimeRange {
        Objects.requireNonNull(pickupAt, "pickupAt");
        Objects.requireNonNull(returnAt, "returnAt");
        if (!returnAt.isAfter(pickupAt)) {
            throw new IllegalArgumentException("returnAt must be after pickupAt");
        }
    }

    /**
     * N calendar days in 'zone' (ZonedDateTime.plusDays), then to instants.
     * This deliberately does NOT use Duration.ofDays: exact-24h blocks would
     * drift across DST boundaries; the human contract is calendar days.
     */
    public static TimeRange forDaysEx(LocalDateTime localPickupAt, ZoneId zone, int days) {
        if (days < 1) {
            throw new IllegalArgumentException("duration must be at least 1 day");
        }
        ZonedDateTime zoned = localPickupAt.atZone(zone);
        return new TimeRange(zoned.toInstant(), zoned.plusDays(days).toInstant());
    }

    public boolean overlaps(TimeRange other) {
        return pickupAt.isBefore(other.returnAt) && other.pickupAt().isBefore(returnAt);
    }

    /** Length of the rental as an exact span (may be 23h/25h per DST day). */
    public Duration exactDuration() {
        return Duration.between(pickupAt, returnAt);
    }
}
