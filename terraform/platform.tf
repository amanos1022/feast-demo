resource "kubernetes_namespace" "ns" {
  for_each = toset(["data", "feast", "flink", "app"])
  metadata { name = each.key }
}

# --- Kafka via Strimzi operator (OSS, the standard for Kafka-on-k8s) ---
resource "helm_release" "strimzi" {
  name       = "strimzi"
  namespace  = "data"
  repository = "https://strimzi.io/charts/"
  chart      = "strimzi-kafka-operator"
  depends_on = [kubernetes_namespace.ns]
}

# --- Flink Kubernetes Operator (official) ---
resource "kubernetes_namespace" "cert_manager" {
  metadata {
    name = "cert-manager"
  }
}

resource "helm_release" "cert_manager" {
  name       = "cert-manager"
  namespace  = "cert-manager"
  repository = "https://charts.jetstack.io"
  chart      = "cert-manager"

  set {
    name  = "crds.enabled"
    value = "true"
  }

  depends_on = [kubernetes_namespace.cert_manager]
}

resource "helm_release" "flink_operator" {
  name       = "flink-operator"
  namespace  = "flink"
  repository = "https://downloads.apache.org/flink/flink-kubernetes-operator-1.15.0"
  chart      = "flink-kubernetes-operator"
  depends_on = [helm_release.cert_manager]

  set {
    name  = "wait"
    value = false
  }
}
