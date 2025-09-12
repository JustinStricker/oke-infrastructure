# FILE: main.tf (Corrected and Aligned with Documentation)

terraform {
  # This backend block is now explicitly defined. The actual values for these
  # arguments will be provided by the '-backend-config' flags in the
  # GitHub Actions workflow, as specified in the HashiCorp documentation.
  backend "oci" {}
}

provider "oci" {
  tenancy_ocid     = var.tenancy_ocid
  user_ocid        = var.user_ocid
  fingerprint      = var.fingerprint
  private_key_path = var.private_key_path
  region           = var.region
}

# (The rest of your fully corrected Terraform code follows)

# -----------------------------------------------------------------------------
# Variable Definitions
# -----------------------------------------------------------------------------
variable "tenancy_ocid" {
  description = "The OCID of the tenancy."
  type        = string
  sensitive   = true
}

variable "user_ocid" {
  description = "The OCID of the user for API authentication."
  type        = string
  sensitive   = true
}

variable "fingerprint" {
  description = "The fingerprint of the API key."
  type        = string
  sensitive   = true
}

variable "private_key_path" {
  description = "The path to the OCI API private key file."
  type        = string
  sensitive   = true
}

variable "compartment_ocid" {
  description = "The OCID of the compartment to create resources in."
  type        = string
}

variable "region" {
  description = "The OCI region where resources will be created."
  type        = string
  default     = "us-ashburn-1"
}

variable "k8s_version" {
  description = "The version of Kubernetes to deploy."
  type        = string
  default     = "v1.29.1"
}

variable "node_image_ocid" {
  description = "The OCID of the custom image to use for the worker nodes. If not provided, a default Oracle Linux image will be used."
  type        = string
  default     = null
}

# -----------------------------------------------------------------------------
# Networking Resources
# -----------------------------------------------------------------------------
resource "oci_core_vcn" "oke_vcn" {
  compartment_id = var.compartment_ocid
  display_name   = "oke_poc_vcn"
  cidr_block     = "10.0.0.0/16"
}

resource "oci_core_internet_gateway" "oke_igw" {
  compartment_id = var.compartment_ocid
  vcn_id         = oci_core_vcn.oke_vcn.id
  display_name   = "oke_poc_igw"
}

resource "oci_core_route_table" "oke_route_table" {
  compartment_id = var.compartment_ocid
  vcn_id         = oci_core_vcn.oke_vcn.id
  display_name   = "oke_poc_route_table"
  route_rules {
    destination       = "0.0.0.0/0"
    destination_type  = "CIDR_BLOCK"
    network_entity_id = oci_core_internet_gateway.oke_igw.id
  }
}

resource "oci_core_subnet" "oke_api_subnet" {
  compartment_id             = var.compartment_ocid
  vcn_id                     = oci_core_vcn.oke_vcn.id
  display_name               = "oke_api_subnet"
  cidr_block                 = "10.0.1.0/24"
  route_table_id             = oci_core_route_table.oke_route_table.id
  prohibit_public_ip_on_vnic = false
}

resource "oci_core_subnet" "oke_nodes_subnet" {
  compartment_id             = var.compartment_ocid
  vcn_id                     = oci_core_vcn.oke_vcn.id
  display_name               = "oke_nodes_subnet"
  cidr_block                 = "10.0.2.0/24"
  route_table_id             = oci_core_route_table.oke_route_table.id
  prohibit_public_ip_on_vnic = false
}

# -----------------------------------------------------------------------------
# OKE Cluster Resources (Free Tier)
# -----------------------------------------------------------------------------
resource "oci_containerengine_cluster" "oke_cluster" {
  compartment_id     = var.compartment_ocid
  kubernetes_version = var.k8s_version
  name               = "poc-cluster"
  vcn_id             = oci_core_vcn.oke_vcn.id

  endpoint_config {
    subnet_id            = oci_core_subnet.oke_api_subnet.id
    is_public_ip_enabled = true
  }

  options {
    kubernetes_network_config {
      pods_cidr     = "10.244.0.0/16"
      services_cidr = "10.96.0.0/16"
    }
  }
}

resource "oci_containerengine_node_pool" "oke_node_pool" {
  cluster_id         = oci_containerengine_cluster.oke_cluster.id
  compartment_id     = var.compartment_ocid
  kubernetes_version = var.k8s_version
  name               = "poc-free-tier-pool"
  node_shape         = "VM.Standard.A1.Flex"

  node_shape_config {
    memory_in_gbs = 6
    ocpus         = 1
  }

  dynamic "node_source_details" {
    for_each = var.node_image_ocid != null ? [1] : []
    content {
      image_id    = var.node_image_ocid
      source_type = "image"
    }
  }

  node_config_details {
    placement_configs {
      availability_domain = data.oci_identity_availability_domains.ads.availability_domains[0].name
      subnet_id           = oci_core_subnet.oke_nodes_subnet.id
    }
    size = 4
  }
}

# -----------------------------------------------------------------------------
# Kubernetes Provider Configuration
# -----------------------------------------------------------------------------
data "oci_containerengine_cluster_kube_config" "kube_config" {
  cluster_id = oci_containerengine_cluster.oke_cluster.id
}

provider "kubernetes" {
  kubeconfig = data.oci_containerengine_cluster_kube_config.kube_config.content
}

# -----------------------------------------------------------------------------
# Kubernetes RBAC for CI/CD Pipeline
# -----------------------------------------------------------------------------
resource "kubernetes_namespace" "app_ns" {
  metadata {
    name = "ktor-app"
  }
}

resource "kubernetes_service_account" "cicd_sa" {
  metadata {
    name      = "github-actions-sa"
    namespace = kubernetes_namespace.app_ns.metadata[0].name
  }
}

resource "kubernetes_role" "cicd_role" {
  metadata {
    name      = "deployer-role"
    namespace = kubernetes_namespace.app_ns.metadata[0].name
  }
  rule {
    api_groups = ["", "apps", "extensions", "networking.k8s.io"]
    resources  = ["deployments", "services", "ingresses", "pods"]
    verbs      = ["*"]
  }
}

resource "kubernetes_role_binding" "cicd_rb" {
  metadata {
    name      = "deployer-binding"
    namespace = kubernetes_namespace.app_ns.metadata[0].name
  }
  role_ref {
    api_group = "rbac.authorization.k8s.io"
    kind      = "Role"
    name      = kubernetes_role.cicd_role.metadata[0].name
  }
  subject {
    kind      = "ServiceAccount"
    name      = kubernetes_service_account.cicd_sa.metadata[0].name
    namespace = kubernetes_namespace.app_ns.metadata[0].name
  }
}

# -----------------------------------------------------------------------------
# Data Source and Outputs
# -----------------------------------------------------------------------------
data "oci_identity_availability_domains" "ads" {
  compartment_id = var.compartment_ocid
}

output "cluster_ocid" {
  description = "The OCID of the OKE cluster."
  value       = oci_containerengine_cluster.oke_cluster.id
}

output "application_namespace" {
  description = "The Kubernetes namespace created for the application."
  value       = kubernetes_namespace.app_ns.metadata[0].name
}

output "kubeconfig_command" {
  description = "Run this command to get the kubeconfig file for local testing."
  value       = "oci ce cluster create-kubeconfig --cluster-id ${oci_containerengine_cluster.oke_cluster.id} --file $HOME/.kube/config --region ${var.region} --token-version 2.0.0"
}
