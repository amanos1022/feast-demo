# music-feature-consistency

Training/serving skew detection for music recommendation features, built with Flink, Feast, and Kafka.

See [README.md](README.md) for a detailed explanation of the architecture and how everything fits together.

## Architecture

- **Kafka** (Strimzi) — event streaming (`plays` topic)
- **Flink** — stream processing for realtime feature computation
- **Feast** — feature store (PostgreSQL registry + offline, Redis online)
- **Redis** — online feature serving
- **PostgreSQL** — offline feature store + Feast registry
- **SeaweedFS** — S3-compatible object store (raw events)

## Project layout

```
.
├── java/                   Java (Gradle multi-module)
│   ├── common/             Shared config and utilities
│   ├── batch/              Batch jobs: raw events -> offline store (Postgres)
│   ├── producer/           Generates play events into Kafka
│   ├── stream/             Flink job for realtime feature computation
│   └── serving/            Model inference server (Spring Boot)
├── python/
│   ├── shared/             Shared config module
│   ├── training/           Model training + seed history script
│   └── feast/              Feature store definitions
├── terraform/              Infrastructure (Helm releases, namespaces)
├── k8s/                    Kubernetes manifests (Kafka, Redis, Postgres, Kafka Connect)
└── scripts/                Helper scripts (init-tables, port-forward)
```

## Prerequisites

- [Nix](https://nixos.org/download/) with flakes enabled
- [direnv](https://direnv.net/)
- Docker (required by kind)

## Getting started

### 1. Enter the dev shell

The root shell provides infrastructure tools (terraform, kubectl, kind, helm).
Subdirectories activate language-specific shells automatically via direnv.

```bash
direnv allow
cd java && direnv allow    # JDK 21, Gradle
cd python && direnv allow  # Python 3.12, uv
```

### 2. Create the kind cluster

```bash
kind create cluster --name music-fs --image kindest/node:v1.33.1
kind get kubeconfig --name music-fs > terraform/kubeconfig
```

### 3. Deploy platform infrastructure (Terraform)

```bash
cd terraform
terraform init
terraform apply
cd ..
```

This installs:
- Namespaces: `data`, `feast`, `flink`, `app`, `cert-manager`
- Strimzi Kafka operator
- cert-manager
- Flink Kubernetes operator

### 4. Install SeaweedFS (via Helm directly)

The SeaweedFS chart uses Helm's `fromToml` function (Helm 3.16+), which the Terraform Helm provider does not yet support. Install it with system Helm instead:

```bash
helm repo add seaweedfs https://seaweedfs.github.io/seaweedfs/helm
helm repo update
helm install seaweedfs seaweedfs/seaweedfs -n data -f terraform/values/seaweedfs.yaml
```

### 5. Deploy Kubernetes resources

Wait for the Strimzi operator to be ready, then apply the manifests:

```bash
kubectl wait --for=condition=ready pod -l name=strimzi-cluster-operator -n data --timeout=120s

kubectl apply -f k8s/kafka-cluster.yaml
kubectl apply -f k8s/postgres.yaml
kubectl apply -f k8s/redis.yaml
kubectl apply -f k8s/kafka-connect-s3-sink.yaml
```

### 6. Port-forward services

```bash
kubectl port-forward svc/seaweedfs-s3 -n data 9000:8333 &
kubectl port-forward svc/music-kafka-brokers -n data 9092:9092 &
kubectl port-forward svc/redis -n data 6379:6379 &
kubectl port-forward svc/postgres -n data 5432:5432 &
```

### 7. Initialize offline store tables and seed data

```bash
./scripts/init-tables.sh

cd python/training
uv sync
.venv/bin/python seed_history.py
```

### 8. Set up Feast

```bash
cd python/feast
uv sync
.venv/bin/feast apply
.venv/bin/feast serve_registry --rest-api --rest-port 8000 &
```

### 9. Run batch processors

```bash
cd java
./gradlew :batch:run
```

### 10. Materialize and train

```bash
cd python/feast
.venv/bin/feast materialize 2024-06-01T00:00:00 2024-06-15T00:00:00

cd ../training
.venv/bin/python train.py
```

### 11. Run inference server and streaming

```bash
cd python/feast
.venv/bin/feast serve &

cd java
./gradlew :serving:run &
./gradlew :producer:run &
./gradlew :stream:run &
```

### 12. Test

```bash
curl -s -X POST http://localhost:8080/predict \
    -H "Content-Type: application/json" \
    -d '{"requestId":"test-1","userId":"u_0002","trackId":"t_0126"}'
```

## Teardown

```bash
kind delete cluster --name music-fs
```
