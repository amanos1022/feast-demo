
# Using Feast and Flink for storing ML features
In this repo, we demonstrate the importance of a feature store - in this case Feast. We generate synthetic data and use it to train and serve a model for making a prediction: will a user with a particular `user_id` skip a music track with a particular `track_id`? Feast adds a framework around the process of consistently providing features at model training time and inference time. 

Additionally, we demonstrate how to create a Flink stream processing job that takes messages being produced to a Kafka topic, aggregates them, and pushes them to Feast which then loads them into the online store - in this case Redis.

## Quick Start
This repo is meant to be batteries included. All you need is [Nix package manager](https://nixos.org/) and [direnv](https://direnv.net/) installed on your system. From the root of this repo you can run the following to get the full flow running.

```bash
direnv allow

# Deploy local K8s cluster with all resources
kind create cluster --name music-fs --image kindest/node:v1.33.1
cd terraform && terraform init -reconfigure && terraform apply
cd ..
kubectl apply -f k8s/postgres.yaml -f k8s/redis.yaml -f k8s/kafka-cluster.yaml

# wait for all resources to be come ready
kubectl apply -f k8s/kafka-connect-s3-sink.yaml
./scripts/port-forward.sh &
./scripts/init-tables.sh

# Seed synthetic raw events
cd python/training && uv sync && .venv/bin/python seed_history.py

# Feast schema creation
cd ../feast && uv sync && .venv/bin/feast apply

# Run feast registry server (batch jobs need this for schema validation)
.venv/bin/feast serve_registry --rest-api --rest-port 8000 &

# Batch job processors
cd ../../java && ./gradlew :batch:run

# Materialize features to online store (Redis)
cd ../feast && .venv/bin/python materialize.py materialize 2024-06-01T00:00:00 2024-06-15T00:00:00

# Train the model
cd ../training && .venv/bin/python train.py 

# Run feast feature server, inference server, producer, and Flink stream processing job
cd ../feast && .venv/bin/feast serve &
cd ../../java && ./gradlew serving:run &
./gradlew producer:run &
./gradlew stream:run &

curl -s -X POST http://localhost:8080/predict \
    -H "Content-Type: application/json" \
    -d '{"requestId":"test-1","userId":"u_0002","trackId":"t_0126"}'
```

## Why Feast?
Feast serves two main purposes:
1. Feature consistency. Feast is a centralized schema declaration for your ML model's features. This provides a contract for anything using the features - e.g. training jobs, inference servers, batch processing jobs, and streaming processing jobs.
2. Point-in-time feature querying framework. This is a critically important thing to get right when training ML models.

Feast achieves these goals by providing python and java SDKs as well as several jobs and servers in the form of `feast <command>` commands.

Before we go deeper into feast, let's set some context.

## What the model does
The purpose of our model is to predict how likely a user is to skip a track based on the following:
- The user's engagement history.
- The track's engagement history.
- The time of day.
- If it's a weekday or weekend.

For user, it looks at the following:
  - Skip rate for last 7 days: numTracksSkipped / numTracksPlayed 
  - Track plays last 7 days: Total number of tracks played in the last 7 days.
  - Average play time: average of track play time over the last 7 days.

For track, it looks at:
  - Genre
  - Tempo
  - Life time plays: total sum of plays over all historical data.
  - Life time skip rate: totalSkips / totalPlays 
  - Plays in the last hour: sum of this track's plays in the last hour.
  - Skips in the last hour: sum of this track's skips in the last hour.

The model also requires date-time data:
  - Hour of day.
  - If it's a weekend day or week day.

We inference the model via a REST HTTP endpoint which takes in the `user_id` and `track_id`. It uses those ids, along with a timestamp generated at request time, to get the aforementioned features of the specified track and user as well as the hour of the day and if it's a weekend or not. The model needs all of these features - 11 of them in total - to make its prediction. The prediction is returned in the form of a 0 to 1 probability that attempts to answer the question "will the user skip this track."

## Data model and Feature engineering 
### Data model
User
- user_id
- ... (non-germane fields)

Track
- track_id
- genre
- tempo

Play event
- event_id
- user_id
- track_id
- genre
- event_timestamp
- played_ms
- track_len_ms
- skipped (boolean)
- device (ios|android)
- tempo

### Features
Organized by Feast's `FeatureView`

#### User stats
- user_skip_rate_7d
- user_plays_7d
- user_avg_play_ms

#### Track stats
- track_lifetime_plays
- track_lifetime_skip_rate
- genre (pass through)
- tempo (pass through)

#### Track realtime
- track_plays_last_1h
- track_skips_last_1h

#### On Demand (computed at request time)
- hour_of_day
- is_weekend

> In this demo, `Track` and `Play` are not stored so they do not have a schema or DB migrations. Everything we need for model training and serving comes from seeded play events and messages produced to the Kafka event bus.
 
## Feast
We're using feast as our feature store. Feast solves two problems regarding model serving: feature consistency at training and inference time and point-in-time feature correctness at model training time. You use it to provide a schema that serves as a shared contract for model training, inferencing, and batch/streaming jobs that ELT raw events into an offline feature store.

### Problems it solves
#### Feature consistency
Feast allows you to define a feature schema in the form of `Entity`s, `FeatureView`s, and `PushSource`s. A `PushSource` allows you to push specified features directly to a store (typically your online store) via a stream processor. This would be utilized for features that we want updated at a high frequency like our `track_plays_last_1h` feature.

The schema declaration makes Feast the single source of truth for obtaining metadata about the structure of your features. We then also use Feast to confidently query feature data for model training and inference.

#### Point-in-time correctness (as-of join)
During training, you don't want to accidentally leak the future, or your model will perform well at training time but will perform poorly in production, and you'll be left scratching your head as to why. In our example, we're predicting track skip probability. One of the features we use is `track_lifetime_skip_rate`. In training, during an iteration imagine we're on an event i.e. training row `X[i]` - we don't want the latest `track_lifetime_skip_rate`, i.e. training row `X[n]`'s skip rate; we want the skip rate _at the time the event we're training on happened._ To be clear, this point in time correctness is a consequence of the dataset construction - it is NOT determined in the training loop. While constructing the training data (i.e. `get_historical_features`), Feast joins features where `timestamp <= event_timestamp`. If it were to use feature data from a later timestamp, the model would be seeing into the future at training time, producing an awesome AUC, but performing like crap in production. This is an example of feature leakage.

### Feast in practice
Looking at the `python/feast` directory you'll see two important files:

- `feature_store.yaml`: Consists of three important data-store declarations:
    - `registry` (Postgres): This is the persistent store where feature schema metadata lives.
    - `online_store` (Redis): This is where online feature cache lives. Feast reads here at inference time and writes here when `materialize*` commands are run and when feast `PushSource` is "pushed" to. All 9 non-on-demand features are stored here. The track realtime features are stored here at near real-time - Flink pushes to Feast which updates the online store. The other features, `user_stats` and `track_stats`, are updated by `feast materialize-incremental` job run on a daily schedule.
    - `offline_store` (Postgres): This is where feast reads features from when `materialize*` commands are run (explained below).
- `features.py`: The actual schema declaration, divided into `FeatureView`s (regular and so-called "on-demand"), and `PushSource`s.

When you have these two files ready, you'll be able to run the following commands:
- `feast apply`: Validates the declaration, and stores feature schema metadata in the persistent store.
- `feast materialize ${start_ts} ${end_ts}`: Writes features to online store for a given date range.
- `feast serve`: Server for querying online feature data.
- `feast serve_registry --rest-api --rest-port 8000`: Server for querying feature schema metadata.
- `feast materialize-incremental ${end_ts}`: Incrementally writes features to online using the last materialization timestamp as the start timestamp.
- `feast teardown`: Tears down schema.

Before you do all this, feast is expecting some data to exist.

## Batch Jobs, Streaming Jobs, and Kafka Connect
In this demo we are producing historical event data using the `python/training/seed_history.py` file and streaming new events using the `java/producer` java subproject. 

### Batch processing
The seed history script creates a bunch of synthetic play events and stores them in parquet format in the `raw` S3 bucket. We then have several batch jobs to convert the raw events into the following Postgres tables:

- `user_stats`: weekly user stats (7-day sliding window, daily snapshots)
- `track_stats`: lifetime track stats (daily snapshots)
- `track_realtime`: hourly track stats

Each of these has a respective batch job in `java/batch`. Those jobs read raw events from S3 via DuckDB, compute the aggregations, and load the results into the Postgres offline store. It's important to note that each of these batch jobs use the server served by `feast serve_registry` command to validate the schema before storing the historical feature data. 

### Stream Processing
There's a Flink job in `java/stream` that does the following:
- Consumes the `plays` topic, aggregates those events grouped by hour, and structures that data to align with the `track_realtime` feature schema. Every minute it pushes that data to the server running from `feast serve`. Feast then pushes that data to Redis - after validating the schema, of course. This online store of features will be used at inference time and fed to the model as an input for predictions.

In `java/producer` there's a job. Running it produces synthetic play events to the `plays` Kafka topic to demonstrate this.

### Kafka Connect
One more thing that's worth mentioning is that we are using Kafka Connect to store new events coming into the `plays` topic in the raw events S3 bucket. So new events come in, get put into the raw events bucket in parquet format properly partitioned, so that the daily batch jobs can read that data, transform it into the structure Feast expects - i.e. track stats and user stats feature schema - and load them into the Postgres offline store. Then `feast materialize` is run and our updated features are available from our online store for inference. See `k8s/kafka-connect-s3-sink.yaml` for details. 

Up to this point the model doesn't exist and we've produced the training data needed using the batch jobs we just discussed. We are now ready to train the model.

## Model training
In `python/training/train.py` we have our code for training the model. The type of model we're using is an xgboost classifier. For this demo we're not really concerned about the specifics of the model or even its performance. Instead, we're focused on the feature store. We need the following data to train the model:

- Raw labeled event data read directly from S3: We need labeled data so that the model can use it while training. The feature store does not store these.
- Features: These are read from offline store using Feast's python SDK - specifically the `FeatureStore.get_historical_features` method. 

### Preparing the data 
We start by getting the raw events into a Pandas `DataFrame`:

```python
labels = cast(
  pd.DataFrame,
  pd.read_parquet("s3://feast/raw/plays.parquet", storage_options=MINIO)[["user_id","track_id","event_timestamp", "skipped"]]
).rename(columns={"skipped":"label"})
```
There are some omitted lines that are important below these, but the point is we're loading in raw labeled data here.

Now, take a look at the Feast `Entity` declarations in `python/feast/features.py`:
```python
user = Entity(
  name="user",
  join_keys=["user_id"] 
)
track = Entity(
  name="track",
  join_keys=["track_id"] 
)
```

Notice the `join_keys` property. Finally, look at these lines in `python/training/train.py`:

```python
store = FeatureStore(repo_path=str(Path(__file__).resolve().parent.parent / "feast"))

training_df = store.get_historical_features(
  entity_df=labels,
  features=store.get_feature_service("skip_v1")
).to_df()
```

What this is doing is creating a wide-column list of training rows with all 11 features by joining features from the `track_stats`, `track_realtime` and `user_stats` Postgres tables on the `track_id` and `user_id` columns respectively, at the point-in-time specified.

The remaining training procedure is fairly typical so let's continue to model serving.

## Checkpoint
Before diving into model serving, let's review where we're at:
- First, we generated our raw events using `seed_history.py`.
- We then configured the Feast data stores and declared our feature schema within the files in `python/feast/` directory.
- Next we ran `feast apply` to persist the schema.
- Then we started the schema registry server with `feast serve_registry --rest-api --rest-port 8000` that way the batch jobs can validate the feature schema they're loading into the offline store.
- Next we ran the batch processing jobs defined in the `java/batch`.
- Then we ran `feast materialize` to populate the online store.
- Then we trained the model, and it currently resides at `python/training/model.onnx`
- Finally, we run `feast serve` which starts a server we use to get feature values at model inference time (at training time, the features are read directly from the offline store using Feast's SDK).

With the feature server running we're ready to run our inference server and start making predictions.

## Model serving
With all we've covered being a foundation, serving the model is fairly simple. The code for the REST server lives in `java/serving`.

One thing to note is that the `FeastReader` class is the thing that actually hits the Feast server to get online features at the aptly named route `get-online-features`. Looking at the controller tells most of the story:
```java
    Map<String,Object> online = feast.getOnlineFeatures(
      "skip_v1",
      Map.of(
        "user_id",
        r.userId(),
        "track_id",
        r.trackId(),
        "request_ts_epoch",
        nowEpoch
      )
    );
```
This is saying, get online features from `skip_v1` feature service, with this `user_id`, `track_id`, and timestamp. With that data, we're able to query the Feast feature server to get the values of the 11 features the model requires to make a prediction. And we're able to do this with a high degree of certainty that the schema of the features is correct, and thus we are inferencing on the model correctly in terms of feature input.

# Conclusion
Feast provides a framework for serving features at model training and inference time. Having the schema registry and point-in-time feature querying contracts Feast provides in place sets the stage for rapid and accurate model iteration. 
