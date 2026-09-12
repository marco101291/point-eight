# 0.8 (point-eight) — context for Claude Code

Deliberate-practice project in Java + Python. Matchmaking simulation: an institutional orchestrator
(Java/Spring) unilaterally assigns matches, backed by a compatibility engine (Python/FastAPI) that
runs thousands of Monte Carlo simulations over emotional Markov chains. The name comes from the
0.8:1 positive:negative ratio that Gottman/Levenson identified as a breakup risk signal.

Full spec: [`hang-the-dj-sim.md`](hang-the-dj-sim.md).
Architecture decisions: [`docs/architecture.md`](docs/architecture.md) (`DEC-001` … `DEC-024`).
C4 diagrams: [`docs/c4.md`](docs/c4.md).

---

## The first thing to know

**The goal is to learn Java, not to ship fast.** Marco comes from Next.js/TypeScript; Java and
Spring are the new part, Next.js isn't. That changes the criterion for what counts as "better" code
here: the deliberately **more explicit** path is chosen over the shorter one. Concrete example: the
aggregates are pure POJOs with mappers to separate JPA entities, even though it's twice the code of
annotating the aggregate directly (`DEC-004`).

When explaining Java code, name the pattern and the why, not just the what. When a design fork has
pedagogical value, ask instead of deciding silently. Don't assume familiarity with Spring/JPA idioms
(constructor injection, `@Transactional`, records as Value Objects); do assume it with
React/Next.js.

---

## Status

| Milestone | Status | What's there |
|---|---|---|
| **M0** Setup | ✅ | Monorepo, compose (Postgres ×2, RabbitMQ), all 3 services online |
| **M1** Java domain | ✅ | `User`/`Match` aggregates, state machine, CRUD, manual match, domain events. 63 tests |
| **M2** Minimal engine | ✅ | FastAPI exposes `POST /api/v1/compatibility` (random score + expiry, envelope with `modelVersion`); Java consumes it via `RestClient` at `POST /api/matches/{id}/score`. 73 tests |
| **M3** Real simulation | ✅ | `Agent`, 5 Strategy scenarios, discrete Markov states (`EmotionalState`) + `RelationshipState`, `run_batch` (plain loop, not vectorized — `DEC-012`). 30 new Python tests |
| **M4** Async + events | ✅ | Candidate `Specification`/`Strategy` (`DEC-014`), `MatchExpiredEvent` auto-rematches both users (`AFTER_COMMIT` + `REQUIRES_NEW`, `DEC-015`), real `collapse_probability(ratio)`, scoring moved to fire-and-forget RabbitMQ (`DEC-016`). 99 Java tests, 39 Python tests |
| **M5** Admin panel | ✅ | `SimulationRun` persistence (`DEC-017`); Markov graph, spaghetti plot, force-directed compound graph (all `d3-force`, `DEC-018`); live "the System deciding" feed polling `GET /api/events`. 104 Java tests, 52 Python tests |
| **M6** Polish | ✅ | Engine endpoint tests against real Postgres via `testcontainers` (`DEC-019`); `java-system` migrates to Flyway, `ddl-auto: validate` (`DEC-020`); C4 context + container diagrams (`docs/c4.md`). 104 Java tests, 55 Python tests |
| **M7** Mobile client | ✅ | Not in the original spec — added after M4 (`DEC-021` … `DEC-024`). `mobile-client/`: Expo SDK 57 + Expo Router + TypeScript, a login screen and a reveal screen (Layer 1 + photo — no name field, same invariant as everywhere else) wired to `java-system` for real, `expo-secure-store` for the tokens, and a working logout button. `GET /api/matches/me/reveal` is the real reveal endpoint (`RevealActiveMatchUseCase`), identity from the JWT only, `Profile` carrying a real `photoUrl` (`V3__profile_photo.sql`). Auth (`com.pointeight.auth`) issues an access/refresh pair (`DEC-023`): a short-lived JWT access token (15 min) plus a long-lived, opaque, revocable refresh token (`RefreshToken`, `V4`/`V5`) that rotates race-safely and detects reuse. `DEC-024` wires push notifications on `MatchStatus → ACTIVE`: `Match.activate()` now records `MatchActivatedEvent`, `MatchActivatedEventListener` reacts `AFTER_COMMIT` to notify both users via `ExpoPushNotificationSender` (a new `push` package, `RestClient` to Expo's push service), and `Account` carries one push token (`V6__account_push_token.sql`) registered through `POST /api/auth/push-token`. **Real on-device delivery confirmed** on a physical Android phone via an EAS development build (`mobile-client/eas.json`) — activating a match on the server produced an actual notification on the device, FCM V1 credentials and all. 157 Java tests |

Inventory: 132 Java files (main), 31 test, 23 Python, 26 TS (18 admin-panel, 8 mobile-client).

**The Python Engine runs a real simulation**, but the wire contract still says
`modelVersion: "v0"` — bumping it to `"v1"` needs a matching one-line change in
`AmqpCompatibilityEngineClient` (`DEC-013`), still an open follow-up. Every AMQP-triggered score
request now persists a `SimulationRun` (`DEC-017`, SQLAlchemy over psycopg3): the run itself, a
50-simulation sample of full day-by-day trajectories, and observed state-transition counts across
the whole batch — the data M5's spaghetti plot and "learned" Markov graph read from.
**The admin panel is no longer just a traffic light**: `/markov-graph`, `/spaghetti`, and
`/compound` each read real data from both services, and `/live` polls `GET /api/events` (a new
`com.pointeight.events` package on the Java side, `DEC-018`) for `MatchAssignedEvent`/
`MatchExpiredEvent` as they happen — scoring's own async request/response cycle doesn't publish a
domain event yet, so it doesn't show up there (see Open questions in `docs/architecture.md`).

**Scoring is asynchronous now**: `POST /api/matches/{id}/score` returns `202 Accepted` and publishes
the request over RabbitMQ; the score lands moments later via a separate response queue
(`DEC-016`), not in that response. **Matches reassign themselves**: when one expires,
`MatchExpiredEvent` triggers a search for the next candidate for both users
(`AssignNextMatchUseCase`, `DEC-014`/`DEC-015`) — nobody has to call `POST /api/matches` by hand
anymore for that case, only for the very first match between two users.

---

## Environment — read before suggesting commands

- **No JDK or Gradle installed.** All Java build and test runs in a container. Use
  `./scripts/java-test.sh`; **don't** suggest `gradle` or `./gradlew` directly. The script passes
  `--user $(id -u):$(id -g)` because, without that, the Gradle container leaves `build/` and
  `.gradle/` owned by root and breaks later deletions.
- **Published ports come from `.env`**, and several aren't at the "obvious" value because they
  clashed with other projects running on the same machine (`alivia-postgres` occupies 5434, a
  `next-server` occupies 3000). They ended up as: System **8080**, Engine **8000**, panel **3006**,
  System Postgres **5436**, Engine Postgres **5435**, RabbitMQ **5672** / **15672**.
- **The admin panel's local dev server** (`npm run dev` / `npm start` inside `admin-panel/`,
  outside Docker) defaults to port **3007**, not 3006 — 3000 and 3001 were both already taken by
  an unrelated `next-server` on this machine. It reads `SYSTEM_BASE_URL`/`ENGINE_BASE_URL` the same
  way the Docker container does, but falls back to `http://localhost:8080`/`:8000` when they're
  unset, which already matches the ports those two services publish to the host — so no env vars
  need setting to run it locally against the rest of the stack in Docker.
- **`python-engine`'s test suite needs Docker running** (M6, `DEC-019`): the DB-backed endpoint
  tests in `tests/api/test_insights.py` spin up a real, ephemeral Postgres via `testcontainers`,
  reusing one container for the whole session (`tests/conftest.py`) — no `docker compose` service
  needed, but the Docker daemon itself has to be reachable from wherever `pytest` runs.
- **The spec doc and the code deliberately differ** in naming: the doc uses provisional names
  (`hang-the-dj-sim`, `com.system`) and defers the rename to M1; `point-eight` / `com.pointeight`
  was adopted from M0 on (`DEC-001`).
- `.env` isn't version-controlled. Copy it from `.env.example` before starting the stack.

```bash
docker compose up -d --build     # bring up all 6 services
docker compose down -v           # tear down and drop the databases
./scripts/java-test.sh           # 63 tests, no Spring or Postgres
```

---

## Architecture rules

### The dependency rule

Package-by-feature at the top level (`user/`, `match/`, `shared/`, `config/`), Ports & Adapters
inside each one:

```
infrastructure/  ──>  application/  ──>  domain/       never the other way around
```

`domain/` is **pure Java**: not a single line of Spring or JPA. It's verifiable, and if this ever
returns something, the architecture is broken:

```bash
grep -rl "jakarta.persistence\|org.springframework" \
  java-system/src/main --include=*.java | grep "/domain/"
```

Ports (repository interfaces) live in `domain/`, **not** in `infrastructure/`: the domain declares
what it needs and the infrastructure adapts to it.

### The non-negotiable invariant: Layer 2

`User` has two layers. **Layer 1** (`Profile`) is visible and editable: age, gender, city,
profession, hobbies. **Layer 2** (`SimulationParameters`) is write-only: attachment style, weights
toward Gottman's four horsemen, infidelity history, active addiction, baseline stress. **It comes in
through the API and never goes out in any response.**

The guarantee is structural, not a list of exclusions: `UserResponse` is a record that only declares
Layer 1 fields, so the hidden parameters have nowhere to enter. When adding endpoints or DTOs,
**preserve that property** — never serialize `SimulationParameters`. `User.toString()` also omits it
on purpose, so it doesn't leak through the logs.

It's the narrative center of the project: the user can't see or correct the model the System has of
them.

### Other invariants in force

- A match can't match someone with themselves, nor have `expiryDuration` ≤ 0.
- The whole transition table lives in `MatchStatus`; the aggregate consults it before mutating.
  `EXPIRED` and `REJECTED` are terminal.
- A user has at most **one open match** (`PENDING` or `ACTIVE`) — `DEC-007`.
- The domain never calls `Instant.now()`: it receives an injected `Clock`, so expirations can be
  tested with a fixed clock.

---

## Code style

**Java** — Google Java Style, 2-space indent, 100-column limit. `record` for Value Objects, with
validation in the compact constructor. Constructor injection (never field `@Autowired`). Domain
exceptions extend `DomainException`; `GlobalExceptionHandler` translates them to `ProblemDetail`
(404 / 409 / 400).

**Python** — PEP 8, type hints required, `black` (line-length 100), `mypy --strict`, Pydantic as the
input/output contract.

**Commits** — Conventional commits scoped per service: `feat(java-system): add match state
machine`. One PR per milestone or sub-task; **never mix `java-system` and `python-engine`** in the
same PR.

Code comments and documentation **in English**. Code identifiers, in English.

---

## Traps that already cost time

- **`next.config.ts` must not declare an `env` block.** That block *inlines* the values at build
  time, so it bakes the development URLs into the image and the panel shows the services as down
  even when they're responding. Service URLs are read from the environment at runtime, inside the
  server component.
- **Flyway owns the schema now** (`java-system/src/main/resources/db/migration`, `DEC-020`,
  superseding `DEC-008`'s `ddl-auto: update`). `ddl-auto: validate` only checks the entities agree
  with it. When changing a JPA entity, add a new `V{n}__description.sql` migration — Hibernate
  won't create or alter anything by itself anymore.
- **`seekingGenders` is a `Set<Gender>`**, even though the doc writes it in the singular (`DEC-005`).
- **`mobile-client` needs `legacy-peer-deps=true`** (already set in its own `.npmrc`). `expo-router`
  declares optional web-only peers (`@radix-ui/react-tabs`, `vaul`, `react-dom`) that conflict under
  npm's strict resolver even for a mobile-only app that never touches the web target — plain
  `npm install` fails with `ERESOLVE` without it.
- **`mobile-client`'s `localhost` default for `java-system` only resolves from the iOS simulator and
  `expo start --web`.** The Android emulator's own loopback is `10.0.2.2`, not `localhost`; a
  physical device needs the dev machine's LAN IP. Override with `EXPO_PUBLIC_API_URL` (copy
  `mobile-client/.env.example` to `.env`) rather than editing `lib/api.ts`'s fallback.
- **`java-system` had no CORS configuration until `mobile-client`'s login screen needed one.** The
  admin panel was never affected — it fetches `java-system` from its Next.js server component, not
  the browser — so this went unnoticed until `expo start --web` tried a real cross-origin `fetch()`
  and got a bare "Invalid CORS request" 403 on the preflight, which surfaces client-side as a
  generic network failure, not a readable 4xx. `SecurityConfig`'s `corsConfigurationSource()` bean
  fixes it (origin patterns `http://localhost:*`/`http://127.0.0.1:*`, since Expo's dev port isn't
  fixed) — if a *new* symptom like this shows up again, check CORS before assuming the backend is
  down.
- **Expo Go cannot receive remote push notifications at all, since SDK 53.** Local (device-
  scheduled) notifications still work there; a server-triggered one — the whole point of `DEC-024`
  — silently does nothing to test in Expo Go, no error either. Testing push for real needs a
  development build (`mobile-client/eas.json`'s `development` profile, `eas build --profile
  development`) installed instead of Expo Go on the device — and reinstalled clean (uninstall
  first), not just updated in place, after any change to `google-services.json` or native config;
  an in-place "update" was observed keeping stale Firebase state and failing with a Firebase
  initialization error that had nothing to do with the actual (already-fixed) cause.
- **`eas credentials`'s Android push menu has two separate FCM slots** — "FCM Legacy" and "FCM V1:
  Google Service Account Key" — and uploading the Firebase service account JSON to the wrong one
  (Legacy) succeeds silently, no warning that Google shut that API down in 2024. Symptom: Expo
  returns `200` with `{"status":"error","details":{"error":"InvalidCredentials"}}` — a per-message
  error inside a successful HTTP response, not an upload-time failure. Fix is re-uploading the same
  JSON into the **FCM V1** slot specifically.
- **Merely importing `expo-notifications` (not calling anything on it) crashes the app on Android
  in Expo Go**, since SDK 53 — a harder failure than the "remote push silently does nothing" trap
  above. `mobile-client/app/index.tsx` imports `lib/pushNotifications.ts` unconditionally, so a
  static `import * as Notifications from "expo-notifications"` at the top of that file took the
  whole app down before any try/catch ever ran. Fixed by moving the `Constants.appOwnership ===
  "expo"` check *before* the import and turning the import itself into a dynamic `await
  import("expo-notifications")`, gated behind that check.
- **A JPA `@Column(updatable = false)` silently drops that column from every `UPDATE`, no error or
  warning.** Left over from when `Match.expiryDuration` was `final`; making the domain field
  mutable (`DEC-028`) without removing the matching flag on `MatchJpaEntity` meant the whole
  chain — domain method, use case, even the value right after `matches.save()` — worked perfectly,
  and the database still never changed. None of java-system's (mock-only, no real Postgres) tests
  could have caught it; only comparing against the actual running stack did.

---

## Pending decisions

Recorded at the end of `docs/architecture.md`. Most relevant for the upcoming milestones: whether
the Engine keeps a replica of profiles or receives them in the payload (M4), who triggers
`activate` on a `PENDING` match, and how `cumulativeConfidenceScore` gets adjusted (it exists today
but never moves). The Java → Python payload format was resolved in M2 as `DEC-009`; `SimulationRun`
persistence was resolved in M5 as `DEC-017`; M5's three visualizations and live feed as `DEC-018`
— the scoring cycle joining that feed is still open. M6 resolved the engine's DB-backed test
strategy as `DEC-019` and the move off `ddl-auto` as `DEC-020`. M7 resolved the mobile client's
stack and scope as `DEC-021`, and minimal authentication (`Account` separate from `User`, JWT) as
`DEC-022`. The reveal endpoint joins `authenticated()` (`GET /api/matches/me/reveal`,
`RevealActiveMatchUseCase`) and the mobile client is wired to it for real. `DEC-023` replaced the
single long-lived JWT with a short-lived access token backed by a revocable, rotating refresh
token, specifically so the mobile client's logout button can actually revoke something server-side
— rotation is race-safe (atomic DB claim) and detects a stale token being replayed (revokes the
whole rotation family), both verified against real Postgres; one non-security nuance remains on the
purely-concurrent case, see Open questions in `docs/architecture.md`. `DEC-024` resolved the last
M7 groundwork item: `Match.activate()` now records `MatchActivatedEvent`, triggering a push
notification via a new `push` package (Expo's push service, `RestClient`) — needed a development
build (Expo Go dropped remote push support in SDK 53) and the project owner's own Firebase project
for FCM V1 credentials, both now set up; confirmed delivering real notifications to a physical
device. M7 is closed. Still open: both sub-questions `DEC-021` raised (Layer 2 sourcing, date-end
detection) — forward-looking product questions, not blocking anything built so far. Post-M7 mobile
polish continues on `feat/mobile-match-screen-redesign`: `DEC-025` split the reveal screen into a
countdown-only route and a separate `/match-profile` detail route with a real back navigation,
moved the visual language away from dating-app conventions toward the login screen's own
restraint, and fixed a centering bug and a near-invisible back control along the way. Three
questions raised during that work are open, see `docs/architecture.md`: whether to add a name
field (paused, not decided), how the countdown display should scale now that `DEC-028` makes
match duration genuinely variable (up to 7 real days, not a flat 12h) rather than a fixed number
that would just need one bump, and (since `DEC-027`) how the countdown should read while a user
genuinely has no match yet — that state is far more common now that matching is real. `DEC-026`
resolves both sub-questions `DEC-021` left open — a nine-question,
multiple-choice-only sign-up questionnaire sources the Layer 2 baseline (`attachmentStyle`,
`attachmentIntensity`, `communicationProfile`; the rest is either derived already or deliberately
left unasked), and post-match recalibration fires once per match at its terminal transition
(`EXPIRED` or `REJECTED`), not per date. The questionnaire and sign-up screen are built
(`mobile-client/app/signup.tsx`); the recalibration trigger is still only a design decision, and
`Match.reject()` still records no domain event, needed before it can fire on that side. `DEC-027`
closes the gap both DEC-025 and DEC-026 surfaced — matching never actually happening on its own:
`MatchExpiryScheduler` (`@Scheduled`, finally built — `Match.isDue()`'s javadoc had claimed this
since M1) auto-expires due matches, and a new `UserRegisteredEvent` (`User` gained the same
`pendingEvents` machinery `Match` already had) triggers `AssignNextMatchUseCase` the moment
someone registers, no polling needed for that half. Turned out to be two separate mechanisms, not
one job, once actually designed. `DEC-028` derives a match's real duration from the Engine's own
`expiryDays` prediction (`ExpiryDurationPolicy`, linearly scaled between a 12h floor and a 7-day
ceiling) instead of the flat default — chosen over a simpler constant bump for being the more
narratively honest reading of "the System decides based on data." Needed `Match.expiryDuration` to
stop being `final` (guarded to `PENDING` only) and, as a real gap this surfaced rather than
something it set out to fix, a new `MatchAssignedEventListener` so scoring itself finally runs
automatically on every match instead of only via the manual `/score` endpoint. Cost the most time
in this block: a `@Column(updatable = false)` left over from the immutable-field days silently
dropped the new duration from every `UPDATE`, invisible to all 174 (mock-only) tests — see Traps.
