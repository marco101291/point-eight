# 0.8 (Point Eight)

Matchmaking simulation: an institutional orchestrator (Java) that unilaterally assigns matches,
backed by a compatibility engine (Python) that runs thousands of Monte Carlo simulations over
emotional Markov chains.

The name comes from the 0.8:1 (positive:negative) ratio that Gottman/Levenson identified as a
breakup risk signal. It's the constant that lives in `collapse_probability`.

Full spec: [`hang-the-dj-sim.md`](hang-the-dj-sim.md).

## Status — M2 (Minimal engine) ✅

The System has real domain: `User` and `Match` aggregates, the match state machine, user CRUD, and
manual match creation. Python is now wired in too — `python-engine` exposes
`POST /api/v1/compatibility` and `java-system` consumes it synchronously at
`POST /api/matches/{id}/score`, so `compatibilityScore` can already hold a real value. The score
itself is still random until M3 brings the real Monte Carlo model.

| Service | Role | Stack | Port |
|---|---|---|---|
| `java-system` | The System — orchestrator, state, rules | Spring Boot 3 / Java 21 | 8080 |
| `python-engine` | The Compatibility Engine | FastAPI / Python 3.12 | 8000 |
| `admin-panel` | The Coaster — UI, no business logic | Next.js 15 | 3001 |
| `postgres-system` | The System's DB | Postgres 16 | 5433 |
| `postgres-engine` | The Engine's DB | Postgres 16 | 5435 |
| `rabbitmq` | Async bus (used from M4 on) | RabbitMQ 3.13 | 5672 / 15672 |

## Getting started

```bash
cp .env.example .env      # adjust ports/credentials if needed
docker compose up --build
```

The first `java-system` build downloads the Gradle toolchain and takes several minutes.
No local Java or Gradle install is needed: the build runs inside the container.

All published ports come from `.env`, so if they clash with another project they're changed there
without touching the compose file.

Verification:

```bash
curl localhost:8080/api/status     # The System
curl localhost:8000/api/status     # The Engine
open http://localhost:3001         # Admin panel (shows both services' status)
open http://localhost:15672        # RabbitMQ management (pointeight / pointeight)
```

## The System's API

```
POST   /api/users                 registration (Layer 2 optional and write-only)
GET    /api/users?page=&size=     paginated listing
GET    /api/users/{id}
PATCH  /api/users/{id}            edits Layer 1 only
DELETE /api/users/{id}

POST   /api/matches               { userAId, userBId, expiryDurationSeconds? }
GET    /api/matches?status=&page= listing, filterable by status
GET    /api/matches/{id}
POST   /api/matches/{id}/activate
POST   /api/matches/{id}/expire
POST   /api/matches/{id}/reject
POST   /api/matches/{id}/score    asks the Engine for a compatibility score (M2)
```

### The match's state machine

```
PENDING ──activate──> ACTIVE ──expire──> EXPIRED  (terminal)
   │                    │
   └───reject───> REJECTED <──reject────┘         (terminal)
```

The whole transition table lives in `MatchStatus`. An illegal transition responds with **409** and
a `ProblemDetail` that includes `from`, `to`, and `allowedTransitions`.

### The user's two layers

The model separates what the user sees from what the System knows:

- **Layer 1** (`Profile`) — age, gender, city, profession, hobbies. Visible and editable.
- **Layer 2** (`SimulationParameters`) — attachment style, weights toward Gottman's four horsemen,
  infidelity history, active addiction, baseline stress. **Comes in through the API and never goes
  out.**

The guarantee is structural: `UserResponse` is a record that only declares Layer 1 fields, so
there's no filter to maintain or exclusion list that could be forgotten. If registration doesn't
provide Layer 2, `TraitDerivation` infers it from the profile — the user never chooses what
parameters they're simulated with.

## Tests

```bash
./scripts/java-test.sh            # 73 domain tests, no Spring or Postgres
```

They run in a Gradle container (no local JDK needed) with the host's UID, so they don't leave
root-owned files in the tree.

## Development per service

**Java** — everything inside Docker, or with a local JDK 21:
```bash
cd java-system && gradle bootRun
```
Architecture: package-by-feature (`user/`, `match/`, `shared/`, `config/`) with Ports & Adapters
inside — `domain/` (pure POJOs, no JPA), `application/` (use cases), `infrastructure/` (entities,
mappers, controllers). See `docs/architecture.md`.

**Python** — local virtual environment:
```bash
cd python-engine
python3 -m venv .venv && source .venv/bin/activate
pip install -r requirements-dev.txt
uvicorn app.main:app --reload
black app && mypy app
```
Interactive docs at http://localhost:8000/docs.

**Next.js**:
```bash
cd admin-panel && npm install && npm run dev
```

## Conventions

- Conventional commits scoped per service: `feat(java-system): add match state machine`
- One PR per milestone or sub-task; never mix `java-system` and `python-engine` in the same PR
- Java: Google Java Style, package-by-feature, records for Value Objects
- Python: PEP 8, type hints required, `black`, Pydantic as the I/O contract

## Roadmap

- [x] **M0** — Setup: monorepo, compose, three services online
- [x] **M1** — Java base domain: `User`/`Match`, state machine, manual match
- [x] **M2** — Minimal engine: FastAPI returns score + expiry, Java consumes it via REST
- [ ] **M3** — Real simulation: agents, Strategy scenarios, Markov, `run_batch` with NumPy
- [ ] **M4** — Async: RabbitMQ, `MatchExpiredEvent`, `collapse_probability` interpolating 5:1 → 0.8:1
- [ ] **M5** — Admin panel: spaghetti plot, Markov graph, force-directed graph
- [ ] **M6** — Polish: domain and engine tests, architecture documentation
