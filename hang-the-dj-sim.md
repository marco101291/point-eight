# 0.8 — Simulación de matchmaking (proyecto de práctica: Java + Python)

Proyecto de práctica inspirado en el episodio "Hang the DJ" (Black Mirror), con fidelidad al canon pero con un algoritmo de compatibilidad basado en literatura real (apego, comunicación bajo conflicto, no similitud de perfil). Objetivo: practicar Java y Python en paralelo, cada uno con un rol arquitectónico distinto.

Nombre del proyecto: **0.8** (ver sección 10 — Naming & branding). Los nombres de carpetas/paquetes usados en este documento (`hang-the-dj-sim`, `com.system`) son provisionales y deberían migrarse a `point-eight` / `com.pointeight` cuando arranque M1.

---

## 1. Visión general del sistema

| Componente | Rol narrativo | Rol técnico | Lenguaje |
|---|---|---|---|
| El Sistema | Autoridad institucional, orquesta el compound | Orquestador, estado, reglas de negocio, API | Java (Spring Boot) |
| El Motor de Compatibilidad | La simulación oculta que corre el sistema | Motor de cómputo Monte Carlo + Markov | Python (FastAPI) |
| El Coaster / Frontend | La interfaz que ve el usuario | UI, sin lógica de negocio | Next.js (tu stack habitual) |

No hay swipe ni elección de perfiles: el Sistema asigna el siguiente match de forma unilateral cuando expira el anterior, disparado por evento.

---

## 2. Arquitectura por servicio

### Java — "El Sistema" (orquestador)
- **Estilo**: Arquitectura Hexagonal (Ports & Adapters) + DDD táctico
- **Aggregates**: `Match`, `User`
- **Value Objects**: `MatchStatus`, `CompatibilityScore`
- **Domain Events**: `MatchExpiredEvent`, `MatchAssignedEvent`
- **Patrones**: State (ciclo de vida del match), Strategy (selección de candidato), Specification (filtros de Capa 1), Factory (payload hacia Python)
- **Framework**: Spring Boot 3, Spring Data JPA, Spring AMQP (RabbitMQ), Spring Events
- **Estilo de código**: Google Java Style Guide, package-by-feature (`match/`, `user/`, `simulation/`), records para Value Objects

### Python — "El Motor de Compatibilidad"
- **Estilo**: Layered/Service architecture (api → services → domain)
- **Patrones**: Strategy (cada escenario de conflicto), Builder (construcción de `Agent` sintético), Monte Carlo/Ensemble (N simulaciones agregadas)
- **Framework**: FastAPI, Pydantic, NumPy (vectorizar el batch)
- **Estilo de código**: PEP 8, type hints obligatorios, Pydantic como contrato de entrada/salida, `black` como formateador

### Comunicación
- **Async**: RabbitMQ — Java publica "correr simulación", Python consume y responde
- **Sync**: REST puntual para consultas del admin panel
- **Datos**: Database per service (Postgres separado por servicio, sincronizado por eventos)

---

## 3. Modelo de datos (núcleo)

```
User (Java/Postgres)
├── — Capa 1: visible/filtro —
│   age, gender, seekingGender, seekingType, city, profession, hobbies[]
├── — Capa 2: parámetros ocultos de simulación —
│   attachmentStyle {anxious|avoidant|secure|disorganized} + intensidad
│   communicationProfile: pesos hacia los 4 "jinetes" de Gottman
│   infidelityHistory, relationshipHistory, activeAddiction (nunca expuestos)
│   derivedFromProfession/Age: stressBaseline, commitmentPaceExpectation
└── cumulativeConfidenceScore

Match (Java/Postgres)
├── userAId, userBId, expiryDurationSeconds
├── status: PENDING | ACTIVE | EXPIRED | REJECTED
└── compatibilityScore (viene de Python)

SimulationRun (Python/Postgres propio)
├── matchId, simulationsRequested, simulationsSucceeded
├── expiryDistribution[], modelVersion
```

---

## 4. Pseudocódigo del motor (Python)

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

## 5. Visualizaciones del admin panel

| Vista | Qué muestra | Librería sugerida |
|---|---|---|
| Spaghetti plot | Miles de trayectorias de una simulación por par | Chart.js / D3 |
| Grafo de Markov | Estados emocionales + probabilidades de transición aprendidas | D3 force / react-flow |
| Force-directed graph | Mapa completo del compound: usuarios como nodos, matches como conexiones, clusters emergentes | D3-force |

---

## 6. Milestones

**M0 — Setup**
- Monorepo, docker-compose (Postgres x2, RabbitMQ), esqueleto de los 3 servicios levantando "hello world"

**M1 — Dominio base (Java)**
- Entidades `User`/`Match`, máquina de estados, CRUD básico, sin Python aún
- Match manual (endpoint que crea un match entre dos IDs dados)

**M2 — Motor mínimo (Python)**
- Endpoint FastAPI que recibe dos perfiles y devuelve un score aleatorio + expiry
- Java consume ese endpoint vía REST síncrono (aún sin colas)

**M3 — Simulación real**
- Modelo de `Agent` con attachmentStyle/communicationProfile
- Escenarios como Strategy, cadena de Markov de `update_state`
- `run_batch` con NumPy, devuelve distribución real de expiries

**M4 — Async + eventos**
- RabbitMQ entre Java y Python
- `MatchExpiredEvent` dispara automáticamente la búsqueda del siguiente candidato (scheduler + domain event)
- Umbral de colapso basado en el ratio real de Gottman: 5:1 (positivo:negativo) en relaciones estables, ~0.8:1 en relaciones en riesgo — `collapse_probability(ratio)` interpola entre ambos extremos en vez de usar un corte binario (ver sección 4)

**M5 — Admin panel**
- Las tres visualizaciones (spaghetti, Markov, force-directed)
- Vista de "el Sistema decidiendo" en tiempo real

**M6 — Pulido**
- Tests de dominio (Java) y de motor (Python)
- Documentación de arquitectura, diagrama de contenedores (C4 opcional)

---

## 7. Estructura del proyecto (monorepo)

```
hang-the-dj-sim/
├── docker-compose.yml
├── README.md
├── java-system/                 # Spring Boot — El Sistema
│   ├── src/main/java/com/system/
│   │   ├── match/                # aggregate + repository + events
│   │   ├── user/
│   │   ├── simulation/           # cliente hacia Python (REST + AMQP)
│   │   ├── config/
│   │   └── SystemApplication.java
│   └── build.gradle
├── python-engine/                # FastAPI — El Motor
│   ├── app/
│   │   ├── api/                  # routers
│   │   ├── services/             # orquestación de simulación
│   │   ├── domain/               # agentes, escenarios, markov engine
│   │   └── main.py
│   ├── pyproject.toml
│   └── requirements.txt
├── admin-panel/                  # Next.js — visualizaciones
│   └── (tu stack habitual: Next.js/Prisma, monorepo Achromatic)
└── docs/
    └── architecture.md
```

---

## 8. Sugerencias para Claude Code

### Skills a instalar/crear
- **spring-boot-hexagonal**: convenciones de package-by-feature, ports/adapters, records para VOs
- **fastapi-monte-carlo**: convenciones para servicios de simulación vectorizados con NumPy
- **gottman-scenario-writer**: skill custom para generar nuevos escenarios de conflicto siguiendo el formato `Scenario.resolve(state, agent_a, agent_b)`
- Las skills públicas de docx/pptx no aplican aquí salvo que quieras generar documentación formal del proyecto

### MCPs sugeridos
- **GitHub MCP**: para que Claude Code abra PRs, cree issues por milestone
- **Postgres MCP** (uno por servicio, o uno compartido apuntando a ambas bases): para que Claude pueda inspeccionar schema/datos reales al debuggear
- **Filesystem MCP**: ya lo tenés vía Claude Code nativo
- **Docker MCP** (si existe en tu registro): para levantar/bajar el compose sin salir del chat

### Agentes sugeridos (subagents de Claude Code)
- `java-architect`: enfocado solo en `java-system/`, con contexto del hexagonal + DDD de este doc
- `python-simulation-engineer`: enfocado en `python-engine/`, con contexto del motor Monte Carlo/Markov
- `frontend-nextjs`: enfocado en `admin-panel/`, con contexto de las 3 visualizaciones
- `code-reviewer`: agente transversal que valida que cada PR respete los estilos de código de la sección 2 antes de mergear

---

## 9. Convenciones de commits/PRs sugeridas
- Conventional commits (`feat:`, `fix:`, `refactor:`) por servicio, con scope: `feat(java-system): add match state machine`
- Un PR por milestone o por sub-tarea de milestone, nunca mezclar cambios de `java-system` y `python-engine` en el mismo PR

---

## 10. Naming & branding

### Nombre: 0.8

Descartado "Hang the DJ" como nombre final por ser IP de un episodio real (Netflix/Charlie Brooker) — no conviene si el proyecto sale del repo personal.

**Por qué 0.8**: no es una referencia al show, es un dato real de investigación (Gottman/Levenson). Su hallazgo: parejas estables mantienen un ratio de 5 interacciones positivas por cada negativa durante el conflicto (5:1); cuando ese ratio cae a ~0.8:1 (las negativas casi igualan a las positivas), es señal fuerte de riesgo de ruptura. Su laboratorio predijo con ~90-94% de precisión, observando 15 minutos de conversación de conflicto, qué parejas se divorciarían.

`0.8` es literalmente la constante que vive en el motor de simulación (`collapse_probability`, sección 4) — el nombre no es decorativo, es el número que el sistema calcula en cada corrida.

Se descartaron nombres previos por perder el filo del concepto: "Rebellion"/"Defiance" (vendían la moraleja feel-good del final del episodio, no la vigilancia/opacidad del sistema); "Sentence"/"Warden"/"Docket" (dirección judicial, correcta en tono pero sin el gancho técnico real del ratio); "Verity"/"Aggregate" (genéricos, sin anclaje a ningún dato concreto).

### Logo

Lockup vertical, tipografía serif (Georgia/Times):
- Número "0·8" (punto medio centrado, no decimal bajo) rotado 90° como una sola unidad — usar `dominant-baseline="central"` al centrar antes de rotar, o el glyph se descuadra
- `letter-spacing` negativo (aprox. `-4`) para que los caracteres queden apretados entre sí
- Debajo, wordmark "POINT EIGHT" en la misma serif, tamaño pequeño (~17-18px vs ~90px del número), letter-spacing positivo (~3-4) para contraste, pegado al número sin espacio de sobra — todo el lockup debe leerse como una sola pieza, no como número + slogan separado
- Variante alterna (no elegida como principal): mostrar el ratio completo "0.8:1" en vez de solo "0.8", con línea punteada separando "colapso" de "estable" — más literal, menos limpia como wordmark
