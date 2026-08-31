# 0.8 (Point Eight)

Simulación de matchmaking: un orquestador institucional (Java) que asigna matches de forma
unilateral, apoyado en un motor de compatibilidad (Python) que corre miles de simulaciones
Monte Carlo sobre cadenas de Markov emocionales.

El nombre viene del ratio 0.8:1 (positivo:negativo) que Gottman/Levenson identificaron como
señal de riesgo de ruptura. Es la constante que vive en `collapse_probability`.

Especificación completa: [`hang-the-dj-sim.md`](hang-the-dj-sim.md).

## Estado — M1 (Dominio base) ✅

El Sistema ya tiene dominio real: aggregates `User` y `Match`, la máquina de estados del match,
CRUD de usuarios y creación de match manual. Todavía sin Python — `compatibilityScore` llega en M2.

| Servicio | Rol | Stack | Puerto |
|---|---|---|---|
| `java-system` | El Sistema — orquestador, estado, reglas | Spring Boot 3 / Java 21 | 8080 |
| `python-engine` | El Motor de Compatibilidad | FastAPI / Python 3.12 | 8000 |
| `admin-panel` | El Coaster — UI, sin lógica de negocio | Next.js 15 | 3001 |
| `postgres-system` | DB de El Sistema | Postgres 16 | 5433 |
| `postgres-engine` | DB de El Motor | Postgres 16 | 5435 |
| `rabbitmq` | Bus async (se usa desde M4) | RabbitMQ 3.13 | 5672 / 15672 |

## Arrancar

```bash
cp .env.example .env      # ajustá puertos/credenciales si hace falta
docker compose up --build
```

La primera build de `java-system` descarga el toolchain de Gradle y tarda varios minutos.
No hace falta tener Java ni Gradle instalados: el build corre dentro del contenedor.

Todos los puertos publicados salen de `.env`, así que si chocan con otro proyecto se cambian ahí
sin tocar el compose.

Verificación:

```bash
curl localhost:8080/api/status     # El Sistema
curl localhost:8000/api/status     # El Motor
open http://localhost:3001         # Admin panel (muestra el estado de ambos)
open http://localhost:15672        # RabbitMQ management (pointeight / pointeight)
```

## La API de El Sistema

```
POST   /api/users                 alta (Capa 2 opcional y write-only)
GET    /api/users?page=&size=     listado paginado
GET    /api/users/{id}
PATCH  /api/users/{id}            edita sólo Capa 1
DELETE /api/users/{id}

POST   /api/matches               { userAId, userBId, expiryDurationSeconds? }
GET    /api/matches?status=&page= listado, filtrable por estado
GET    /api/matches/{id}
POST   /api/matches/{id}/activate
POST   /api/matches/{id}/expire
POST   /api/matches/{id}/reject
```

### Máquina de estados del match

```
PENDING ──activate──> ACTIVE ──expire──> EXPIRED  (terminal)
   │                    │
   └───reject───> REJECTED <──reject────┘         (terminal)
```

Toda la tabla de transiciones vive en `MatchStatus`. Una transición ilegal responde **409** con un
`ProblemDetail` que incluye `from`, `to` y `allowedTransitions`.

### Las dos capas del usuario

El modelo separa lo que el usuario ve de lo que el Sistema sabe:

- **Capa 1** (`Profile`) — edad, género, ciudad, profesión, hobbies. Visible y editable.
- **Capa 2** (`SimulationParameters`) — estilo de apego, pesos hacia los cuatro jinetes de Gottman,
  historial de infidelidad, adicción activa, estrés basal. **Entra por la API y no sale nunca.**

La garantía es estructural: `UserResponse` es un record que sólo declara campos de Capa 1, así que
no hay filtro que mantener ni lista de exclusiones que se pueda olvidar. Si el alta no aporta Capa 2,
`TraitDerivation` la infiere del perfil — el usuario nunca elige con qué parámetros lo simulan.

## Tests

```bash
./scripts/java-test.sh            # 63 tests de dominio, sin Spring ni Postgres
```

Corren en un contenedor Gradle (no hace falta JDK local) con el UID del host, así que no dejan
archivos de root en el árbol.

## Desarrollo por servicio

**Java** — todo dentro de Docker, o con un JDK 21 local:
```bash
cd java-system && gradle bootRun
```
Arquitectura: package-by-feature (`user/`, `match/`, `shared/`, `config/`) con Ports & Adapters
adentro — `domain/` (POJOs puros, sin JPA), `application/` (casos de uso), `infrastructure/`
(entities, mappers, controllers). Ver `docs/architecture.md`.

**Python** — entorno virtual local:
```bash
cd python-engine
python3 -m venv .venv && source .venv/bin/activate
pip install -r requirements-dev.txt
uvicorn app.main:app --reload
black app && mypy app
```
Docs interactivas en http://localhost:8000/docs.

**Next.js**:
```bash
cd admin-panel && npm install && npm run dev
```

## Convenciones

- Conventional commits con scope por servicio: `feat(java-system): add match state machine`
- Un PR por milestone o sub-tarea; nunca mezclar `java-system` y `python-engine` en el mismo PR
- Java: Google Java Style, package-by-feature, records para Value Objects
- Python: PEP 8, type hints obligatorios, `black`, Pydantic como contrato de I/O

## Roadmap

- [x] **M0** — Setup: monorepo, compose, tres servicios en línea
- [x] **M1** — Dominio base en Java: `User`/`Match`, máquina de estados, match manual
- [ ] **M2** — Motor mínimo: FastAPI devuelve score + expiry, Java lo consume por REST
- [ ] **M3** — Simulación real: agentes, escenarios Strategy, Markov, `run_batch` con NumPy
- [ ] **M4** — Async: RabbitMQ, `MatchExpiredEvent`, `collapse_probability` interpolando 5:1 → 0.8:1
- [ ] **M5** — Admin panel: spaghetti plot, grafo de Markov, force-directed graph
- [ ] **M6** — Pulido: tests de dominio y motor, documentación de arquitectura
