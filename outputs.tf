output "cluster_id" {
  description = "The OCID of the OKE cluster."
  value       = oci_containerengine_cluster.this.id
}

output "cluster_name" {
  description = "Name of the OKE cluster."
  value       = oci_containerengine_cluster.this.name
}

output "kubernetes_version" {
  description = "Kubernetes version running on the cluster."
  value       = oci_containerengine_cluster.this.kubernetes_version
}

output "node_pool_id" {
  description = "The OCID of the node pool."
  value       = oci_containerengine_node_pool.this.id
}

output "node_pool_size" {
  description = "Number of worker nodes."
  value       = var.node_pool_size
}

output "vcn_id" {
  description = "The OCID of the VCN."
  value       = oci_core_vcn.this.id
}

output "postgres_subnet_id" {
  description = "The OCID of the private PostgreSQL subnet."
  value       = oci_core_subnet.postgres_private.id
}

output "postgres_subnet_cidr" {
  description = "CIDR block of the private PostgreSQL subnet."
  value       = oci_core_subnet.postgres_private.cidr_block
}

output "node_subnet_id" {
  description = "The OCID of the worker node subnet."
  value       = oci_core_subnet.node.id
}

output "backup_bucket_name" {
  description = "Name of the OCI Object Storage bucket for PostgreSQL backups."
  value       = oci_objectstorage_bucket.postgres_backups.name
}

output "backup_bucket_namespace" {
  description = "Object Storage namespace for the backup bucket."
  value       = data.oci_objectstorage_namespace.this.namespace
}

output "kubeconfig_command" {
  description = "Command to generate kubeconfig for this cluster."
  value       = "oci ce cluster create-kubeconfig --cluster-id ${oci_containerengine_cluster.this.id} --file $$HOME/.kube/config --region ${var.region} --token-version 2.0.0"
}
