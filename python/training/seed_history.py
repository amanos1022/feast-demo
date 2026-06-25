import pandas as pd, numpy as np, s3fs, uuid
from datetime import datetime, timedelta, timezone
from shared.config import config

N_USERS = 20
N_TRACKS = 500
N_EVENTS = 2_000_000

rng = np.random.default_rng(42)
now = datetime.now(timezone.utc)

track_ids = [f"t_{i:04d}" for i in range(N_TRACKS)]
skip_prior = {t: 0.2 + 0.6 * (abs(hash(t) % 100) / 100) for t in track_ids}
ts = now - pd.to_timedelta(rng.integers(0, 60 * 24 * 3600, N_EVENTS), unit="s")
tr = rng.choice(track_ids, N_EVENTS)
skipped = rng.random(N_EVENTS) < np.array([skip_prior[t] for t in tr])
track_lens = 120_000 + rng.integers(0, 180_000, N_EVENTS)
played_ms = np.where(
    skipped,
    rng.integers(0, 30_000, N_EVENTS),
    30_000 + rng.integers(0, track_lens - 30_000),
)
event_ids = [str(uuid.uuid4()) for _ in range(N_EVENTS)]
user_ids = [f"u_{i:04d}" for i in rng.integers(0, N_USERS, N_EVENTS)]
genre = [
    ["synthwave", "jazz", "techno", "ambient", "pop"][abs(hash(t)) % 5] for t in tr
]

df = pd.DataFrame(
    {
        "event_id": event_ids,
        "user_id": user_ids,
        "track_id": tr,
        "genre": genre,
        "event_timestamp": ts,
        "played_ms": played_ms,
        "track_len_ms": track_lens,
        "skipped": skipped,
        "device": rng.choice(["android", "ios"], N_EVENTS),
    }
)

fs = s3fs.S3FileSystem(
    key=config.aws_access_key_id,
    secret=config.aws_secret_access_key,
    client_kwargs={"endpoint_url": config.s3_endpoint},
)
df.to_parquet(f"s3://{config.bucket}/raw/plays.parquet", filesystem=fs, index=False)
print("seeded", len(df), "events")
