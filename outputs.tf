# This output will display the public IP address of the load balancer
# created for the Kubernetes service. This is the main endpoint for
# accessing the deployed Ktor application.

output "load_balancer_ip" {
  description = "Public IP address of the Ktor application's load balancer."
  # The value is sourced from the 'kubernetes_service' data block in main.tf
  value = data.kubernetes_service.ktor_service.status[0].load_balancer[0].ingress[0].ip
}

