# Car Rental Technical Assignment — Design Plan (grilled & sealed, Nov 2026)

**Status:** Design done, implementation NOT started. Switch to a Build agent and follow this spec top-to-bottom. Build order is Tiered (§7) — Tier 1 first.

**Workspace:** `/home/stefan/praca/street` (currently empty, not a git repo — `git init` first)
**Stack decision ground truth:** Full-Stack Java + React role (per recruiter correspondence, included below).

---

## 1. The assignment (verbatim)

> We invite you to complete the following technical exercise. A production ready solution is not
> expected; an effort equivalent to approximately two hours should be sufficient. We encourage
> you to focus on areas where you have the greatest expertise and can demonstrate depth rather
> than breadth.
>
> The use of any tools, including AI-based assistance, is permitted; however, all design and
> implementation decisions will be considered your own. Please clearly note any known
> limitations or tradeoffs in your solution.
>
> Please design and implement a simulated Car Rental system using object-oriented principles.
> Requirements:
> - The system should allow reservation of a car of a given type at a desired date and time
>   for a given number of days.
> - There are 3 types of cars (Sedan, SUV and van).
> - The number of cars of each type is limited.
> - Use unit tests to prove the system satisfies the requirements.
>
> During the interview, you will be asked to show your code and explain your technical decisions
> and reasoning. Please concentrate on non-trivial aspects and avoid spending time on
> boilerplate or purely infrastructural elements. Emphasize the strengths of your solution and
> the areas where you intentionally chose to invest more effort.

**Correspondence (recruiter):** The candidate (Piotr) asked about stack/UI/sample. Answer (Kamil,
recruiter): *"As you are applying for Full-Stack role with Java and React the best will be if you
will use Java and React to prepare this Technical Assessment."*

## 2. Technology decisions (all confirmed by user)

| Area | Decision |
|---|---|
| Language | **Java 25** (latest LTS — verified current, JDK 26 exists but is non-LTS) |
| Backend | Spring Boot 3, Spring Data JPA (Hibernate) |
| Schema mgmt | **Flyway**, hand-written migrations (V1: schema; V2: seed) — user may revisit after seeing it |
| Frontend | Vite + React + **TypeScript**, **axios** (not fetch), plain CSS (one stylesheet), no state library |
| Database | **PostgreSQL**, free tier via **Neon** (attached from Vercel marketplace, 0.5 GB free, no credit card) |
| Local dev | `docker-compose` Postgres + `mvn spring-boot:run` |
| Hosting | **All Vercel (free)**: backend as container via `Dockerfile.vercel` (listens on `$PORT`), frontend via framework detection. Verified: Vercel launched "Run any Dockerfile on Vercel" June 30, 2026 ("Backends are back") — supports Spring Boot explicitly ("even Java"). Autoscales stateless containers on Fluid compute. |
| Repo layout | Monorepo: `backend/` + `frontend/` + `docker-compose.yml` + `README.md` |

## 3. Domain model

- **`Car`** — one row per vehicle *instance*. Columns: `id`, `type VARCHAR CHECK (type IN ('SEDAN','SUV','VAN'))`, `removed_at` NOT used (see delete decision below). Java enum `CarType` mapped `@Enumerated(EnumType.STRING)` (never ORDINAL).
- **`Reservation`** (rental) — `id`, `car_id FK`, **NO type column (user decision: normalized schema — car is the single owner of type; historical type derivable via FK; a real product would use audit tables for that, which this choice leaves open)**, **NO duration column (user decision: derived everywhere as return_at − pickup_at; "N days" input exists only at the API boundary)**, **`pickup_at` + `return_at` as `TIMESTAMPTZ` instants** (user decision Nov 5: DB stores *precise moments*; the API input stays wall-clock `LocalDateTime` — the assignment's language — which the backend interprets in ONE configurable booking zone `BOOKING_ZONE`, default `Europe/Warsaw`, and converts; **"N days" = N calendar days in the booking zone** (DST-correct civil arithmetic, exact span 71h across spring DST) then to instants; **API responses emit ISO-8601 `Z` instants and the frontend owns presentation**), **`borrowed_by VARCHAR` = email the rental-service manager types into the UI** (no user entity, no FK, no auth — placeholder column, one-line justification in README).
- Range semantics: **half-open `[pickup, return)` over instants** — a car returned at instant X is bookable at X. Back-to-back bookings both succeed; 1-minute overlap conflicts.
- Index: `(car_id, pickup, return)`.
- Fleet seed: **5 SEDAN, 3 SUV, 2 VAN** (asymmetric on purpose — scarcity of vans is the visual/exhaustion story) + a few example reservations/history rows so no page is empty on first run (user-confirmed).

## 4. Concurrency design — THE core decision

**No app-level locking, no in-memory state.** Consistency lives in PostgreSQL:

```sql
CREATE EXTENSION IF NOT EXISTS btree_gist;
ALTER TABLE reservation ADD CONSTRAINT no_double_booking
  EXCLUDE USING gist (car_id WITH =, tstzrange(pickup_at, return_at, '[)') WITH &&);
```

- The DB rejects two overlapping rentals on the same car **regardless of which node issued them**.
- Why DB-level: hosting on Vercel Fluid compute = stateless containers, autoscaled to N instances under load — in-memory `synchronized` would be actively wrong two-node.
- Allocation: `POST /reservations` (pickup, durationDays, type, borrowed_by) opens a transaction, selects a free car of the type with no overlapping rentals (**deterministic pick: earliest-free-then-lowest-id, possibly `FOR UPDATE SKIP LOCKED`**), inserts reservation. On exclusion-constraint violation → retry next candidate / fail with **HTTP 409**.
- Availability query per type: count cars of type MINUS time-range overlap count — implemented as hand-written JPQL/SQL (explainable), NOT magic-derived.

**Upskill links (btree_gist / EXCLUDE — user requested):**
1. Range types (`tsrange`, `&&` operator): https://www.postgresql.org/docs/current/rangetypes.html
2. Exclusion constraints on ranges (has a bookings example): https://www.postgresql.org/docs/current/rangetypes.html#rangetypes-exclude
3. `EXCLUDE` syntax in CREATE TABLE: https://www.postgresql.org/docs/current/sql-createtable.html#sql-createtable-parameexclude
4. GiST indexes: https://www.postgresql.org/docs/current/gist.html
5. btree_gist extension: https://www.postgresql.org/docs/current/btree-gist.html
Search phrase: *"postgres exclusion constraint prevent overlapping bookings"*.

## 5. API surface (manager-facing, JSON)

- `GET  /api/car-types/availability?from={pickup}&days={n}` → `[{type, fleetSize, available}]`
- `POST /api/reservations` `{carType, pickup, durationDays, borrowedBy}` → **201** + reservation (incl. assigned `carId`) · **409** conflict · **400** validation (past pickup, non-positive days)
- `GET  /api/reservations` (newest first)
- `DELETE /api/reservations/{id}` → 204 (manager cancels)
- `GET  /api/cars` (fleet status) · `POST /api/cars {type}` · `DELETE /api/cars/{id}` (**user-confirmed: HARD delete + `ON DELETE CASCADE` of its rentals/history** — manager aware; README notes real app would use "archive" instead) · `GET /api/cars/{id}/history`
- Error semantics rationale: **409 = Conflict** (RFC 9110 §15.5.10 — collides with *server state*, exactly double-booking), **400** = invalid payload. 409 body carries human-readable reason.

## 6. Frontend (manager app — user-confirmed framing)

Internal tool for the **rental-company manager**, NOT a public customer booking page. No auth. Pages:
1. **Booking page:** type picker with live availability (from/for-N-days), pickup datetime, duration, borrower email text input → posts; on **409: show inline error + prompt to reload** (arrival: type was taken while typing — never trust the UI, the constraint is the arbiter).
2. **Reservation list:** upcoming/past, cancel button.
3. **Fleet page:** all cars, expandable → status + borrow history; simple Add-car / Remove-car buttons.
4. **Per-car history sub-page** (or expanded view).

axios centralizes 409→toast/error mapping via interceptor.

## 7. Build order with pre-agreed cut-line (the 2h-overpressure plan)

- **Tier 1 (non-negotiable):** domain logic + unit tests, JPA + Flyway V1/V2, exclusion constraint, REST API, React booking page.
- **Tier 2:** Testcontainers race tests, fleet admin UI + per-car history pages.
- **Tier 3 (first out the door under time pressure):** actual Vercel deployment — ship `docker-compose.yml` + README instead; deploy only if time remains.

## 8. Testing (user-confirmed split)

- **Pure unit tests** (JUnit 5 + AssertJ), no DB: interval/overlap math, validation, capacity math, 409-mapping. These "prove the requirements" per the assignment.
- **Testcontainers Postgres** integration tests: concurrent transactions racing for the same car / last car of a type (e.g., 10 threads → exactly N cars' worth of winners), exclusion-constraint behavior, back-to-back boundary case. H2 cannot emulate exclusion constraints — Testcontainers is the only honest tool here. Requires Docker locally (and ideally CI).

## 9. Known limitations & tradeoffs (go into README verbatim-ish)

1. Auth/user model omitted (assignment scope); `borrowed_by` is a free-form email column.
2. Hard car delete cascades rental history away — real product would archive.
3. Denormalized `return` column + `type` on reservations — derived data, kept deliberately, explained.
4. JVM container on Vercel = heavier boot than Go; first-request cold start noted; mitigations: JRE-slim/distroless, Spring Boot layered jar.
5. Database-enforced overlap rather than app locks — single source of truth, horizontally safe.
6. "Production ready not expected" — in-memory alternative was rejected for robustness, not rubric compliance.

## 10. Deployment checklist (Tier 3)

1. `git init` + GitHub, push monorepo.
2. Neon free Postgres via Vercel marketplace → `DATABASE_URL` env var (Neon driver / JDBCSSL).
3. `Dockerfile.vercel` in `backend/`: multi-stage (maven build → JRE 25 slim), `CMD` with Spring layered jar, listens `$PORT`.
4. Vite frontend project on Vercel; `VITE_API_URL` env var pointing at backend.
5. `vercel dev` locally if desired; kitchen-anchor: both backend and frontend platforms free-plan sized.

## 11. Gotcha notes for a future agent

- The workspace was **empty** — the grilling happened over 4 rounds (hosting topology, Java version, DB column choices, interval math, cascade-delete vs soft-delete, axios vs fetch, 409 semantics, seeds 5/3/2).
- User's own words to preserve in README/interview: *"None of the in-memory locking I originally envisioned survives Vercel autoscaling — the exclusion constraint does."*
- Nothing writes disk outside Tier lines until the human confirms next agents to run.
- Sign-off (user): Q23–Q26 = "yes", "yes", "I think yes."
