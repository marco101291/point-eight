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

## Open questions

- Profile synchronization strategy toward `python-engine`: does the payload carry full profiles, or
  does the engine keep its own event-fed replica? To be defined in M4.
- Persistence in `python-engine`: SQLAlchemy vs. direct psycopg for `SimulationRun`. To be defined
  in M3.
- Who triggers `activate` on a `PENDING` match: today it's manual. In M4 the scheduler should either
  activate it as soon as it's assigned, or leave `PENDING` as a preparation window with its own
  timeout.
- `cumulativeConfidenceScore` exists but never moves: still need to define how a match's outcome
  adjusts it. Depends on having real predictions from the Engine (M3).
