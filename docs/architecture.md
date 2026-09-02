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
bumped in this change: Java's `EngineCompatibilityClient` still sends `"v0"`, and this branch is
scoped to `python-engine` only (never mix `java-system` and `python-engine` in one PR/branch). The
bump is a deliberate, separate one-line java-system follow-up.

## Open questions

- Profile synchronization strategy toward `python-engine`: does the payload carry full profiles, or
  does the engine keep its own event-fed replica? To be defined in M4.
- Persistence in `python-engine`: SQLAlchemy vs. direct psycopg for `SimulationRun`. M3 added the
  engine that produces a `CompatibilityReport`, but nothing persists it yet — `evaluate()` computes
  and returns it in the same request. Still open.
- Bump `EngineCompatibilityClient`'s `modelVersion` to `"v1"` on the java-system side (DEC-013).
- Who triggers `activate` on a `PENDING` match: today it's manual. In M4 the scheduler should either
  activate it as soon as it's assigned, or leave `PENDING` as a preparation window with its own
  timeout.
- `cumulativeConfidenceScore` exists but never moves: still need to define how a match's outcome
  adjusts it. Depends on having real predictions from the Engine (M3).
