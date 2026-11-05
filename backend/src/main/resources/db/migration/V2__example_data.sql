-- V2: demo fleet (5 Sedan / 3 SUV / 2 Van - asymmetric on purpose: scarcity
-- of vans is the point of the "limited number of cars" requirement) plus a
-- handful of example rentals so fleet/history pages are not empty on first run.
-- (duration is no longer stored: return_at encodes it.)

INSERT INTO car (type)
VALUES ('SEDAN'), ('SEDAN'), ('SEDAN'), ('SEDAN'), ('SEDAN'),
       ('SUV'), ('SUV'), ('SUV'),
       ('VAN'), ('VAN');

-- History: a Sedan rented and returned two weeks ago
INSERT INTO reservation (car_id, pickup_at, return_at, borrowed_by)
SELECT id, now() - interval '14 days', now() - interval '10 days', 'anna.k@example.com'
FROM car WHERE type = 'SEDAN' ORDER BY id LIMIT 1;

-- History: a Van that was returned exactly today (exercises the half-open boundary)
INSERT INTO reservation (car_id, pickup_at, return_at, borrowed_by)
SELECT id, now() - interval '2 days', now(), 'marek.s@example.com'
FROM car WHERE type = 'VAN' ORDER BY id LIMIT 1;

-- Upcoming: an SUV booked from tomorrow for 3 days (shows up as BOOKED in fleet view)
INSERT INTO reservation (car_id, pickup_at, return_at, borrowed_by)
SELECT id, now() + interval '1 day', now() + interval '4 days', 'tom@example.com'
FROM car WHERE type = 'SUV' ORDER BY id LIMIT 1;
