"""Service configuration, read from the environment (12-factor)."""

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_prefix="ENGINE_", extra="ignore")

    service_name: str = "el-motor"
    version: str = "0.0.1"
    milestone: str = "M3"

    database_url: str = (
        "postgresql+psycopg://pointeight:pointeight@localhost:5435/pointeight_engine"
    )
    rabbitmq_url: str = "amqp://pointeight:pointeight@localhost:5672/"

    # The constant that gives the project its name: positive:negative risk ratio (Gottman).
    collapse_ratio: float = 0.8

    # The doc cites 10,000 as the canonical Monte Carlo batch size, but `run_batch` is a plain
    # Python loop (DEC-012) and Java calls this endpoint synchronously (still true until M4's
    # RabbitMQ decouples them) — 10,000 sequential simulations measured ~8.5s per request, long
    # enough to risk a client-side timeout. 1,000 keeps the same request under ~1s.
    default_simulations: int = 1_000


settings = Settings()
