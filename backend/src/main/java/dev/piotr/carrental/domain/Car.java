package dev.piotr.carrental.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * One row per physical car. The fleet size of a type is simply the number of
 * rows of that type - the "limited number of cars" requirement is modeled as
 * data, not as a hardcoded quota.
 */
@Entity
@Table(name = "car")
public class Car {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Never ORDINAL: a re-ordered enum must not silently rewrite history. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private CarType type;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Car() {
    }

    public Car(CarType type) {
        this.type = type;
    }

    public Long getId() {
        return id;
    }

    public CarType getType() {
        return type;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
