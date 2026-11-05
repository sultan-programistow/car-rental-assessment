package dev.piotr.carrental.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * A rental. Normalized on purpose: the car type lives ONLY on {@link Car}
 * (single owner of that fact) - if a historical reservation's type is ever
 * needed after the car was deleted, that belongs in audit tables, not in a
 * copied column here.
 *
 * Both boundaries are persisted as INSTANTS (timestamptz): the API's
 * wall-clock "N days" input is interpreted in the configured booking zone
 * and converted on the way in. Persisting both boundaries makes range checks
 * plain indexable column comparisons; pickup and return are precise moments.
 */
@Entity
@Table(name = "reservation")
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "car_id", nullable = false)
    private Car car;

    @Column(name = "pickup_at", nullable = false)
    private Instant pickupAt;

    @Column(name = "return_at", nullable = false)
    private Instant returnAt;

    /** Manager-entered email; a real user/tenant model is out of scope. */
    @Column(name = "borrowed_by", nullable = false)
    private String borrowedBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Reservation() {
    }

    public Reservation(Car car, Instant pickupAt, Instant returnAt, String borrowedBy) {
        this.car = car;
        this.pickupAt = pickupAt;
        this.returnAt = returnAt;
        this.borrowedBy = borrowedBy;
    }

    public Long getId() {
        return id;
    }

    public Car getCar() {
        return car;
    }

    public Instant getPickupAt() {
        return pickupAt;
    }

    public Instant getReturnAt() {
        return returnAt;
    }

    public String getBorrowedBy() {
        return borrowedBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
