# music-feature-consistency

Training/serving skew detection for music recommendation features, built with Flink, Feast, and Kafka.

## Architecture

- **Kafka** (Strimzi) — event streaming (`plays`, `served-features` topics)
- **Flink** — stream processing for feature computation
- **Feast** — feature store (PostgreSQL offline, Redis online, SeaweedFS S3 registry)
- **Redis** — online feature serving
- **PostgreSQL** — offline feature store
- **SeaweedFS** — S3-compatible object store

## Project layout

```
.
├── java/                   Java (Gradle multi-module)
│   ├── common/             Shared POJOs and utilities
│   ├── producer/           Generates play events into Kafka
│   ├── stream/             Flink jobs for feature computation
│   └── serving/            Feature serving and skew detection
├── python/                 Python
│   ├── training/           Seed historical data into S3
│   └── feast/              Feature store definitions
├── terraform/              Infrastructure (Helm releases, namespaces)
└── k8s/                    Kubernetes manifests (Kafka, Redis, Postgres)
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

Or manually:

```bash
nix develop              # infra tools
nix develop .#java       # java tools
nix develop .#python     # python tools
```

### 2. Create the kind cluster

```bash
kind create cluster --name music-fs \
  --config - <<'EOF'
kind: Cluster
apiVersion: kind.x-k8s.io/v1alpha4
nodes:
  - role: control-plane
    extraPortMappings:
      - containerPort: 30080
        hostPort: 8080
  - role: worker
  - role: worker
EOF

kind get kubeconfig --name music-fs > terraform/kubeconfig
```

### 3. Deploy platform infrastructure (Terraform)

```bash
cd terraform
terraform init
terraform apply
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
```

### 6. Port-forward services

```bash
kubectl port-forward svc/seaweedfs-s3 -n data 9000:8333 &
kubectl port-forward svc/music-kafka-brokers -n data 9092:9092 &
kubectl port-forward svc/redis -n data 6379:6379 &
kubectl port-forward svc/postgres -n data 5432:5432 &
```

### 7. Seed historical data

```bash
cd python/training
uv sync
uv run python seed_history.py
```

### 8. Set up Feast

```bash
cd python/feast
uv sync
export AWS_ACCESS_KEY_ID=minio AWS_SECRET_ACCESS_KEY=minio12345 FEAST_S3_ENDPOINT_URL=http://localhost:9000
uv run feast apply
uv run feast serve   # starts feature server
```

### 9. Build and run Java

```bash
cd java
gradle build
gradle :producer:run     # starts producing play events to Kafka
```

## Teardown

```bash
kind delete cluster --name music-fs
```
