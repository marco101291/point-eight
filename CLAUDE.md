# 0.8 (point-eight) — context for Claude Code

Deliberate-practice project in Java + Python. Matchmaking simulation: an institutional orchestrator
(Java/Spring) unilaterally assigns matches, backed by a compatibility engine (Python/FastAPI) that
runs thousands of Monte Carlo simulations over emotional Markov chains. The name comes from the
0.8:1 positive:negative ratio that Gottman/Levenson identified as a breakup risk signal.

Full spec: [`hang-the-dj-sim.md`](hang-the-dj-sim.md).
Architecture decisions: [`docs/architecture.md`](docs/architecture.md) (`DEC-001` … `DEC-013`).

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
| **M4** Async + events | ⬜ | RabbitMQ, `MatchExpiredEvent` triggers the next match, `collapse_probability` |
| **M5** Admin panel | ⬜ | Spaghetti plot, Markov graph, force-directed graph |
| **M6** Polish | ⬜ | Engine tests, migration to Flyway, C4 |

Inventory: 59 Java files (main), 6 test, 7 Python, 3 TS (before M2).

**The Python Engine now runs a real simulation**, but `POST /api/v1/compatibility` still answers
Java with `modelVersion: "v0"` — bumping it to `"v1"` needs a matching one-line change in
`EngineCompatibilityClient` (`DEC-013`), deliberately left for its own java-system commit. Nothing
persists a `SimulationRun` yet; `evaluate()` computes and returns the report in the same request.
**The admin panel is just a traffic light**: it checks whether the two services respond, it doesn't
read users or matches. `Match.compatibilityScore` can already have a real value (requested by hand
via `POST /api/matches/{id}/score`), but nothing triggers it automatically yet — that lands in M4
with `MatchAssignedEvent`.

---

## Environment — read before suggesting commands

- **No JDK or Gradle installed.** All Java build and test runs in a container. Use
  `./scripts/java-test.sh`; **don't** suggest `gradle` or `./gradlew` directly. The script passes
  `--user $(id -u):$(id -g)` because, without that, the Gradle container leaves `build/` and
  `.gradle/` owned by root and breaks later deletions.
- **Published ports come from `.env`**, and several aren't at the "obvious" value because they
  clashed with other projects running on the same machine (`alivia-postgres` occupies 5434, a
  `next-server` occupies 3000). They ended up as: System **8080**, Engine **8000**, panel **3001**,
  System Postgres **5433**, Engine Postgres **5435**, RabbitMQ **5672** / **15672**.
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
- **Hibernate generates the schema** (`ddl-auto: update`, `DEC-008`). There are no migrations yet;
  it migrates to Flyway in M6. When changing entities, don't write SQL by hand.
- **`seekingGenders` is a `Set<Gender>`**, even though the doc writes it in the singular (`DEC-005`).

---

## Pending decisions

Recorded at the end of `docs/architecture.md`. Most relevant for the upcoming milestones: whether
the Engine keeps a replica of profiles or receives them in the payload (M4), `SimulationRun`
persistence (M3), who triggers `activate` on a `PENDING` match, and how `cumulativeConfidenceScore`
gets adjusted (it exists today but never moves). The Java → Python payload format was resolved in
M2 as `DEC-009`.
