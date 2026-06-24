# This entire file is a declaration of the feast schema and service. Serving is done by `feast serve`.
from datetime import timedelta
from feast import (
    Entity,
    FeatureView,
    Field,
    PushSource,
    FeatureService,
    RequestSource,
)
from feast.infra.offline_stores.contrib.postgres_offline_store.postgres_source import (
    PostgreSQLSource,
)
from feast.on_demand_feature_view import on_demand_feature_view
from feast.types import Int32, Int64, Float32, String, Bool
from shared.config import config

user = Entity(name="user", join_keys=["user_id"])
track = Entity(name="track", join_keys=["track_id"])

# Configure offline store for sourcing features
user_src = PostgreSQLSource(
    name="user_stats_source",
    query="SELECT * FROM user_stats",
    timestamp_field="event_timestamp",
)

track_src = PostgreSQLSource(
    name="track_stats_source",
    query="SELECT * FROM track_stats",
    timestamp_field="event_timestamp",
)

track_real_time_batch = PostgreSQLSource(
    name="track_realtime_source",
    query="SELECT * FROM track_realtime",
    timestamp_field="event_timestamp",
)

# FeatureView, essentially configure offline and online store "table"
user_stats = FeatureView(
    name="user_stats",
    entities=[user],
    ttl=timedelta(
        days=2
    ),  # for redis when you materialize., also Feast joins T_e - ttl <= event_timestamp <= T_e
    source=user_src,
    schema=[
        Field(name="user_skip_rate_7d", dtype=Float32),
        Field(name="user_plays_7d", dtype=Int64),
        Field(name="user_avg_play_ms", dtype=Float32),
    ],
)

track_stats = FeatureView(
    name="track_stats",
    entities=[track],
    ttl=timedelta(days=2),
    source=track_src,
    schema=[
        Field(name="track_lifetime_plays", dtype=Int64),
        Field(name="track_lifetime_skip_rate", dtype=Float32),
        Field(name="genre", dtype=Int32),
        Field(name="tempo", dtype=Float32),
    ],
)


# Pushed to online store configured in online store.
track_real_time_push = PushSource(
    name="track_realtime_push",
    batch_source=track_real_time_batch,  # offline fall back
)

track_realtime = FeatureView(
    name="track_realtime",
    entities=[track],
    ttl=timedelta(hours=2),
    source=track_real_time_push,
    schema=[
        Field(name="track_plays_last_1h", dtype=Int64),
        Field(name="track_skips_last_1h", dtype=Int64),
    ],
)


req = RequestSource(
    name="req",
    schema=[Field(name="request_ts_epoch", dtype=Int64)],  # how feast serve knows input
)


# compute this feature at request time, e.g. the timestamp.
# takes raw request_ts_epoch and derives hour of day and is_weekend
@on_demand_feature_view(
    sources=[req],
    schema=[
        Field(name="hour_of_day", dtype=Int32),
        Field(name="is_weekend", dtype=Bool),
    ],
)
def request_features(inp):
    import pandas as pd

    out = pd.DataFrame()
    ts = pd.to_datetime(inp["request_ts_epoch"], unit="s", utc=True)

    out["hour_of_day"] = ts.dt.hour
    out["is_weekend"] = ts.dt.dayofweek >= 5

    return out


# At serving time, you'll pass a user_id, track_id, and request_ts_epoch
# Feast looks up all four views, joins results during read, and returns one feature vector.

# user_id, track_id, come in as well as hour_of_day and is_weekend request context.

# This is defining this:
# (user_id, track_id, request_ts_epoch) -> {
#   user_skip_rate_7d,
#   user_plays_7d,
#   user_avg_play_ms,

#   track_lifetime_plays,
#   track_lifetime_skip_rate,
#   genre,
#   tempo,

#   track_plays_last_1h,
#   track_skips_last_1h,

#   hour_of_day,
#   is_weekend
# }
# One user and one track. Real systems would do batch lookups/responses.

# declare "skip_v1" service. `feast serve` actually serves the service.
skip_svc = FeatureService(
    name="skip_v1", features=[user_stats, track_stats, track_realtime, request_features]
)
