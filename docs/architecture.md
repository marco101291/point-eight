# Arquitectura — 0.8

Documento vivo. La especificación de producto está en [`../hang-the-dj-sim.md`](../hang-the-dj-sim.md);
acá se registran sólo las decisiones de implementación a medida que se toman.

## Contexto (C4 nivel 1–2)

```
        ┌──────────────┐
        │ admin-panel  │  Next.js 15 — sólo presentación
        └──────┬───────┘
               │ REST
        ┌──────▼───────────────┐        REST (M2) / AMQP (M4)      ┌──────────────────┐
        │  java-system         │◄────────────────────────────────► │  python-engine   │
        │  "El Sistema"        │                                   │  "El Motor"      │
        │  Hexagonal + DDD     │                                   │  api→svc→domain  │
        └──────┬───────────────┘                                   └────────┬─────────┘
               │                          ┌──────────┐                      │
        ┌──────▼─────────┐                │ RabbitMQ │             ┌────────▼─────────┐
        │ postgres-system│                └──────────┘             │ postgres-engine  │
        └────────────────┘                                          └─────────────────┘
```

Database per service: ninguno de los dos servicios lee la base del otro. La consistencia
se logra por eventos, no por joins.

## Decisiones tomadas

### DEC-001 — Naming definitivo desde M0
El doc de especificación proponía nombres provisionales (`hang-the-dj-sim`, `com.system`) a
migrar en M1. Se adoptó directamente `point-eight` / `com.pointeight` para evitar un refactor
de paquetes innecesario.

### DEC-002 — Build de Java dentro de Docker
No se versiona el Gradle wrapper; el `Dockerfile` de `java-system` usa la imagen
`gradle:8.10-jdk21` en la etapa de build. Permite trabajar sin JDK ni Gradle instalados.
Si más adelante se quiere build local rápido, agregar el wrapper con `gradle wrapper`.

### DEC-003 — Estructura interna de los paquetes de Java
Package-by-feature en el primer nivel (`match/`, `user/`, `simulation/`, `config/`) y
Ports & Adapters dentro de cada feature (`domain/`, `application/`, `infrastructure/`).
Los paquetes existen desde M0 con `package-info.java`; se pueblan en M1.

### DEC-004 — Dominio puro, separado de JPA
Los aggregates `User` y `Match` son POJOs sin una sola anotación de JPA ni de Spring. La
persistencia vive en `infrastructure/` como `<X>JpaEntity` + `<X>JpaMapper` + `<X>RepositoryAdapter`,
que implementa el port declarado en `domain/`.

Cuesta el doble de código que anotar el aggregate directamente. Se paga porque: (a) es el ejercicio
de Ports & Adapters que plantea el doc, y (b) hace que todo el dominio se teste con JUnit plano —
los 63 tests de M1 corren en segundos sin levantar Spring ni Postgres.

### DEC-005 — La máquina de estados como enum, no como jerarquía de clases
El doc pide el patrón State para el ciclo de vida del match. Se implementa como tabla de
transiciones dentro de `MatchStatus`: cada constante declara a qué estados puede moverse. Toda la
máquina se lee de un vistazo y no se puede agregar una transición sin tocar esa tabla. Una jerarquía
de clases `PendingState`/`ActiveState`/... sería más ceremonia para el mismo comportamiento, dado
que los estados no tienen datos propios.

### DEC-006 — `seekingGenders` es un conjunto, no un valor
El doc lo escribe en singular (`seekingGender`). Se modela como `Set<Gender>` porque el filtro de
Capa 1 de M4 tiene que poder expresar bisexualidad. Es un superconjunto estricto del doc.

### DEC-007 — Un match abierto por usuario
`CreateManualMatchUseCase` rechaza (409) armar un match si alguno de los dos ya tiene uno en
`PENDING` o `ACTIVE`. En el compound cada persona está en una única relación a la vez; sin esta
regla, M4 podría asignarle varios matches simultáneos al mismo usuario.

### DEC-008 — `ddl-auto: update` hasta M6
Hibernate genera y evoluciona el schema. El modelo todavía se mueve mucho (M2 suma el score, M3 el
`SimulationRun`), así que escribir migraciones a mano ahora sería fricción pura. El costo asumido:
el schema no queda versionado y las columnas renombradas quedan huérfanas. Se migra a Flyway en M6.

## Pendiente de decidir

- Formato del payload Java → Python (Factory, sección 2 del doc): JSON plano vs. envelope con
  `modelVersion`. Se define en M2.
- Estrategia de sincronización de perfiles hacia `python-engine`: ¿el payload lleva los perfiles
  completos, o el motor mantiene una réplica propia alimentada por eventos? Se define en M4.
- Persistencia en `python-engine`: SQLAlchemy vs. psycopg directo para `SimulationRun`. Se define en M3.
- Quién dispara `activate` sobre un match `PENDING`: hoy es manual. En M4 el scheduler debería
  activarlo apenas se asigna, o dejar `PENDING` como ventana de preparación con su propio timeout.
- `cumulativeConfidenceScore` existe pero nunca se mueve: falta definir cómo lo ajusta el resultado
  de un match. Depende de tener predicciones reales del Motor (M3).
