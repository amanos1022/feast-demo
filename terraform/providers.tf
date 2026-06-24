terraform {
  required_providers {
    kind = { source = "tehcyx/kind", version = "~> 0.5" }
    helm = { source = "hashicorp/helm", version = "~> 2.17" }
    kubernetes = { source = "hashicorp/kubernetes", version = "~> 2.30"
    }
  }
}
# resource "kind_cluster" "this" {
#   name           = "music-fs"
#   wait_for_ready = true
#   kind_config {
#     kind        = "Cluster"
#     api_version = "kind.x-k8s.io/v1alpha4"
#     node {
#       role = "control-plane"
#       # expose Envoy's nodeport so you can curl /predict from the host
#       extra_port_mappings {
#         container_port = 30080
#         host_port      = 8080
#       }
#     }
#     node { role = "worker" }
#     node { role = "worker" }
#   }
# }

provider "helm" {
  kubernetes {
    config_path = "${path.module}/kubeconfig"
  }
}
provider "kubernetes" {
  config_path = "${path.module}/kubeconfig"
}
