-- V1: car rental schema.
-- Design notes:
--  * one row per physical car; the car *type* is a plain VARCHAR with a CHECK
--    constraint (mutable-friendly vs a native PG enum, and never ORDINAL-mapped).
--  * reservation stores NO car type: the FK to car is the single source of
--    truth (normalized). Historical type of a reservation is derivable via the
--    car - if ever needed to survive car changes, that belongs in audit
--    tables, not in a denormalized copy here.
--  * TIME MODEL: the database stores PRECISE INSTANTS using timestamptz.
--    The API still speaks wall-clock ("sample date and time, N days" - the
--    assignment's language); the backend immediately interprets that local
--    label in a single configurable booking zone (BOOKING_ZONE,
--    default Europe/Warsaw) and persists the instant. All time presentation
--    ("10:00 in my browser") is a frontend concern. "For N days" is computed
--    as N calendar days in the booking zone (DST-correct civil arithmetic,
--    like a calendar would) and then converted to instants.
--  * intervals are HALF-OPEN [pickup_at, return_at): a car returned at
--    instant X is bookable at X. Double-booking is impossible NOT because of
--    application code but because of the exclusion constraint below - the
--    database itself is the arbiter, safe across N app instances.
CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE car (
    id         BIGSERIAL PRIMARY KEY,
    type       VARCHAR(16) NOT NULL CHECK (type IN ('SEDAN', 'SUV', 'VAN')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE reservation (
    id           BIGSERIAL PRIMARY KEY,
    car_id       BIGINT       NOT NULL REFERENCES car (id) ON DELETE CASCADE,
    pickup_at    TIMESTAMPTZ  NOT NULL,
    return_at    TIMESTAMPTZ  NOT NULL CHECK (return_at > pickup_at),
    borrowed_by  VARCHAR(255) NOT NULL, -- manager-entered email; real user model out of scope
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- THE consistency primitive: no two overlapping rentals for the same car,
-- enforced by PostgreSQL regardless of which app instance wrote the row.
ALTER TABLE reservation
    ADD CONSTRAINT no_double_booking
        EXCLUDE USING gist (car_id WITH =,
                            tstzrange(pickup_at, return_at, '[)') WITH &&);

CREATE INDEX idx_car_type ON car (type);
CREATE INDEX idx_reservation_car_time ON reservation (car_id, pickup_at, return_at);
CREATE INDEX idx_reservation_pickup_ret ON reservation (pickup_at, return_at);
