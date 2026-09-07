"""Shared fixtures for tests that need a real database.

A real, ephemeral Postgres via testcontainers, not a mock session or a different dialect
(SQLite): the insights endpoints build real SQL (GROUP BY, selectinload, ORDER BY + LIMIT), which
only a real Postgres actually exercises the same way production does.
"""

from collections.abc import AsyncGenerator, Iterator

import pytest
import pytest_asyncio
from httpx import ASGITransport, AsyncClient
from sqlalchemy.ext.asyncio import (
    AsyncEngine,
    AsyncSession,
    async_sessionmaker,
    create_async_engine,
)
from testcontainers.community.postgres import PostgresContainer

from app.api.insights import get_db_session
from app.main import app
from app.persistence.models import Base


@pytest.fixture(scope="session")
def postgres_url() -> Iterator[str]:
    # Same image docker-compose.yml uses for postgres-engine, for the same reason as the dialect
    # choice above: minimize drift between what tests run against and what production runs on.
    with PostgresContainer("postgres:16-alpine", driver="psycopg") as postgres:
        yield postgres.get_connection_url()


@pytest_asyncio.fixture(scope="session")
async def engine(postgres_url: str) -> AsyncGenerator[AsyncEngine]:
    async_engine = create_async_engine(postgres_url)
    async with async_engine.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)
    yield async_engine
    await async_engine.dispose()


@pytest_asyncio.fixture
async def db_session(engine: AsyncEngine) -> AsyncGenerator[AsyncSession]:
    session_factory = async_sessionmaker(engine, expire_on_commit=False)
    async with session_factory() as session:
        yield session
    # Truncate rather than drop/recreate: cheap for the handful of rows a test seeds, and keeps
    # the one container (and its schema) alive for the whole session instead of paying container
    # startup cost per test. Reverse dependency order so a child row is never left orphaned mid-way.
    async with engine.begin() as conn:
        for table in reversed(Base.metadata.sorted_tables):
            await conn.execute(table.delete())


@pytest_asyncio.fixture
async def client(db_session: AsyncSession) -> AsyncGenerator[AsyncClient]:
    async def override_get_db_session() -> AsyncGenerator[AsyncSession]:
        yield db_session

    # Not app.main's own lifespan: that also connects to RabbitMQ (CompatibilityScoreConsumer),
    # which these HTTP-only tests have no need for, and ASGITransport never triggers it anyway.
    app.dependency_overrides[get_db_session] = override_get_db_session
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as ac:
        yield ac
    app.dependency_overrides.clear()
