# Architecture — 0.8

Living document. The product specification is in [`../hang-the-dj-sim.md`](../hang-the-dj-sim.md);
this document records only implementation decisions as they're made.

## Context (C4 level 1–2)

```
        ┌──────────────┐
        │ admin-panel  │  Next.js 15 — presentation only
        └──────┬───────┘
               │ REST
        ┌──────▼───────────────┐        REST (M2) / AMQP (M4)      ┌──────────────────┐
        │  java-system         │◄────────────────────────────────► │  python-engine   │
        │  "The System"        │                                   │  "The Engine"    │
        │  Hexagonal + DDD     │                                   │  api→svc→domain  │
        └──────┬───────────────┘                                   └────────┬─────────┘
               │                          ┌──────────┐                      │
        ┌──────▼─────────┐                │ RabbitMQ │             ┌────────▼─────────┐
        │ postgres-system│                └──────────┘             │ postgres-engine  │
        └────────────────┘                                          └─────────────────┘
```

Database per service: neither service reads the other's database. Consistency is achieved through
events, not joins.

## Decisions made

### DEC-001 — Final naming from M0 on
The spec doc proposed provisional names (`hang-the-dj-sim`, `com.system`) to migrate in M1.
`point-eight` / `com.pointeight` was adopted directly to avoid an unnecessary package refactor.

### DEC-002 — Java build inside Docker
The Gradle wrapper isn't version-controlled; `java-system`'s `Dockerfile` uses the
`gradle:8.10-jdk21` image in the build stage. This allows working without a local JDK or Gradle.
If a fast local build is wanted later on, add the wrapper with `gradle wrapper`.

### DEC-003 — Internal structure of the Java packages
Package-by-feature at the top level (`match/`, `user/`, `simulation/`, `config/`) and
Ports & Adapters inside each feature (`domain/`, `application/`, `infrastructure/`).
The packages exist from M0 on with `package-info.java`; they get populated in M1.

### DEC-004 — Pure domain, separate from JPA
The `User` and `Match` aggregates are POJOs without a single JPA or Spring annotation. Persistence
lives in `infrastructure/` as `<X>JpaEntity` + `<X>JpaMapper` + `<X>RepositoryAdapter`, which
implements the port declared in `domain/`.

It costs twice the code of annotating the aggregate directly. That's paid for because: (a) it's the
Ports & Adapters exercise the doc calls for, and (b) it lets the whole domain be tested with plain
JUnit — M1's 63 tests run in seconds without spinning up Spring or Postgres.

### DEC-005 — The state machine as an enum, not a class hierarchy
The doc asks for the State pattern for the match lifecycle. It's implemented as a transition table
inside `MatchStatus`: each constant declares which states it can move to. The whole machine reads at
a glance and no transition can be added without touching that table. A `PendingState`/`ActiveState`/
... class hierarchy would be more ceremony for the same behavior, given that the states have no data
of their own.

### DEC-006 — `seekingGenders` is a set, not a single value
The doc writes it in the singular (`seekingGender`). It's modeled as `Set<Gender>` because M4's
Layer 1 filter has to be able to express bisexuality. It's a strict superset of the doc.

### DEC-007 — One open match per user
`CreateManualMatchUseCase` rejects (409) building a match if either party already has one in
`PENDING` or `ACTIVE`. In the compound, each person is in a single relationship at a time; without
this rule, M4 could assign the same user several simultaneous matches.

### DEC-008 — `ddl-auto: update` until M6
Hibernate generates and evolves the schema. The model still moves a lot (M2 adds the score, M3 adds
`SimulationRun`), so hand-writing migrations now would be pure friction. The accepted cost: the
schema isn't version-controlled and renamed columns are left orphaned. It migrates to Flyway in M6.

### DEC-009 — Java → Python payload: envelope with `modelVersion`
The alternative was flat JSON (`{ agentA, agentB }` with no metadata). An envelope
(`{ modelVersion: "v0", agentA, agentB }`) was chosen because M3 is going to replace the random
score with a real `run_batch`, and without a version field there would be no way to know which
algorithm generated an already-persisted score. The Engine rejects any `modelVersion` it doesn't
recognize with a 422, so the field isn't merely decorative from day one.

`AgentSnapshot` (`simulation/domain`) packages a user's `Profile` + `SimulationParameters`; it's the
only place in the System where Layer 2 goes outward, deliberately kept separate from `UserResponse`
so that record never risks inheriting that outbound path by accident. It follows the same pattern as
DEC-004: infrastructure DTOs (`ProfileDto`, `SimulationParametersDto`, etc.) instead of serializing
the domain records directly.

### DEC-010 — The score is requested on a separate endpoint, not when the match is created
`POST /api/matches/{id}/score` and its `RequestCompatibilityScoreUseCase` are independent of
`CreateManualMatchUseCase`. The alternative — requesting the score within the same registration
transaction — is simpler today, but in M4 the real trigger will be `MatchAssignedEvent`, not match
creation; separating them now avoids touching `CreateManualMatchUseCase` when that milestone
arrives.

The HTTP client is `RestClient` (Spring 6.1+), not `WebClient`: it's Spring's current synchronous
idiom and doesn't drag in the reactive module, which the rest of the project doesn't use. The
adapter (`EngineCompatibilityClient`) translates `RestClientException` into
`CompatibilityEngineException` — a pure Java type, no Spring — so the port in `domain/` doesn't
force anyone to know about the concrete HTTP client; `GlobalExceptionHandler` maps it to 502.

`CompatibilityAssessment.suggestedExpiry` (the duration the Engine suggests) still isn't applied to
`Match.expiryDuration` — that field is `final` and today the System sets it when the match is
created, not the Engine. Reconciling the two values is left open for when M3 produces a real score.

### DEC-011 — A discrete Markov layer on top of the continuous state, and `Scenario.resolve()` as the Strategy contract
Section 4's pseudocode only carries `RelationshipState(trust, resentment, satisfaction)` and
checks `resentment >= COLLAPSE_THRESHOLD` to end a simulation. M3 adds `EmotionalState` (`STABLE`,
`TENSE`, `REPAIRING`, `HOSTILE`, `COLLAPSED`) with a hand-authored transition table
(`app/domain/state.py`), and ends a simulation when `emotional_state is COLLAPSED` — the chain's
absorbing state — instead of the separate scalar threshold. The alternative (keep only the
continuous state, add the discrete layer in M5 when the admin panel actually needs to draw it)
was rejected: M5's "Markov graph" view needs real states and transition probabilities to plot, and
retrofitting them after the engine already has callers would mean redoing `update_state`'s
contract later instead of getting it right once.

The Strategy contract follows section 8's `Scenario.resolve(state, agent_a, agent_b)` convention
(for the future `gottman-scenario-writer` skill) rather than section 4's looser
`agent.react(scenario, state)` + free `update_state(...)`. Each `Scenario.resolve()` still calls
`agent.react()` and `update_state()` internally — the difference is that the caller
(`run_simulation`) only ever sees "give me state + two agents, get back the new state", not the
two-step pseudocode. `resolve()` also takes an explicit `rng: random.Random`, which neither section
mentions — needed so `run_batch` can reuse one seeded generator across simulations instead of the
domain silently reaching for global randomness.

### DEC-012 — `run_batch` stays a plain Python loop; `default_simulations` drops from 10,000 to 1,000
Section 2 asks for NumPy to "vectorize the batch". A real vectorized version would represent all N
simulations as arrays (trust/resentment/satisfaction per simulation, a boolean mask for who's
already collapsed) and step every "day" for all of them at once — correct, but a substantially
bigger rewrite of `Agent`/`Scenario` to operate on arrays instead of one state at a time. For M3,
correctness and testability mattered more than throughput, so `run_batch` is the pseudocode's
literal `[run_simulation(...) for _ in range(n)]`.

The cost is real: 10,000 sequential simulations measured ~8.5s locally, which is too slow for a
synchronous request from Java (still true until M4's RabbitMQ decouples them). `default_simulations`
drops to 1,000 (~0.85s) so the existing `POST /api/matches/{id}/score` flow doesn't risk a
client-side timeout. NumPy vectorization is left as a concrete, well-scoped follow-up rather than
done speculatively now.

### DEC-013 — `modelVersion` stays "v0" for now, even though the model is real
M3 replaces the M2 random stub with the actual engine, which is exactly the scenario DEC-009's
envelope was built for — bumping `modelVersion` to `"v1"` would make that traceable. It isn't
bumped in this change: Java's engine client still sends `"v0"`, and this branch is scoped to
`python-engine` only (never mix `java-system` and `python-engine` in one PR/branch). The bump is a
deliberate, separate one-line java-system follow-up. (M4 note: the client that sends `"v0"` is now
`AmqpCompatibilityEngineClient`, not `EngineCompatibilityClient` — see DEC-016 — but the version
itself still hasn't moved, and see DEC-016's own note on where version validation does and doesn't
apply now.)

### DEC-014 — Specification for Layer 1 eligibility, Strategy for picking among them
The doc names two separate patterns for candidate selection (section 2): Specification for Layer 1
filters, Strategy for the pick itself. `UserSpecification` (`user/domain`, composable via
`and`/`or`/`negate`) answers "does this candidate count at all" — `NotSelf` and
`ReciprocalGenderInterest` (`match/domain`), composed by `CandidateEligibility.of(seeker)`.
`CandidateSelectionStrategy` (`match/domain`) answers "which one, out of an already-eligible pool"
— `RandomEligibleCandidateStrategy` is the only implementation M4 needs, uniform at random.

Splitting these was deliberate: availability (does the candidate already have an open match?) is a
`MatchRepository` concern, not a Layer 1 rule, so it can't live inside a pure `UserSpecification`.
`AssignNextMatchUseCase` composes eligibility + availability into the pool it hands the strategy,
which never has to know why a candidate was excluded — only that it wasn't in the list.

`RandomEligibleCandidateStrategy` lives in `match/infrastructure`, not `match/domain`, even though
its logic is pure — same reasoning as `EngineCompatibilityClient` before it (DEC-009): the
interface is the port, and whichever implementation Spring is meant to wire in as a bean is the
adapter, regardless of whether that implementation does I/O. Its `RandomGenerator` argument is a
method parameter, not a constructor-injected field, mirroring `Match.propose(..., Clock clock)`:
the type stays a plain POJO that doesn't need Spring to exist, and tests can pass a fixed generator
for a reproducible pick.

### DEC-015 — `MatchExpiredEventListener` runs `AFTER_COMMIT`, and why `AssignNextMatchUseCase` needs `REQUIRES_NEW`
If assigning the next match fails (no eligible candidate, say), the expiry that already happened
shouldn't undo itself — they're independent facts. `@TransactionalEventListener(phase =
AFTER_COMMIT)` gets that half right: it only fires once `expire()`'s transaction has committed.

The half that isn't obvious: `AssignNextMatchUseCase.execute()` must be
`@Transactional(propagation = REQUIRES_NEW)`, not the default. `expire()`'s `EntityManager` is only
unbound from the thread in `afterCompletion()`, which runs *after* every `afterCommit()` callback —
this listener included. With the default propagation, the new use case would silently join that
already-committed, soon-to-be-discarded resource instead of opening its own, and since it isn't
that transaction's owner, nothing it does would ever actually commit. This surfaced as a real bug
during manual testing against containers: matches got logged as `MatchAssignedEvent` but were never
in the database. `REQUIRES_NEW` forces a genuinely new connection/transaction, sidestepping the
stale-resource window entirely.

### DEC-016 — Fire-and-forget over RabbitMQ replaces the synchronous REST call for scoring
The M2/M3 flow (`EngineCompatibilityClient` over `RestClient`) blocked Java for as long as
`run_batch` took — ~0.85s, worse if `default_simulations` ever goes back up (DEC-012). RPC-style
messaging (`RabbitTemplate.convertSendAndReceive`) was considered and rejected: it would change the
transport without solving the actual problem, since Java would still be blocked waiting.

`CompatibilityEnginePort` changes shape accordingly: `assess(agentA, agentB) ->
CompatibilityAssessment` becomes `requestAssessment(matchId, agentA, agentB) -> void`.
`AmqpCompatibilityEngineClient` publishes to `compatibility.score.requested` and returns;
python-engine's `CompatibilityScoreConsumer` (`app/messaging.py`, aio-pika) processes the request
and publishes to `compatibility.score.computed`; `CompatibilityScoreResponseListener` consumes that
and calls the new `ApplyCompatibilityScoreUseCase`. `RequestCompatibilityScoreUseCase` no longer
applies a score itself — it only publishes and returns the match as-is.
`POST /api/matches/{id}/score` returns `202 Accepted`, not `200`, to say so honestly.

`EngineCompatibilityClient`/`EngineRestClientConfig` and their REST-only DTOs
(`CompatibilityRequestDto`/`CompatibilityResponseDto`) are deleted, not left alongside the new path
— nothing called them anymore, and two ways to ask for the same thing is exactly the kind of
parallel-path complexity this project avoids on purpose. `POST /api/v1/compatibility` on the
python-engine side is untouched: it's a separate, still-useful way to exercise the Engine directly
without a broker, not a duplicate of the async flow.

Neither side validates `modelVersion` on the async path the way the REST router does — python-engine's
`build_response_payload` calls `evaluate()` directly, which never checked the version to begin with
(only `app/api/compatibility.py`'s router did). Noted as a gap, not fixed here: see Open questions.

### DEC-017 — `SimulationRun` persistence: SQLAlchemy over psycopg3, three tables split by granularity
Resolves the open question from M3: SQLAlchemy (async, over psycopg3 — the same `postgresql+psycopg://`
URL scheme works for both sync and async engines, confirmed against the real `postgres-engine`), not
raw SQL, for the same reason Java uses Hibernate/JPA instead of hand-written queries — and it's the
same pedagogical parallel the project already draws between the two services' persistence layers.

The schema is split across `simulation_runs` / `simulation_trajectories` / `simulation_transitions`
instead of one wide table because M5's two data-hungry views need very different granularity from
the same batch: the spaghetti plot needs a handful of *full* day-by-day trajectories (expensive, so
only the first `DEFAULT_SAMPLE_SIZE` — 50 — simulations get one), the Markov graph needs *aggregated
counts* across the whole batch (cheap, so every simulation contributes, not just the sampled ones).

Capturing that data required instrumenting `run_simulation`/`run_batch` with an optional
`SimulationObserver` (a `Protocol`, `app/domain/simulation.py`) that gets narrated each day's state
and each simulation's outcome — the domain stays free of persistence concerns; it doesn't know or
care whether anyone's listening. `SamplingRecorder` (`app/services/simulation_recording.py`) is the
only implementation, and defaults each simulation's "previous" emotional state to `STABLE` (matching
`RelationshipState.initial()`) so **self-loops get counted as transitions too** — without that, the
empirical matrix would be missing its dominant weights entirely, since staying put (not changing) is
most of what a `0.97` self-loop (DEC-015) actually produces. Verified against real containers: a real
score request produced 50 sampled trajectories and 13 distinct observed transitions dominated by
`stable -> stable` (88,045 of ~96,000) — the recalibrated matrix behaving as intended, not just in
the unit tests.

Only the AMQP path (a real match's score request) persists a run. The REST endpoint
(`POST /api/v1/compatibility`, kept per DEC-016 as a broker-free way to exercise the Engine directly)
does not — its calls aren't tied to a real match, and persisting them would just be test noise in
the tables M5's admin panel reads from. `services/compatibility.evaluate()` now returns an
`Evaluation` bundling the wire response with the recorder; the REST endpoint takes `.response` and
discards the rest.

### DEC-018 — M5's three visualizations, the live feed, and a client-only-layout lesson
`d3-force` (not `react-flow`, not a hand-rolled static SVG) for both graph views — the Markov
graph's 5 discrete states and the compound graph's users/matches. `react-flow` was ruled out as too
opinionated for what's ultimately a plain node-link diagram; a hand-rolled layout was viable for the
5 fixed Markov states but wouldn't generalize to the compound graph's unbounded, ever-changing user
count, so one library serves both instead of introducing a second later.

That choice surfaced a real SSR/hydration lesson: the compound graph's `layout()` initially ran
directly in the component's render body, computing during both the server prerender and the client
hydration render. 300 ticks of `Math.cos`/`Math.sin`/`sqrt`-driven force simulation can round
ever-so-slightly differently between the server's V8 build and the browser's, and after 300
compounding iterations that's enough to visibly diverge — React flagged a hydration mismatch on
every node's `cx`/`cy`. Fixed by moving the computation into a `useEffect` (client-only, never runs
during SSR) — the same reason `MarkovGraphView`'s fetch-then-layout never had this problem: nothing
chaotic ever ran before mount there either, just by how it was already built.

A second, related bug: the SVG's `viewBox` was originally sized from a formula guessing at the node
count (`side = f(sqrt(n))`), independent of the actual force parameters. Repulsion is pairwise, so
it grows faster than linearly with node count — a size tuned to look right for 4 nodes clipped
almost everything for 20, leaving only one edge that happened to fall inside the guessed box
visible. Fixed by letting the simulation settle unconstrained around the origin and fitting the
`viewBox` to the resulting bounding box afterward, instead of guessing it beforehand.

The compound graph needed no new java-system endpoint: `GET /api/users` and `GET /api/matches`
already existed and only ever serialize Layer 1 fields, so the non-negotiable invariant held for
free.

The live "the System deciding" feed reads `MatchAssignedEvent`/`MatchExpiredEvent` — scoring
request/response is deliberately out of scope for now (see Open questions). A new `RecentEventsFeed`
(`com.pointeight.events`, mirroring `status`'s existing precedent of a flat top-level package with
no domain/application split for a cross-cutting, non-aggregate concern) sits next to
`DomainEventLogger` as another fan-in `@EventListener` on every `DomainEvent`, keeping an in-memory,
process-lifetime ring buffer of the last 200 it recognizes — deliberately not every event type, and
deliberately not persisted: this is an observability feed for one admin panel, not an audit log.
`GET /api/events?since={sequence}` is polled by the panel every 2.5s rather than pushed over SSE:
simpler to build and debug, and consistent with the rest of the panel's existing polling style
(M0's `force-dynamic` status probes) — at the cost of not being real push and up to ~2.5s of lag.

### DEC-019 — Engine endpoint tests run against a real, ephemeral Postgres via testcontainers
M6 closes the gap the M5 review flagged: `/api/v1/markov-graph` and
`/api/v1/matches/{id}/trajectories` had zero test coverage beyond the pure `build_markov_graph()`
helper — nothing exercised the actual `GROUP BY`/`SUM` query, the `selectinload` eager-load, or the
`ORDER BY ... LIMIT 1` "most recent run" logic.

Real Postgres via `testcontainers`, not SQLite and not a mocked `AsyncSession`. Neither alternative
would have caught what these tests are actually for: a mocked session never runs real SQL at all,
and `SimulationTrajectory.points` is a Postgres-native `JSONB` column with no SQLite equivalent —
testing against a different dialect risks exactly the "passes in CI, breaks against the real
database" gap the missing coverage already represented. `testcontainers` starts one Postgres
container per test *session* (`tests/conftest.py`), not per test or per `docker compose` service:
schema is created once, and each test's rows are truncated afterward for isolation — cheap enough
that the whole suite, container startup included, still runs in a few seconds.

This is the first place either service's test suite depends on a real database — every other test
in both `java-system` (104 tests) and `python-engine` runs against mocked ports/repositories. That
precedent holds everywhere else; it only breaks here because the thing under test **is** the SQL.

### DEC-020 — java-system migrates to Flyway, replacing `ddl-auto: update`
`V1__init.sql` (`java-system/src/main/resources/db/migration`) is captured from the live schema
Hibernate's `ddl-auto: update` had already built through M0-M5 (`pg_dump --schema-only` against the
running `postgres-system`), not hand-authored from a blank slate — the goal was a baseline
indistinguishable from what already exists, not a redesign. `ddl-auto` switches to `validate`
(`DEC-008` is superseded, not deleted — it's still the right record of why `update` was fine
*then*): Hibernate now only checks the entities agree with the schema, it never creates or alters
anything.

Verified by dropping the local `postgres-system` volume entirely and letting Flyway bootstrap a
fresh database from `V1` alone: Hibernate's `validate` accepted it with no mismatch, and a real
user/match round-trip through the API worked end to end — not just "the app starts."

The one deliberate deviation from a pure `pg_dump` capture: `user_hobbies`/`user_seeking_genders`'s
foreign keys keep Hibernate's original auto-generated names in the *running* database, but this
migration gives them readable ones (`fk_user_hobbies_user`, `fk_user_seeking_genders_user`) instead
— a migration is meant to be read, and Hibernate's schema validator doesn't check constraint names,
only tables/columns/types, so renaming them costs nothing. `matches` still has no FK to `users`:
the Match aggregate references `UserId` only, no JPA relationship (`DEC-004`), and this baseline
preserves that rather than quietly introducing a constraint the domain never actually declared.

### DEC-021 — Mobile client: React Native + Expo, reveal-only, no accept/reject
Not in the original spec (`hang-the-dj-sim.md` never mentions a mobile client at all) — decided
during M4's PR discussion, scoped for real once M5/M6 wrapped. A minimal **native** mobile client
for the person being matched (not an admin tool): a single "reveal" screen once a match activates,
showing the other person's photo and Layer 1 profile (age, city, profession, hobbies — never Layer
2, never a name, since the domain has none). No accept/reject step: that would contradict the
project's central premise that the System assigns unilaterally, no swiping or profile selection.

React Native + Expo over Swift/Flutter or a bare RN setup, for the same reason CLAUDE.md already
assumes React/Next.js familiarity elsewhere: it reuses that background directly, and Expo gets push
notifications (`expo-notifications`) without an App Store-only iOS build. `mobile-client/`, Expo SDK
57, **Expo Router** (file-based, not a plain `App.tsx` entry) rather than React Navigation's
imperative tree — deliberately chosen for the same reason as the framework itself: it's the same
mental model as Next.js's App Router, so it transfers rather than being a second routing paradigm
to learn. `expo-image` over React Native's core `Image` for the photo (better caching/perf for
exactly this kind of photo-heavy screen) and `expo-linear-gradient` for the reveal overlay.

First block (this one) is the screen alone, hardcoded mock data — no backend integration yet. That
exposed a real prerequisite gap: **there is no authentication anywhere in the system.** Without it,
"show this phone's user their active match" has no way to know which user the phone belongs to.
Decided to build minimal real auth (not a client-side "pick your persona" placeholder) as its own
upcoming block, before wiring the app to `java-system` for real — a placeholder would need
replacing later anyway, and this is a genuine, contained piece of backend learning (basic Spring
Security) in its own right.

Known groundwork identified but not yet built: `Profile` has no photo field; a new endpoint
exposing Layer 1 + photo for a user's *active* match only, with the Layer 2 non-leak invariant
preserved the same structural way `UserResponse` already does it; a push-notification trigger point
wherever a match transitions to `ACTIVE` (today `Match.activate()` records no domain event at all —
unlike `propose()`/`expire()`, nothing observes this transition yet).

**Two sub-questions raised at the same time, still open, revisit once auth and the reveal endpoint
exist:**
- Where Layer 2 actually comes from in-fiction, once there's a real client: `User.recalibrate()`
  (M1, unused since) hints at the intended shape — a `TraitDerivation` baseline at registration,
  then ongoing recalibration from indirect post-date signals collected through the app (never an
  explicit "rate your date" prompt, since the user must never knowingly shape their own Layer 2).
- How to detect the *actual date* ending (the in-person meeting), not just the match's assigned
  window expiring — those aren't the same thing. GPS co-presence detection is the most narratively
  honest option but a large privacy/battery cost; treating `ACTIVE → EXPIRED` as an approximate
  proxy is far cheaper but conflates "the window closed" with "they actually met." Rejected: a fixed
  polling cadence (e.g. checking in 2x/day) — it reads as overt surveillance rather than organic
  signal collection, which undermines the same "unilateral, unnoticed observation" premise the
  System is built on.

### DEC-022 — Minimal authentication: JWT, `Account` separate from `User`
DEC-021 exposed a real prerequisite for the mobile client: **there was no authentication anywhere
in the system.** Built as its own four-block sequence rather than one change, since it touches
domain modeling, an existing endpoint's contract, and a new cross-cutting HTTP concern:

1. **`Account`, not fields on `User`.** Login identity and the dating profile that gets matched
   have different lifecycles — the same reasoning that already keeps Layer 1 and Layer 2 apart
   inside `User`. `Account` (`com.pointeight.auth`) is keyed by `UserId` directly (no surrogate id
   of its own: it's a one-to-one detail of a user, not an aggregate), with its own `Email` and
   `HashedPassword` Value Objects. `accounts` has a real FK to `users` — unlike `matches` (DEC-004,
   no FK, cross-aggregate reference only), this is a true one-to-one composition. Registration is
   orchestrated in `auth.application.RegisterAccountUseCase`, not inside `user`'s own
   `RegisterUserUseCase`: it calls that use case and then creates the linked `Account`, in one
   `@Transactional`, so `user` never needs to know `auth` exists. The email-uniqueness check runs
   *before* creating the `User`, so a duplicate email can't leave an orphaned profile behind.
2. **JWT, self-issued and self-validated** (`jjwt`, not Spring Security's OAuth2 Resource Server
   module, which assumes trusting an external JWK endpoint this system doesn't have). A `TokenIssuer`
   port keeps the domain/application layers oblivious to JWT specifically; `JwtTokenIssuer` signs
   with a shared HMAC secret (`pointeight.auth.jwt-secret`, dev default in `application.yml`,
   override via `JWT_SECRET` outside local dev), subject = `UserId`, 24h expiry by default.
3. **`JwtAuthenticationFilter`** (`OncePerRequestFilter`) reads `Authorization: Bearer`, and — if
   the signature and expiry check out — populates `SecurityContext`. It never rejects a request
   itself; a missing or invalid token just leaves the request unauthenticated, and whether that's a
   problem is `SecurityConfig`'s `authorizeHttpRequests` decision per endpoint, not this filter's.
4. **`GET /api/auth/me`**, the first (and today, only) endpoint requiring a valid token — not
   because it's needed on its own, but because "the filter works" needs *something* real to prove
   it against, and the actual reveal endpoint doesn't exist yet. It also happens to be a genuinely
   useful pattern going forward: a client can check whether its stored token is still valid without
   touching a real resource.

**Everything else stays wide open on purpose.** Merely adding `spring-boot-starter-security` to the
classpath makes Spring Boot lock down every endpoint with HTTP Basic and a random generated
password by default; `SecurityConfig` replaces that with an explicit `permitAll()` for everything
except `/api/auth/me`, so the admin panel and every existing endpoint keep working unauthenticated
exactly as before. The reveal endpoint is meant to join `authenticated()` once it exists, not to
trigger locking down the rest of the API.

**Two bugs found and fixed via real Docker verification, not caught by the unit tests:**
- `@WebMvcTest(UserController.class)` started failing with 401s the moment `spring-boot-starter-
  security` landed on the classpath: that test slice doesn't scan `SecurityConfig` (a plain
  `@Configuration`, outside a `@WebMvcTest`'s narrow bean scan), so Spring Boot's own default
  security auto-configuration filled the gap instead. Fixed with `@AutoConfigureMockMvc(addFilters
  = false)` — that test is about controller/validation behavior, not security.
- Spring Security's own default response to a request with no credentials at all against a
  protected endpoint is **403 Forbidden**, not 401 — semantically wrong (401 means "say who you
  are", 403 means "I know who you are and it's not enough"). Fixed with a one-line
  `authenticationEntryPoint` that sends a bare 401 instead of Spring's default.

## Open questions

- Profile synchronization strategy toward `python-engine`: does the payload carry full profiles, or
  does the engine keep its own event-fed replica? To be defined in M4.
- Bump `modelVersion` to `"v1"` on the java-system side (DEC-013) — and once it's bumped, decide
  whether `modelVersion` should be validated on the AMQP path too, not just the REST one (DEC-016).
- Who triggers `activate` on a `PENDING` match: today it's manual. The scheduler should either
  activate it as soon as it's assigned, or leave `PENDING` as a preparation window with its own
  timeout.
- `cumulativeConfidenceScore` exists but never moves: still need to define how a match's outcome
  adjusts it. Depends on having real predictions from the Engine (M3, done) — the adjustment rule
  itself is still undefined.
- Candidate pool for `AssignNextMatchUseCase` (DEC-014) is the first N registered users, filtered in
  memory — no query pushes Layer 1 reciprocity or availability down to the database. Fine at this
  scale, not something to carry into M5 unexamined.
- The live feed (DEC-018) doesn't show the async scoring cycle (request over RabbitMQ, response
  applied by `ApplyCompatibilityScoreUseCase`) — neither side publishes a `DomainEvent` for it today.
  Adding one would touch `RequestCompatibilityScoreUseCase`/`ApplyCompatibilityScoreUseCase`, not
  just `RecentEventsFeed`. If polling lag ever actually matters, revisit SSE then too.
- The Engine's `expiryDays` (predicted survival, in the simulation's own day-clock — `MAX_DAYS =
  1000` in `run_simulation`) reaches `CompatibilityScoreResponseMessage` on the Java side but is
  never applied: `ApplyCompatibilityScoreUseCase` only assigns the score, `Match.expiryDuration`
  stays whatever `default-expiry-seconds` (12h) or the manual override set at creation. Wiring it
  through isn't just plumbing — `expiryDays` and the match's real expiry window are in genuinely
  different units (a "survived" simulation reports `1000`, i.e. ~2.7 real years if read as
  `Duration.ofDays`), and no conversion rule between simulated days and real match-window time has
  ever been specified, here or in the spec doc. Needs that rule defined before it's implemented,
  not a unit coercion.
