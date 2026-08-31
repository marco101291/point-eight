"""Configuración del servicio, leída del entorno (12-factor)."""

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_prefix="ENGINE_", extra="ignore")

    service_name: str = "el-motor"
    version: str = "0.0.1"
    milestone: str = "M0"

    database_url: str = "postgresql+psycopg://pointeight:pointeight@localhost:5435/pointeight_engine"
    rabbitmq_url: str = "amqp://pointeight:pointeight@localhost:5672/"

    # Constante que le da nombre al proyecto: ratio positivo:negativo de riesgo (Gottman).
    collapse_ratio: float = 0.8
    default_simulations: int = 10_000


settings = Settings()
