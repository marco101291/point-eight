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

**Update, once auth existed:** `Profile` gained `photoUrl` (`V3__profile_photo.sql`, required like
every other Layer 1 field, backfilled for existing rows since it's `NOT NULL`). `GET
/api/matches/me/reveal` is the real reveal endpoint — `RevealActiveMatchUseCase` (`match.application`,
crosses into `UserRepository` the same way `RequestCompatibilityScoreUseCase` already does)
resolves the caller's one ACTIVE match from the JWT subject, never a path parameter, and returns
only the counterpart's `Profile`; `RevealResponse` declares just `age`/`city`/`profession`/
`hobbies`/`photoUrl`, the same structural non-leak guarantee `UserResponse` uses for Layer 2, with
no name field to leak either. `NoActiveMatchException` (`ResourceNotFoundException`, so it's a
plain 404) covers "nothing to reveal yet." `SecurityConfig` now requires a token on this endpoint
too, alongside `/api/auth/me`. The mobile client got a login screen and switched off mock data:
`expo-secure-store` holds the JWT (falls back to `localStorage` under `expo start --web`, since
SecureStore has no native backing there), and the reveal screen now has real loading/no-match/error
states instead of a single hardcoded payload. Push notifications on activation are still open — no
domain event exists for that transition yet, so there's nothing to trigger off of.

**Two sub-questions raised at the same time, resolved as `DEC-026` below:**
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

### DEC-023 — Access/refresh token split, so logout actually revokes something

DEC-022's JWT was self-issued and self-validated on purpose — nothing to check against a database
on every request. That had a real, deliberately-accepted cost, and building the mobile client's
logout button (M7) surfaced it directly: a self-validating JWT **can't be revoked before it
expires**. "Logging out" was purely `SecureStore.deleteItemAsync` on the phone — the 24h token
itself stayed valid the whole time if anyone else had a copy of it.

Two ways to close that were on the table: a server-side blocklist of revoked token ids (checked on
every request — reintroduces exactly the per-request database cost DEC-022 avoided, but is a small,
contained change) or the industry-standard pattern (what Auth0/Firebase/Cognito/OAuth2 all
actually do): a short-lived access token nobody bothers revoking, backed by a long-lived refresh
token that's a real, deletable database row. Went with the latter — closer to what a real system
would do, at the cost of `mobile-client` needing actual refresh logic, not just a bigger login
screen.

**The split:**
- **Access token** (`JwtTokenIssuer`): still a JWT, still self-validated, just short now —
  `pointeight.auth.access-token-ttl-minutes`, default 15. Nobody can kill one early; the whole
  strategy is to make that window small enough not to matter.
- **Refresh token** (`auth.domain.RefreshToken`): opaque (32 random bytes, base64url) — never a
  JWT, since nothing needs to read claims out of it locally, only look it up by hash. Hashed at
  rest (SHA-256, deterministic — the hash doubles as the lookup key) for the same reason
  `HashedPassword` never stores a raw password: a leaked `refresh_tokens` row shouldn't hand out a
  live credential. `pointeight.auth.refresh-token-ttl-days`, default 30. Deliberately *not* run
  through `PasswordEncoder`/bcrypt like `Account`'s password — bcrypt's slow-by-design work factor
  defends against offline guessing of a *low-entropy human password*; a 256-bit random token has no
  guessing surface to defend against, so a fast, deterministic hash is the right tool, not the
  wrong one reused out of habit.
- **Rotation on every refresh** (`RefreshAccessTokenUseCase`), race-safe and with reuse detection:
  every token belongs to a `familyId` (the lineage descended from one login), and rotating claims
  the presented token atomically (`RefreshTokenRepository#claim`, a conditional `UPDATE ... WHERE
  used_at IS NULL` — Postgres's own row locking, not a find-then-delete-then-insert from
  application code, which is what makes two concurrent refreshes on the same token unable to both
  "win"). A failed claim — the token was already used, whether that's a race against a concurrent
  refresh or a stale token being replayed later — revokes the *whole family*
  (`RefreshTokenRepository#deleteFamily`), not just the one token: nothing here can tell a benign
  race from real theft, so both get treated as theft, and every descendant of that lineage is
  forced back to a real login. See the Open questions entry on the one remaining nuance (the
  concurrent case's outcome isn't fully deterministic; the delayed-replay case — the actual threat
  this exists for — is).
- **`POST /api/auth/logout`** (`LogoutUseCase`): deletes the refresh token. Idempotent — logging
  out twice, or logging out a token that already rotated or expired, is not an error, since the
  caller's actual goal ("this shouldn't work anymore") is already true either way.
- **`POST /api/auth/refresh`**: deliberately *not* behind `SecurityConfig`'s `authenticated()` —
  requiring a valid (non-expired) access token to refresh would defeat the one case that matters,
  refreshing *after* it expired. The refresh token in the body is what proves identity here, the
  same way the password does at `/login`.

**What this doesn't fix:** the access token is still unrevokable inside its own 15-minute window —
logout (or a compromised refresh token getting deleted) only stops *future* silent renewal, it
doesn't retroactively kill a still-valid access token already in someone's hands. Accepted
trade-off, not an oversight; closing that fully would mean going back to the per-request blocklist
option this design specifically avoided.

`mobile-client` follows: `login.tsx` and `lib/tokenStorage.ts` store both tokens; `lib/api.ts`'s
`authenticatedFetch` retries a 401 exactly once after a silent refresh, and gives up (clearing both
tokens, sending the user to `/login`) if the refresh itself fails; the logout button calls `POST
/api/auth/logout` before clearing local state.

### DEC-024 — Push notifications on `MatchStatus → ACTIVE`, and why `mobile-client` needs a development build now

M7's last groundwork item: `Match.activate()` used to record no domain event at all, so nothing
existed to trigger a notification from. Closing it turned up a real constraint worth recording
before it cost debugging time later: **Expo Go has not supported remote push notifications since
SDK 53** (local, device-scheduled notifications still work there; a server-triggered one does
not). Since "the System notifies you, you do nothing" is the whole narrative point of this feature
— a local notification would mean the app polling and notifying itself, which contradicts DEC-021's
own "unilateral, unnoticed observation" premise the same way a fixed polling cadence for Layer 2
signals was already rejected — the only faithful option is a real development build via EAS,
installed instead of Expo Go on the test device. `mobile-client/eas.json` adds a `development`
build profile (`developmentClient: true`) and `expo-dev-client`; actually running `eas login` /
`eas build --profile development` needs the project owner's own Expo account, so that step doesn't
happen from here.

**The chain:** `Match.activate()` now records `MatchActivatedEvent` (mirroring `MatchAssignedEvent`/
`MatchExpiredEvent`, and — like those — picked up by `RecentEventsFeed` for the live feed too).
`MatchActivatedEventListener` (`match.infrastructure`) reacts `AFTER_COMMIT`, one call per user, via
`NotifyMatchActivatedUseCase` — `REQUIRES_NEW` for the exact reason `AssignNextMatchUseCase`
(`DEC-015`) already needs it: the calling transaction's `EntityManager` is on its way out by the
time an `AFTER_COMMIT` listener runs. A push failure for one or both users is logged and swallowed,
never rethrown — same reasoning `MatchExpiredEventListener` already applies to
`AssignNextMatchUseCase`: the activation that already happened stays activated regardless.

**Where the token lives:** `Account` (not a new aggregate) gained `registerPushToken` — one push
token per account, overwritten on each registration (`V6__account_push_token.sql`, nullable: most
accounts won't have one). A second device, or a reinstall, simply replaces the token on file rather
than this project taking on multi-device fan-out and the receipt-driven cleanup of dead tokens that
would come with modeling that properly. `POST /api/auth/push-token` (behind `authenticated()`,
alongside `/me` and the reveal endpoint) is how a device registers one — `mobile-client`'s
`lib/pushNotifications.ts` calls it after login and again on every launch already-authenticated,
never assuming last session's token is still current.

**What the notification says:** nothing about the match. No age, city, profession, and certainly no
name (there isn't one anywhere in this system) — just enough to prompt opening the app. A push
banner sits on the lock screen; Layer 1 has no business showing up there when the reveal screen's
whole point is a gated, in-app moment.

**A new outbound HTTP client, and why it's synchronous:** `push.infrastructure.
ExpoPushNotificationSender` calls Expo's push service (`https://exp.host/--/api/v2/push/send`) via
`RestClient` (Spring's, not a new dependency — `spring-boot-starter-web` already provides it; this
is the first thing in the codebase to use it since M4 moved the Engine call to AMQP, `DEC-016`).
Deliberately *not* pushed onto a queue the way scoring was: nothing here is on a request/response
critical path a caller is blocked waiting on — the trigger is already an `AFTER_COMMIT` listener
running outside any HTTP request. First verified against the real Expo endpoint (not mocked) with a
fake token: the call succeeded at the HTTP level (Expo returns 200 with a per-message error in the
body — `"DeviceNotRegistered"` — not an HTTP error status), confirming the whole chain fired
correctly before real device delivery was even possible to test.

**Real device delivery, and the trap that cost the most time in this block:** with a development
build installed and a real Expo push token registered, activation still failed —
`"InvalidCredentials" / "Unable to retrieve the FCM server key"` — even though `eas credentials`
had "succeeded." The Firebase service account key had landed in the wrong slot: EAS's credentials
menu lists **"Push Notifications (FCM Legacy)"** and **"Push Notifications (FCM V1): Google Service
Account Key"** as two separate entries, and the legacy one accepted a file path without complaint
even though Google shut that API down in 2024 — nothing in the CLI flags this as wrong at upload
time. The fix was uploading the same key again into the V1 slot specifically. Once corrected,
delivery was confirmed twice on a physical Android device: once via a direct call to Expo's
endpoint, once via a real `Match.activate()` → `MatchActivatedEvent` → listener →
`ExpoPushNotificationSender` cycle. M7 is closed.

### DEC-025 — Reveal screen split into countdown + profile detail, institutional look over dating-app conventions

Post-M7 mobile polish, not a new milestone: the reveal screen's first version showed the
countdown and Layer 1 data on one screen, expanding in place on tap. Two problems with that —
visually it read as a generic dating-app card (full-bleed photo, rows of labeled data), which
worked against the "the System observes you, you don't interrogate it" tone the login screen
already established; and once the data was up, there was no way to collapse it again short of
tapping the photo a second time, which doesn't read as an affordance.

Replaced with two routes instead of one screen with two states. `app/index.tsx` now shows only
the circular photo and a large countdown (`HH:MM:SS`, ticking client-side off the match's
`expiresAt` — see below) — closer to the login screen's own restraint than a profile card.
Tapping the photo runs a brief scale-bounce (`Animated`, no `react-native-reanimated` dependency)
and pushes `app/match-profile.tsx`, a separate screen with its own circular photo, Layer 1 facts
as plain centered serif text (no bordered rows — the earlier "table" look was explicitly the
thing being moved away from), and a bordered "Volver" control. Closing it is a real back
navigation, not a toggle, so it can't get stuck half-open.

`match-profile.tsx` fetches its own reveal rather than receiving one through route params: if the
match ends while the screen is open, the reveal call 404s, and rather than this screen trying to
own that state too, it just shows a generic "no longer available" message and lets the user go
back — `index.tsx`'s own `load()` discovers the real state (`no-match`) on return. This also
needed `java-system` to start returning the match's own `expiresAt` alongside the counterpart's
profile: `RevealActiveMatchUseCase` now returns `ActiveMatchReveal(profile, expiresAt)` instead of
a bare `Profile`, and `RevealResponse` serializes both.

Two layout bugs surfaced once this was actually used on a device, both from the same root cause:
the "Volver" control lived in a normal flex row above the centered content, so its height pushed
the content's vertical center down from the screen's true center — same-looking bug as "not
centered," but actually a flex-flow issue. Fixed by making the top bar `position: absolute` so it
floats over a `centerLayer` that owns the full screen height. The control itself was also nearly
invisible: its border used `theme.line` (`#26262a`), a hair different from the screen's own
background (`#0b0b0c`) — practically zero contrast. Switched to `theme.fg` for both this and the
equivalent control in the error state. Separately, `router.back()` silently does nothing if the
screen is ever reached with no navigation history behind it (e.g. a reload landing directly on
this route) — `handleBack()` now checks `router.canGoBack()` first and falls back to
`router.replace("/")`.

### DEC-026 — Layer 2 sourcing: a multiple-choice sign-up questionnaire, and a match-level recalibration trigger

Resolves both sub-questions `DEC-021` left open. The sign-up questionnaire below is now built
(`mobile-client/app/signup.tsx`, `lib/layer2Questionnaire.ts`); the recalibration trigger is still
only a design decision — no code for it exists yet.

**At sign-up:** rejected an explicit "rate your personality" form outright — a user can't
knowingly shape the model the System keeps of them, the same reasoning that keeps Layer 2 out of
every response. Instead, a bank of indirect, scenario-based questions, each phrased as a concrete
situation with fixed multiple-choice answers (never open text): free text would need AI/NLP
classification to map onto a trait, which is both unreliable and gameable — a user can type
anything, including nothing usable, and a scenario question with three fixed options can't be
answered with garbage. Each option maps deterministically to a value, no inference step at
request time.

Scoped to exactly the `SimulationParameters` fields that are actually personality, not fact or
derivable data: `infidelityHistory`, `relationshipHistory`, and `activeAddiction` are left
unrequested by design (factual/sensitive, not something a scenario question can honestly surface);
`stressBaseline` and `commitmentPaceExpectation` are already derived from profession/age per the
spec (`TraitDerivation`), so asking about them would be redundant. That leaves `attachmentStyle`,
`attachmentIntensity`, and `communicationProfile` — nine questions total:

- **Attachment (4, → `attachmentStyle`):** each question offers one option leaning secure, one
  anxious, one avoidant. Scored by plurality across all four: 3+ secure answers → `SECURE`;
  otherwise, if both anxious- and avoidant-leaning answers appear at all → `DISORGANIZED`
  (avoidant-anxious mixed, per attachment theory); otherwise whichever of anxious/avoidant has more
  answers wins; a clean tie falls back to `SECURE`.
- **Communication under conflict (4, → `communicationProfile`):** one question per horseman
  (criticism, contempt, defensiveness, stonewalling — same field order as
  `CommunicationProfile`'s constructor), each a "nunca / a veces / seguido" frequency scale mapped
  to `0.1 / 0.4 / 0.8` for that field.
- **Attachment intensity (1, → `attachmentIntensity`):** no field derives or asks this anywhere
  else, so without a question it silently stays at the flat default (`0.5`) for every user. One
  question, three options mapped to `0.2 / 0.5 / 0.8`.

No new backend work was needed: `POST /api/users` (`RegisterUserRequest.toSimulationParameters()`)
already accepted all three fields and already created both `User` and `Account` in one call
(`RegisterAccountUseCase`). `signup.tsx` is one route, one question per screen (a fade transition
between steps, a `n / 9` progress counter) rather than nine separate Expo Router routes — simpler
state (one array of selected option indices) and no navigation-stack complexity for something
that's really one linear flow. Layer 1 comes first, then the nine questions; the last answer
submits immediately (no separate review step), computes the Layer 2 baseline client-side via
`computeLayer2Baseline`, calls the existing endpoint, then logs in with the same credentials right
after, since registration returns `UserResponse`, not a token pair. Rather than landing straight on
the countdown screen, a confirmation view holds for a few seconds first — a ring animation and "En
breve te asignaremos una pareja..." — auto-advancing on its own (or immediately on tap), the same
"it decides, you're told" tone as the rest of the System rather than a "tap to continue" the user
has to drive themselves.

Verifying this end to end surfaced two things worth its own script: `scripts/seed-users.py`
populates thousands of randomized users via the same `POST /api/users` endpoint, since
`RandomEligibleCandidateStrategy` had almost nothing to pick from otherwise; and doing that
surfaced the "no first match on registration" gap recorded in Open questions below.

**Recalibration trigger, post-match:** fires once per match, at the match's own terminal
transition (`ACTIVE → EXPIRED` or `ACTIVE → REJECTED` — explicitly both, not just expiry), not
once per individual date. Rejected finer-grained triggers (per-date, or trying to detect the
in-person meeting itself) as too elaborate for what the System actually needs to know: not how
many dates happened, whether they became a couple, or anything past that — "simply the time they
spent together once they matched." A rematch (expire → match again with someone new) is a
different match entirely with its own lifecycle, not a second data point on the same one. Inputs
to the recalibration are indirect signals collected during the match's own active window — e.g.
whether the reveal screen was ever opened at all — never an explicit rating prompt, for the same
reason the sign-up questionnaire avoids one.

**Known gap this surfaces:** `Match.reject()` records no domain event today (only `propose()`,
`activate()`, and `expire()` do) — needed before this trigger can fire on the `REJECTED` side.

### DEC-027 — Two separate mechanisms close the "nothing ever triggers a match" gap, not one

Closes both open questions `DEC-026` (and DEC-025 before it) left about matching never actually
happening on its own: no auto-expiry, and no first match for a new registration. Originally
assumed these belonged in one `@Scheduled` job; turned out to be two different *kinds* of trigger,
and forcing them into one job would have meant polling for something registration can announce for
free.

**Auto-expiry stays genuinely time-driven** — nothing "happens" when a match's window runs out,
there's no event to react to, so it needs a poll. `MatchExpiryScheduler`
(`match.infrastructure`, `@Scheduled(fixedDelayString =
"${pointeight.match.expiry-check-interval-ms:60000}")`, `SchedulingConfig` adds the
`@EnableScheduling` nothing had turned on before) fetches every `ACTIVE` match in one page —
deliberately not an incrementing-offset loop, since expiring a match removes it from the `ACTIVE`
set mid-sweep, which would shift what "page 2" means out from under an offset-based scan; a single
fetch sized to the current `ACTIVE` count sidesteps that entirely, the same "fine at this scale"
tradeoff `AssignNextMatchUseCase`'s own candidate-pool fetch already makes. For each match past its
`expiresAt` (`Match.isDue(clock)`), it calls `MatchLifecycleUseCases.expire()` — not a bespoke bulk
update — so `MatchExpiredEventListener`'s existing rematch chain fires exactly as it already does
for a manual `/expire` call. This job's only job is finding what's due.

**First-match assignment turned out not to need polling at all.** A new registration is a real
event happening at a real moment — there's no reason to wait up to a minute for the next scheduler
tick to notice it when registration can announce it directly, the same way `Match.activate()`
announces itself instead of something polling for `ACTIVE` matches with no notification sent yet.
`User` gained the same `pendingEvents`/`pullEvents()` machinery `Match` already had (it had none
before this — first time a second aggregate needed it), and `User.register()` now records
`UserRegisteredEvent`. `RegisterUserUseCase.execute()` publishes it after `save()`, pulling events
from the pre-save `user` object rather than the one `save()` returns — the adapter round-trips
through `UserJpaMapper.toDomain()`, a fresh rehydrated instance with an empty event buffer, exactly
the reason `MatchLifecycleUseCases.apply()` already pulls from `match`, not from what `matches.save`
hands back. Since `RegisterUserUseCase` is only ever called from `RegisterAccountUseCase`
(`@Transactional`, default propagation), the event fires only once both `User` and `Account` are
durably committed together — not merely once the inner call returns.

`UserRegisteredEventListener` (`match.infrastructure`, `AFTER_COMMIT`) reacts by calling the
already-generic `AssignNextMatchUseCase.execute(userId)` — no changes needed there at all, it
already just checks `hasOpenMatch` and searches for a candidate regardless of whether the caller is
a fresh registration or a just-expired match. Deliberately placed in `match.infrastructure`, not
`user.infrastructure`: matchmaking is a `match`-package concern reacting to a `user`-package event,
the same direction `RevealActiveMatchUseCase` already crosses (`match` depending on `user`, never
the reverse).

**Known limitation:** the ~3000 users `scripts/seed-users.py` created before this shipped never
got the event fired, so they stay matchless unless something re-triggers them — acceptable for now
since their purpose was being a candidate *pool* for other users (`AssignNextMatchUseCase`'s
random selection doesn't care whether a candidate itself has ever been matched), not being matched
with each other.

### DEC-029 — python-engine's collapse dynamics recalibrated so bad pairs actually collapse early

Number assumes "match duration derived from the compatibility score" lands as `DEC-028` (PR #8,
open at the time of this branch, not yet merged) — this is a direct, real reaction to building
that: with real `expiryDays` flowing into a real duration, the Engine's own calibration became
something Java-side scaling could no longer paper over.

**What DEC-028's verification surfaced:** even a manually constructed, maximally toxic pair on
both sides (`DISORGANIZED`, every Gottman weight ≥0.9, active addiction, infidelity history) came
back with a median `expiryDays` around 580–625 out of 1000 across real batches. Bad predictions
weren't reaching the short end of the real-duration range at all — not a Java-side scaling problem
(already confirmed correct down to `expiryDays=1` in `ExpiryDurationPolicyTest`), but the actual
distribution the Engine was producing.

**Root cause:** `EmotionalState.STABLE`'s self-loop is 0.97 (already raised once, in M4, from an
M3 value so low that `0.85 ** 130 ≈ 0` made it "essentially impossible for *any* pair, however
compatible, to stay STABLE for a whole simulation" — see that comment in `app/domain/state.py`).
`_PERTURBATION_STRENGTH` (0.4) only let a maximally negative day weaken that self-loop to ~93% —
still highly sticky. Compounding that, `MoneyConflict`/`TrustBreach` (the two highest-negative
scenarios) both have low `affinity_by_state` while `STABLE` (0.2/0.1), specifically to stop every
pair converging toward the same mediocre survival odds (see that class's own comment) — but the
side effect is that *escaping* `STABLE` in the first place draws mostly gentle scenarios even for
a personality profile that would react badly to a real conflict. Net effect: even a maximally
incompatible pair spent most of a thousand-day simulation just getting out of `STABLE` before real
negative charge (and the ratio-based collapse hazard, `gottman.py`) could ever meaningfully engage.

**The fix:** `_PERTURBATION_STRENGTH` raised from 0.4 to 1.0 — a single constant, not a rework of
the transition table or the scenario-affinity design that already fixed a *different* over-collapse
bug in M4. Verified empirically (the same "run real batches, look at the resulting distribution"
method `MAX_COLLAPSE_PROBABILITY`'s own comment already documents, not a formula derived
analytically) across five profile tiers, 300 simulations each, before/after:

| Profile | Before: median expiryDays | After: median expiryDays |
|---|---|---|
| Very toxic (both sides) | 624 | 153–212 (range across runs) |
| Moderately bad | 766 | 398–510 |
| Neutral | 1000 | 1000 (unchanged) |
| Good | 1000 | 1000 (unchanged) |
| Very good | 1000 | 1000 (unchanged) |

Neutral-and-better pairs are unaffected because their days are net *positive* — the self-loop
strengthens under a positive `positivity`, same direction as before `_PERTURBATION_STRENGTH`
changed, just more of it; only pairs whose days are net negative move at all. Confirmed live
against the real stack too: a partially-toxic real match (one genuinely bad profile paired with a
random pool candidate, so diluted compared to the controlled toxic-toxic test above) landed at
`expiryDays` giving ~9.6 real days — a large, visible drop from the "months to ~2 years" this same
kind of pairing produced before the fix.

All 55 python-engine tests still pass unchanged (none hardcode an exact transition-weight number
tied to `_PERTURBATION_STRENGTH`'s specific value, only relational ones like "positive rate >
negative rate" and "stable pair survives more than volatile pair" — both hold, more clearly, after
this change) — `black`/`mypy --strict` clean.

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
- ~~`RefreshAccessTokenUseCase`'s rotation has a real race~~ and ~~no reuse-detection~~ — both
  closed. `RefreshTokenRepository#claim` is now an atomic, conditional `UPDATE ... WHERE used_at IS
  NULL`, so two concurrent `/refresh` calls on the same token can't both succeed; the loser calls
  `deleteFamily` on the whole rotation lineage, so presenting an old, already-rotated token (a
  delayed replay, not a race) reliably kills the current live session too, verified against real
  Postgres. One documented, non-security nuance survives: in the *concurrent* case specifically
  (not the delayed-replay one), whether the winner's brand-new token also gets caught by the
  loser's family-wide delete depends on ordering — if the winner's `save` hasn't committed yet when
  the loser's delete runs, the delete doesn't see it and the winner's session survives; if it has,
  the winner is caught too and both sides end up back at login. Both outcomes are safe (worst case:
  an extra unnecessary re-login), just not deterministic — tightening that further would mean
  serializing the two paths (e.g. `SELECT ... FOR UPDATE` before the family delete), a real cost
  for a benign-race edge case, not something this project's threat model calls for.
  Getting the fix right also cost a detour worth remembering: the first version wrapped `claim` +
  revoke-on-failure + throw in one `@Transactional` method, which either rolled back the revoke
  (the exception undoes everything in its own transaction, including a delete that just ran) or, if
  the revoke was moved to its own `REQUIRES_NEW` transaction, **deadlocked outright** — the
  suspended parent transaction holds a lock the child needs to delete that same row, and the parent
  can't resume to release it until the child returns. The actual fix was simpler than either: no
  `@Transactional` on `RefreshAccessTokenUseCase.execute()` at all, with `@Transactional` moved
  directly onto the individual `RefreshTokenJpaRepository` methods instead, so each repository call
  commits independently the moment it returns — nothing left open to deadlock against, and nothing
  to roll back a revoke that already happened.
- Raised and paused: adding a name field. Explicitly requested twice, pushed back on twice (it
  breaks the structural non-name invariant documented as non-negotiable — see CLAUDE.md), confirmed
  twice by the project owner, then paused mid-discussion ("mejor pensemos esto de nuevo") before any
  code changed. No implementation exists. Needs the project owner to either resume or drop this
  explicitly before it's touched again — not something to infer from later, unrelated requests.
- `pointeight.match.default-expiry-seconds` is `43200` (12h) today, and every `Match` test uses the
  same figure — it's never been exercised at the scale a real "how long are these two paired"
  window might actually need (weeks, months). Nothing in `Match`'s own logic assumes hours (it's
  plain `Instant`/`Duration` arithmetic), but `mobile-client`'s countdown display does: it formats
  remaining time as `HH:MM:SS` with no cap, so a month-scale match would render as something like
  `"720:00:00"` in the giant countdown DEC-025 just built. If match duration is ever meant to
  reflect something closer to relationship length rather than a fast demo cadence, the countdown
  display needs a different treatment at that scale (e.g. a coarser unit above some threshold), not
  just a passing domain test with a longer `Duration`.
- ~~No scheduler exists to auto-expire matches~~ and ~~a newly registered user never gets a first
  match~~ — both closed by `DEC-027`: `MatchExpiryScheduler` polls for due matches,
  `UserRegisteredEvent` + `UserRegisteredEventListener` handle first-match assignment without
  polling. The ~3000 users seeded before `DEC-027` shipped stay matchless themselves (see DEC-027's
  own "known limitation") — not re-triggered retroactively, since it wasn't needed for what they're
  actually for (being candidates for other users, not getting matched with each other).
