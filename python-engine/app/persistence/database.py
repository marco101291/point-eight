"""Async SQLAlchemy engine and session factory."""

from collections.abc import AsyncGenerator
from contextlib import asynccontextmanager

from sqlalchemy.ext.asyncio import AsyncSession, async_sessionmaker, create_async_engine

from app.config import settings
from app.persistence.models import Base

engine = create_async_engine(settings.database_url)
async_session_factory = async_sessionmaker(engine, expire_on_commit=False)


async def init_models() -> None:
    """Creates tables if they don't exist yet — mirrors java-system's `ddl-auto: update` (DEC-008):
    no migrations until M6, the model still moves too much to justify hand-written ones."""
    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)


@asynccontextmanager
async def get_session() -> AsyncGenerator[AsyncSession]:
    async with async_session_factory() as session:
        yield session
