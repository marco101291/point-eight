# 0.8 (point-eight) — contexto para Claude Code

Proyecto de **práctica deliberada** de Java + Python. Simulación de matchmaking: un orquestador
institucional (Java/Spring) asigna matches de forma unilateral, apoyado en un motor de
compatibilidad (Python/FastAPI) que corre miles de simulaciones Monte Carlo sobre cadenas de Markov
emocionales. El nombre viene del ratio 0.8:1 positivo:negativo que Gottman/Levenson identificaron
como señal de riesgo de ruptura.

Especificación completa: [`hang-the-dj-sim.md`](hang-the-dj-sim.md).
Decisiones de arquitectura: [`docs/architecture.md`](docs/architecture.md) (`DEC-001` … `DEC-008`).

---

## Lo primero que hay que saber

**El objetivo es aprender Java, no entregar rápido.** Marco viene de Next.js/TypeScript; Java y
Spring son lo nuevo, Next.js no. Eso cambia el criterio de qué es "mejor" código acá: se elige
deliberadamente el camino **más explícito** por sobre el más corto. Ejemplo concreto: los aggregates
son POJOs puros con mappers a entidades JPA separadas, aunque sea el doble de código que anotar el
aggregate directamente (`DEC-004`).

Al explicar código Java, nombrar el patrón y el porqué, no sólo el qué. Ante una bifurcación de
diseño con valor pedagógico, preguntar en vez de decidir en silencio. No asumir familiaridad con
idioms de Spring/JPA (inyección por constructor, `@Transactional`, records como Value Objects); sí
asumirla con React/Next.js.

---

## Estado

| Milestone | Estado | Qué hay |
|---|---|---|
| **M0** Setup | ✅ | Monorepo, compose (Postgres ×2, RabbitMQ), los 3 servicios en línea |
| **M1** Dominio Java | ✅ | Aggregates `User`/`Match`, máquina de estados, CRUD, match manual, domain events. 63 tests |
| **M2** Motor mínimo | ⬜ | FastAPI devuelve score + expiry; Java lo consume por REST síncrono |
| **M3** Simulación real | ⬜ | Agentes, escenarios Strategy, Markov, `run_batch` con NumPy |
| **M4** Async + eventos | ⬜ | RabbitMQ, `MatchExpiredEvent` dispara el próximo match, `collapse_probability` |
| **M5** Admin panel | ⬜ | Spaghetti plot, grafo de Markov, force-directed graph |
| **M6** Pulido | ⬜ | Tests de motor, migración a Flyway, C4 |

Inventario: 59 archivos Java (main), 6 de test, 7 Python, 3 TS.

**El Motor de Python es sólo un esqueleto**: levanta y responde `/api/status`, sin agentes ni
simulación. **El admin panel es sólo un semáforo**: consulta si los dos servicios responden, no lee
usuarios ni matches. `Match.compatibilityScore` siempre vale `null` hasta M2.

---

## Entorno — leer antes de sugerir comandos

- **No hay JDK ni Gradle instalados.** Todo build y test de Java corre en contenedor. Usar
  `./scripts/java-test.sh`; **no** sugerir `gradle` ni `./gradlew` directo. El script pasa
  `--user $(id -u):$(id -g)` porque el contenedor de Gradle, sin eso, deja `build/` y `.gradle/`
  como root y rompe los borrados posteriores.
- **Los puertos publicados salen de `.env`**, y varios no están en el valor "obvio" porque chocaban
  con otros proyectos que corren en la misma máquina (`alivia-postgres` ocupa 5434, un `next-server`
  ocupa 3000). Quedaron: Sistema **8080**, Motor **8000**, panel **3001**, Postgres Sistema **5433**,
  Postgres Motor **5435**, RabbitMQ **5672** / **15672**.
- **El doc de especificación y el código difieren a propósito** en el naming: el doc usa nombres
  provisionales (`hang-the-dj-sim`, `com.system`) y difiere el renombre a M1; se adoptó
  `point-eight` / `com.pointeight` desde M0 (`DEC-001`).
- `.env` no está versionado. Copiar de `.env.example` antes de levantar.

```bash
docker compose up -d --build     # levantar los 6 servicios
docker compose down -v           # bajar y borrar las bases
./scripts/java-test.sh           # 63 tests, sin Spring ni Postgres
```

---

## Reglas de arquitectura

### La regla de dependencias

Package-by-feature en el primer nivel (`user/`, `match/`, `shared/`, `config/`), Ports & Adapters
adentro de cada uno:

```
infrastructure/  ──>  application/  ──>  domain/       nunca al revés
```

`domain/` es **Java puro**: ni una línea de Spring ni de JPA. Es verificable, y si alguna vez
devuelve algo, la arquitectura se rompió:

```bash
grep -rl "jakarta.persistence\|org.springframework" \
  java-system/src/main --include=*.java | grep "/domain/"
```

Los ports (interfaces de repositorio) viven en `domain/`, **no** en `infrastructure/`: el dominio
declara qué necesita y la infraestructura se adapta.

### El invariante que no se negocia: la Capa 2

El `User` tiene dos capas. **Capa 1** (`Profile`) es visible y editable: edad, género, ciudad,
profesión, hobbies. **Capa 2** (`SimulationParameters`) es write-only: estilo de apego, pesos hacia
los cuatro jinetes de Gottman, historial de infidelidad, adicción activa, estrés basal. **Entra por
la API y no sale nunca en ninguna respuesta.**

La garantía es estructural, no una lista de exclusiones: `UserResponse` es un record que sólo
declara campos de Capa 1, así que los parámetros ocultos no tienen dónde entrar. Al agregar
endpoints o DTOs, **mantener esa propiedad** — nunca serializar `SimulationParameters`. `User.toString()`
también la omite a propósito, para que no se filtre por los logs.

Es el centro narrativo del proyecto: el usuario no puede ver ni corregir el modelo que el Sistema
tiene de él.

### Otras invariantes vigentes

- Un match no puede matchear a alguien consigo mismo, ni tener `expiryDuration` ≤ 0.
- Toda la tabla de transiciones vive en `MatchStatus`; el aggregate la consulta antes de mutar.
  `EXPIRED` y `REJECTED` son terminales.
- Un usuario tiene como máximo **un match abierto** (`PENDING` o `ACTIVE`) — `DEC-007`.
- El dominio nunca llama a `Instant.now()`: recibe un `Clock` inyectado, para que los vencimientos
  se testeen con reloj fijo.

---

## Estilo de código

**Java** — Google Java Style, indent 2 espacios, límite 100 columnas. `record` para Value Objects,
con validación en el compact constructor. Inyección por constructor (nunca `@Autowired` en campos).
Excepciones de dominio extienden `DomainException`; el `GlobalExceptionHandler` las traduce a
`ProblemDetail` (404 / 409 / 400).

**Python** — PEP 8, type hints obligatorios, `black` (line-length 100), `mypy --strict`, Pydantic
como contrato de entrada/salida.

**Commits** — Conventional commits con scope por servicio: `feat(java-system): add match state
machine`. Un PR por milestone o sub-tarea; **nunca mezclar `java-system` y `python-engine` en el
mismo PR**.

Comentarios y documentación del proyecto **en español**. Los identificadores de código, en inglés.

---

## Trampas que ya costaron tiempo

- **`next.config.ts` no debe declarar un bloque `env`.** Ese bloque *inlinea* los valores en build
  time, así que hornea las URLs de desarrollo en la imagen y el panel muestra los servicios caídos
  aunque respondan. Las URLs de servicio se leen del entorno en runtime, dentro del server component.
- **El schema lo genera Hibernate** (`ddl-auto: update`, `DEC-008`). No hay migraciones; se migra a
  Flyway en M6. Al cambiar entidades, no escribir SQL a mano.
- **`seekingGenders` es un `Set<Gender>`**, aunque el doc lo escriba en singular (`DEC-005`).

---

## Pendiente de decidir

Registrado al final de `docs/architecture.md`. Lo más relevante para los próximos milestones:
formato del payload Java → Python (M2), si el Motor mantiene réplica de perfiles o los recibe en el
payload (M4), persistencia de `SimulationRun` (M3), quién dispara `activate` sobre un match
`PENDING`, y cómo se ajusta el `cumulativeConfidenceScore` (hoy existe pero nunca se mueve).
