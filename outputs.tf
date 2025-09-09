# This output will display the public IP address of the load balancer
# once the deployment is complete.
output "load_balancer_ip" {
  description = "The public IP address of the load balancer."
  # Correctly references the data source to get the service status.
  value = data.kubectl_get_service.ktor_service.status.0.load_balancer.0.ingress.0.ip
}

# This output displays the ID of the created OKE cluster.
output "cluster_id" {
  value = oci_containerengine_cluster.oke_cluster.id
}

