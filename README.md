# Car Rental — Technical Assessment

A simulated car-rental system (Java 25 / Spring Boot backend, React manager UI, PostgreSQL) where
**the database itself enforces the no-double-booking rule** — the app cannot double-book a car from
any number of concurrent instances, because the arbiter is a Postgres exclusion constraint, not
application code.

The full decision trail (including what *didn't* work and why) lives in
[`docs/design-plan.md`](docs/design-plan.md) — it doubles as interview material.

---

## Quickstart

Prerequisites: **Docker**, **JDK 25 + Maven** (user-local script: `source env.sh` at repo root),
**Node 22+**.

```bash
# 1. PostgreSQL 17 (local dev)
docker compose up -d

# 2. Backend — Flyway applies the schema + seed at boot (port 8080)
source env.sh && cd backend && mvn spring-boot:run

# 3. Frontend — Vite dev server proxies /api to :8080 (no CORS in dev; port 5173)
cd ../frontend && npm install && npm run dev
```

Open **http://localhost:5173** — the rental-company manager console:
book a car (type, pickup date/time, N days, borrower email), see live availability per type,
list/cancel reservations, manage the fleet (add/remove car, per-car history).

## Tests

```bash
cd backend && mvn test
```

- **20 pure/fast tests**: interval math (DST-inclusive), service rules with a frozen `Clock`
  (Mockito), HTTP status-code contract (MockMvc). No Docker needed.
- **3 integration tests** (`RentalRequirementProofTest`, Testcontainers — Docker required): boot a
  real PostgreSQL 17, apply the real migrations, and fire **8 concurrent threads at the last 3
  SUVs**; exactly 3 may win, 5 must get the server's conflict verdict. Also proves the half-open
  boundary against the real constraint and overlap conflicts.

## The requirements, and how they're proven

| Requirement | How it's modeled | Proof |
|---|---|---|
| Reserve a car of a given type at a desired date/time for N days | `POST /api/reservations {carType, pickupAt, durationDays, borrowedBy}`; the manager's wall-clock input is interpreted in one configurable booking zone and stored as precise instants | unit + integration |
| 3 types (Sedan, SUV, Van) | `car.type VARCHAR` + CHECK; Java enum (`@Enumerated(STRING)`, never ORDINAL) | unit + API |
| Number per type is limited | fleet = rows in `car` per type; availability = `COUNT(*) FILTER (WHERE NOT EXISTS overlap)` in one native query | the 8-thread race test: exactly `fleetSize` winners |
| Unit tests prove it | 20 fast unit tests + 3 Testcontainers proofs | `mvn test` |

## Architecture in one paragraph + one SQL line

```sql
ALTER TABLE reservation ADD CONSTRAINT no_double_booking
  EXCLUDE USING gist (car_id WITH =, tstzrange(pickup_at, return_at, '[)') WITH &&);
```

One `car` row per physical vehicle. A reservation stores the **half-open** window
`[pickup_at, return_at)` as `timestamptz` instants (return derived as **N calendar days in the
booking zone** — DST-correct). Booking = 3 acts: lock-free candidate scan → **lock ONE car row**
(waiting) → **re-check overlap on the freshly locked state** → insert; a lost race (constraint
violation) moves to the next candidate, an exhausted fleet yields **HTTP 409**. The constraint is
the last line of defence: correctness does not depend on the app at all, which is what makes
multi-instance deployment safe.

Frontend: React 19 + Vite + TypeScript; single axios client normalizing every error into
`ApiError{status, message}` carrying the *server's* message (the 409 wording is authored by the
races' arbiter, not the UI). Instants arrive as ISO-8601 `Z`; presentation (browser-local time)
is the frontend's only time concern.

## API surface

| Method & path | Purpose | Success | Errors |
|---|---|---|---|
| `GET /api/car-types/availability?from&days` | per-type availability for a window | 200 | 400 |
| `POST /api/reservations` | book a car | **201** + reservation (assigned `carId`) | 400 validation · **409 conflict** |
| `GET /api/reservations` | all rentals, newest first | 200 | — |
| `DELETE /api/reservations/{id}` | cancel a future rental | 204 | 404 · 409 (past = history) |
| `GET /api/cars` | fleet with status (AVAILABLE/RENTED/BOOKED) | 200 | — |
| `POST /api/cars` | add a car | 201 | 400/404 |
| `DELETE /api/cars/{id}` | hard delete **cascades its history** (documented, deliberate) | 204 | 404 |
| `GET /api/cars/{id}/history` | per-car borrow history | 200 | 404 |

Error bodies are uniform: `{"message": "..."}`. 400 = invalid on its own; **409 = collides with
server state** (RFC 9110 §15.5.10 — double-booking, canceling history); 404 = unknown id.

## Deployment (all free: Vercel + Neon)

- `backend/Dockerfile.vercel` — multi-stage Maven build → JRE 25 runtime, listens on `$PORT`
  (Vercel Fluid compute; stateless containers on N instances — which is precisely why the
  consistency is DB-enforced).
- Database: **Neon** free Postgres (attach from the Vercel marketplace). The backend consumes
  either `DATABASE_URL` (Neon-style `postgresql://…`, parsed at startup) or explicit
  `DATABASE_URL_JDBC` + `DATABASE_USER` + `DATABASE_PASSWORD`.
- Frontend: separate Vite project on Vercel; set `VITE_API_URL` to the backend's origin and
  `CARRENTAL_ALLOWED_ORIGINS` on the backend to the frontend's origin (CORS is opt-in — absent in
  same-origin deployment). `frontend/vercel.json` rewrites every unknown path to `/index.html`
  (SPA fallback) so deep links like `/reservations` survive a page reload — without it, Vercel's
  static host 404s any route that isn't a real file, since `BrowserRouter` URLs are app-internal.

## Known limitations & tradeoffs (the honest list)

1. **No auth, no multi-tenancy.** The assignment is a simulation; the manager's console is openly
   writable. `borrowed_by` is a plain email column — a real product would have users/roles and
   audit tables (the normalized schema leaves that door open).
2. **Hard car delete cascades history away** (FK `ON DELETE CASCADE`). A real product would
   archive instead of destroying history. Chosen for a mechanical fleet-management demo.
3. **Single booking zone** (`BOOKING_ZONE`, default `Europe/Warsaw`): wall-clock input from one
   region interpreted into instants. Multi-region fleets would need per-showroom zones.
4. **"N days" = N calendar days in the booking zone** (exact spans can be 23h/25h at DST
   edges) — human contract semantics, verified by a unit test (71h across spring DST).
5. **Flyway migrations were edited in place during development** — the git history records every
   schema evolution; from here on, additive migrations only (never editing applied ones).
6. **No CI pipeline.** `mvn test` is the gate; the integration suite needs Docker, which limits
   hosted CI choices (GitHub Actions × Docker-in-Docker would work).
7. **Deterministic allocation picks the lowest-id free car** (stable, explainable). A
   least-recently-returned heuristic would spread utilization better.
8. **JVM cold start** on Vercel's Fluid compute is higher than Node/Go images; acceptable for a
   demo, worth noting for real autoscaling traffic.
9. **Seed data uses `now()`-relative dates** — the demo dataset never ages on disk.
