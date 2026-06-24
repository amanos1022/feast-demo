from pathlib import Path
from typing import cast
import pandas as pd
from feast import FeatureStore
import xgboost as xgb
from onnxmltools import convert_xgboost
from onnxmltools.convert.common.data_types import FloatTensorType
from shared.config import config

##########################
# Get labeled data
##########################
MINIO = {
    "key": config.aws_access_key_id,
    "secret": config.aws_secret_access_key,
    "client_kwargs": {"endpoint_url": config.s3_endpoint},
}

labels = cast(
    pd.DataFrame,
    pd.read_parquet(f"s3://{config.bucket}/raw/plays.parquet", storage_options=MINIO)[
        ["user_id", "track_id", "event_timestamp", "skipped"]
    ],
).rename(columns={"skipped": "label"})

labels["event_timestamp"] = pd.to_datetime(labels["event_timestamp"], utc=True)
## for feast to do point-in-time join.
labels["request_ts_epoch"] = labels["event_timestamp"].astype("int64") // 10**9
labels = labels.head(10_000)

##########################
# Use Feast SDK to load features for training
##########################
store = FeatureStore(repo_path=str(Path(__file__).resolve().parent.parent / "feast"))
training_df = store.get_historical_features(
    entity_df=labels,
    features=store.get_feature_service("skip_v1"),
).to_df()
training_df["is_weekend"] = training_df["is_weekend"].astype(int)

##########################
# Train and write model
##########################
X = training_df.drop(
    columns=["label", "user_id", "track_id", "event_timestamp", "request_ts_epoch"]
)
y = training_df["label"].astype(int)

model = xgb.XGBClassifier(
    n_estimators=200,
    max_depth=6,
    eval_metric="logloss",
)
model.fit(X.values, y.values)
onnx_model = convert_xgboost(
    model,
    initial_types=[("input", FloatTensorType([None, X.shape[1]]))],
)
with open("model.onnx", "wb") as f:
    f.write(onnx_model.SerializeToString())
