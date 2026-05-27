variable "compartment_ocid" {
  description = "The OCID of the compartment where resources will be created."
  type        = string
}

variable "tenancy_ocid" {
  description = "The OCID of your tenancy."
  type        = string
}

variable "cluster_name" {
  description = "Name of the OKE cluster."
  type        = string
  default     = "oke-infrastructure"
}

variable "kubernetes_version" {
  description = "Kubernetes version for the OKE cluster and node pool."
  type        = string
  default     = "v1.35.2"
}

variable "node_shape" {
  description = "The compute shape for worker nodes."
  type        = string
  default     = "VM.Standard.A1.Flex"
}

variable "node_ocpus" {
  description = "Number of OCPUs per worker node."
  type        = number
  default     = 1
}

variable "node_memory_gbs" {
  description = "Memory in GB per worker node."
  type        = number
  default     = 6
}

variable "node_pool_size" {
  description = "Number of worker nodes in the node pool."
  type        = number
  default     = 4
}

variable "region" {
  description = "OCI region for data source lookups."
  type        = string
  default     = "us-ashburn-1"
}