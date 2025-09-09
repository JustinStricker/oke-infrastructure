output "cluster_name" {
  value = oci_containerengine_cluster.oke_cluster.name
}

output "load_balancer_ip" {
  value = kubectl_get_service.ktor_service.status.0.load_balancer.0.ingress.0.ip
}

data "kubectl_get_service" "ktor_service" {
    name = "ktor-app-service"
}
