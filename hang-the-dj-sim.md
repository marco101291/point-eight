# 0.8 — Matchmaking simulation (practice project: Java + Python)

Practice project inspired by the "Hang the DJ" episode (Black Mirror), faithful to the canon but
with a compatibility algorithm based on real literature (attachment, communication under conflict,
not profile similarity). Goal: practice Java and Python in parallel, each with a distinct
architectural role.

Project name: **0.8** (see section 10 — Naming & branding). The folder/package names used in this
document (`hang-the-dj-sim`, `com.system`) are provisional and should migrate to `point-eight` /
`com.pointeight` once M1 starts.

---

## 1. System overview

| Component | Narrative role | Technical role | Language |
|---|---|---|---|
| The System | Institutional authority, orchestrates the compound | Orchestrator, state, business rules, API | Java (Spring Boot) |
| The Compatibility Engine | The hidden simulation the system runs | Monte Carlo + Markov compute engine | Python (FastAPI) |
| The Coaster / Frontend | The interface the user sees | UI, no business logic | Next.js (your usual stack) |

There's no swiping or profile selection: the System unilaterally assigns the next match when the
previous one expires, triggered by an event.

---

## 2. Architecture per service

### Java — "The System" (orchestrator)
- **Style**: Hexagonal Architecture (Ports & Adapters) + tactical DDD
- **Aggregates**: `Match`, `User`
- **Value Objects**: `MatchStatus`, `CompatibilityScore`
- **Domain Events**: `MatchExpiredEvent`, `MatchAssignedEvent`
- **Patterns**: State (match lifecycle), Strategy (candidate selection), Specification (Layer 1
  filters), Factory (payload toward Python)
- **Framework**: Spring Boot 3, Spring Data JPA, Spring AMQP (RabbitMQ), Spring Events
- **Code style**: Google Java Style Guide, package-by-feature (`match/`, `user/`, `simulation/`),
  records for Value Objects

### Python — "The Compatibility Engine"
- **Style**: Layered/Service architecture (api → services → domain)
- **Patterns**: Strategy (each conflict scenario), Builder (building a synthetic `Agent`), Monte
  Carlo/Ensemble (N aggregated simulations)
- **Framework**: FastAPI, Pydantic, NumPy (vectorize the batch)
- **Code style**: PEP 8, type hints required, Pydantic as the input/output contract, `black` as the
  formatter

### Communication
- **Async**: RabbitMQ — Java publishes "run simulation", Python consumes and responds
- **Sync**: point-to-point REST for admin panel queries
- **Data**: Database per service (separate Postgres per service, synchronized via events)

---

## 3. Data model (core)

```
User (Java/Postgres)
├── — Layer 1: visible/filter —
│   age, gender, seekingGender, seekingType, city, profession, hobbies[]
├── — Layer 2: hidden simulation parameters —
│   attachmentStyle {anxious|avoidant|secure|disorganized} + intensity
│   communicationProfile: weights toward Gottman's 4 "horsemen"
│   infidelityHistory, relationshipHistory, activeAddiction (never exposed)
│   derivedFromProfession/Age: stressBaseline, commitmentPaceExpectation
└── cumulativeConfidenceScore

Match (Java/Postgres)
├── userAId, userBId, expiryDurationSeconds
├── status: PENDING | ACTIVE | EXPIRED | REJECTED
└── compatibilityScore (comes from Python)

SimulationRun (Python/own Postgres)
├── matchId, simulationsRequested, simulationsSucceeded
├── expiryDistribution[], modelVersion
```

---

## 4. Engine pseudocode (Python)

```python
def run_simulation(agent_a, agent_b, max_days=1000):
    state = RelationshipState(trust=0.6, resentment=0.0, satisfaction=0.5)
    day = 0
    scenarios = [MoneyConflict(), TrustBreach(), ExternalCrisis(), Routine(), NeedForSpace()]

    while day < max_days:
        scenario = pick_next_scenario(scenarios, state, day)
        reaction_a = agent_a.react(scenario, state)
        reaction_b = agent_b.react(scenario, state)
        state = update_state(state, reaction_a, reaction_b)

        if state.resentment >= COLLAPSE_THRESHOLD:
            return SimulationResult(expiry_day=day, outcome="collapsed", final_state=state)

        day += random_interval(agent_a, agent_b)

    return SimulationResult(expiry_day=max_days, outcome="survived", final_state=state)


def run_batch(agent_a, agent_b, n_simulations=10000):
    results = [run_simulation(agent_a, agent_b) for _ in range(n_simulations)]
    compatibility_score = sum(r.outcome == "survived" for r in results) / n_simulations
    expiry_distribution = [r.expiry_day for r in results]
    return CompatibilityReport(compatibility_score, expiry_distribution)
```

---

## 5. Admin panel visualizations

| View | What it shows | Suggested library |
|---|---|---|
| Spaghetti plot | Thousands of trajectories from one pair's simulation | Chart.js / D3 |
| Markov graph | Emotional states + learned transition probabilities | D3 force / react-flow |
| Force-directed graph | Full map of the compound: users as nodes, matches as connections, emergent clusters | D3-force |

---

## 6. Milestones

**M0 — Setup**
- Monorepo, docker-compose (Postgres x2, RabbitMQ), skeleton of the 3 services booting "hello world"

**M1 — Base domain (Java)**
- `User`/`Match` entities, state machine, basic CRUD, no Python yet
- Manual match (endpoint that creates a match between two given IDs)

**M2 — Minimal engine (Python)**
- FastAPI endpoint that receives two profiles and returns a random score + expiry
- Java consumes that endpoint via synchronous REST (still no queues)

**M3 — Real simulation**
- `Agent` model with attachmentStyle/communicationProfile
- Scenarios as Strategy, `update_state` Markov chain
- `run_batch` with NumPy, returns a real expiry distribution

**M4 — Async + events**
- RabbitMQ between Java and Python
- `MatchExpiredEvent` automatically triggers the search for the next candidate (scheduler + domain
  event)
- Collapse threshold based on Gottman's real ratio: 5:1 (positive:negative) in stable
  relationships, ~0.8:1 in at-risk relationships — `collapse_probability(ratio)` interpolates
  between both extremes instead of using a binary cutoff (see section 4)

**M5 — Admin panel**
- The three visualizations (spaghetti, Markov, force-directed)
- Real-time "the System deciding" view

**M6 — Polish**
- Domain tests (Java) and engine tests (Python)
- Architecture documentation, container diagram (C4, optional)

---

## 7. Project structure (monorepo)

```
hang-the-dj-sim/
├── docker-compose.yml
├── README.md
├── java-system/                 # Spring Boot — The System
│   ├── src/main/java/com/system/
│   │   ├── match/                # aggregate + repository + events
│   │   ├── user/
│   │   ├── simulation/           # client toward Python (REST + AMQP)
│   │   ├── config/
│   │   └── SystemApplication.java
│   └── build.gradle
├── python-engine/                # FastAPI — The Engine
│   ├── app/
│   │   ├── api/                  # routers
│   │   ├── services/             # simulation orchestration
│   │   ├── domain/               # agents, scenarios, markov engine
│   │   └── main.py
│   ├── pyproject.toml
│   └── requirements.txt
├── admin-panel/                  # Next.js — visualizations
│   └── (your usual stack: Next.js/Prisma, Achromatic monorepo)
└── docs/
    └── architecture.md
```

---

## 8. Suggestions for Claude Code

### Skills to install/create
- **spring-boot-hexagonal**: package-by-feature, ports/adapters, records for VOs conventions
- **fastapi-monte-carlo**: conventions for NumPy-vectorized simulation services
- **gottman-scenario-writer**: custom skill to generate new conflict scenarios following the
  `Scenario.resolve(state, agent_a, agent_b)` format
- The public docx/pptx skills don't apply here unless you want to generate formal project
  documentation

### Suggested MCPs
- **GitHub MCP**: so Claude Code can open PRs, create issues per milestone
- **Postgres MCP** (one per service, or a shared one pointing at both databases): so Claude can
  inspect real schema/data while debugging
- **Filesystem MCP**: you already have it natively via Claude Code
- **Docker MCP** (if it exists in your registry): to bring the compose stack up/down without
  leaving the chat

### Suggested agents (Claude Code subagents)
- `java-architect`: focused only on `java-system/`, with context on this doc's hexagonal + DDD
- `python-simulation-engineer`: focused on `python-engine/`, with context on the Monte
  Carlo/Markov engine
- `frontend-nextjs`: focused on `admin-panel/`, with context on the 3 visualizations
- `code-reviewer`: cross-cutting agent that checks each PR follows section 2's code styles before
  merging

---

## 9. Suggested commit/PR conventions
- Conventional commits (`feat:`, `fix:`, `refactor:`) per service, with scope:
  `feat(java-system): add match state machine`
- One PR per milestone or per milestone sub-task, never mix `java-system` and `python-engine`
  changes in the same PR

---

## 10. Naming & branding

### Name: 0.8

"Hang the DJ" was ruled out as the final name for being IP from a real episode (Netflix/Charlie
Brooker) — not a good fit if the project leaves the personal repo.

**Why 0.8**: it's not a reference to the show, it's a real research finding (Gottman/Levenson).
Their finding: stable couples maintain a ratio of 5 positive interactions for every negative one
during conflict (5:1); when that ratio drops to ~0.8:1 (negatives nearly match positives), it's a
strong signal of breakup risk. Their lab predicted, with ~90-94% accuracy, which couples would
divorce by observing 15 minutes of conflict conversation.

`0.8` is literally the constant that lives in the simulation engine (`collapse_probability`,
section 4) — the name isn't decorative, it's the number the system computes on every run.

Previous names were dropped for losing the edge of the concept: "Rebellion"/"Defiance" (sold the
feel-good moral of the episode's ending, not the system's surveillance/opacity);
"Sentence"/"Warden"/"Docket" (judicial direction, right in tone but without the real technical
hook of the ratio); "Verity"/"Aggregate" (generic, with no anchor to any concrete data point).

### Logo

Vertical lockup, serif typeface (Georgia/Times):
- The number "0·8" (centered middle dot, not a baseline decimal) rotated 90° as a single unit —
  use `dominant-baseline="central"` when centering before rotating, or the glyph goes off-center
- Negative `letter-spacing` (approx. `-4`) so the characters sit tight against each other
- Below it, the "POINT EIGHT" wordmark in the same serif, small size (~17-18px vs. the number's
  ~90px), positive letter-spacing (~3-4) for contrast, flush against the number with no extra
  gap — the whole lockup should read as a single piece, not as a number + separate slogan
- Alternate variant (not chosen as primary): show the full ratio "0.8:1" instead of just "0.8",
  with a dotted line separating "collapse" from "stable" — more literal, less clean as a wordmark
