import os
from dataclasses import dataclass


@dataclass(frozen=True)
class Config:
    postgres_user: str
    postgres_password: str
    postgres_host: str
    postgres_port: int

    redis_host: str
    redis_port: int

    s3_endpoint: str
    bucket: str

    aws_access_key_id: str
    aws_secret_access_key: str
    aws_default_region: str


def env_int(name: str, default: int) -> int:
    value = os.environ.get(name)

    if value is None or value == "":
        return default

    return int(value)


config = Config(
    postgres_user=os.environ.get("PG_USER", "feast"),
    postgres_password=os.environ.get("PG_PASS", "feast"),
    postgres_host=os.environ.get("PG_HOST", "localhost"),
    postgres_port=env_int("PG_PORT", 5432),
    redis_host=os.environ.get("REDIS_HOST", "localhost"),
    redis_port=env_int("REDIS_PORT", 6379),
    s3_endpoint=os.environ.get("AWS_ENDPOINT_URL", "http://localhost:9000"),
    bucket=os.environ.get("FEAST_BUCKET", "feast"),
    aws_access_key_id=os.environ.get("AWS_ACCESS_KEY_ID", "minio"),
    aws_secret_access_key=os.environ.get("AWS_SECRET_ACCESS_KEY", "minio12345"),
    aws_default_region=os.environ.get("AWS_DEFAULT_REGION", "us-east-1"),
)

os.environ.setdefault("AWS_ENDPOINT_URL", config.s3_endpoint)
os.environ.setdefault("AWS_ACCESS_KEY_ID", config.aws_access_key_id)
os.environ.setdefault("AWS_SECRET_ACCESS_KEY", config.aws_secret_access_key)
os.environ.setdefault("AWS_DEFAULT_REGION", config.aws_default_region)
