#!/usr/bin/env bash
set -euo pipefail

psql -h "${PG_HOST:-localhost}" -U "${PG_USER:-feast}" -d "${PG_DB:-feast}" -c "
CREATE TABLE IF NOT EXISTS user_stats (
    user_id TEXT, event_timestamp TIMESTAMPTZ,
    user_skip_rate_7d REAL, user_plays_7d BIGINT, user_avg_play_ms REAL
);
CREATE TABLE IF NOT EXISTS track_stats (
    track_id TEXT, event_timestamp TIMESTAMPTZ,
    track_lifetime_plays BIGINT, track_lifetime_skip_rate REAL, genre INT, tempo REAL
);
CREATE TABLE IF NOT EXISTS track_realtime (
    track_id TEXT, event_timestamp TIMESTAMPTZ,
    track_plays_last_1h BIGINT, track_skips_last_1h BIGINT
);
"
