# --- Terraform Backend & Providers ---
terraform {
  # Stores the state file remotely in OCI Object Storage for safety and collaboration
  backend "oci" {
    bucket    = "ktor-oke-app-tfstate"
    key       = "ktor-oke/terraform.tfstate"
    region    = "us-ashburn-1"
    namespace = "idrolupgk4or"
  }

  required_providers {
    oci        = { source = "oracle/oci", version = ">= 5.0" }
    kubernetes = { source = "hashicorp/kubernetes", version = ">= 2.20" }
    local      = { source = "hashicorp/local", version = "2.5.1" }
  }
}

# Provider for managing Oracle Cloud Infrastructure resources
provider "oci" {
  tenancy_ocid = var.tenancy_ocid
  user_ocid    = var.user_ocid
  fingerprint  = var.fingerprint
  private_key  = var.private_key
  region       = var.region
}

# Provider for managing Kubernetes resources within the created OKE cluster
provider "kubernetes" {
  alias = "oke"

  host = oci_containerengine_cluster.oke_cluster.endpoints[0].kubernetes

  cluster_ca_certificate = base64decode(local.cluster_ca_certificate_data)

  # This exec block tells the provider how to authenticate with the cluster.
  # It runs the OCI CLI command to generate a temporary auth token.
  exec {
    api_version = "client.authentication.k8s.io/v1beta1"
    command     = "oci"
    args = [
      "ce",
      "cluster",
      "generate-token",
      "--cluster-id",
      oci_containerengine_cluster.oke_cluster.id
    ]
  }
}

# --- Variable Definitions ---
# These variables receive their values from the GitHub Actions workflow environment

variable "tenancy_ocid" {
  description = "OCI tenancy OCID"
  type        = string
}

variable "user_ocid" {
  description = "OCI user OCID"
  type        = string
}

variable "fingerprint" {
  description = "API key fingerprint"
  type        = string
}

variable "private_key" {
  description = "Private API key content"
  type        = string
  sensitive   = true
}

variable "region" {
  description = "OCI region"
  type        = string
}

variable "compartment_ocid" {
  description = "Target compartment OCID where resources will be created"
  type        = string
}

variable "node_image_ocid" {
  description = "The OCID of the image to use for the OKE worker nodes"
  type        = string
}

variable "docker_image" {
  description = "The full URL of the Docker image built by the CI/CD pipeline"
  type        = string
}

variable "tenancy_namespace" {
  description = "The Object Storage namespace, used for OCIR"
  type        = string
}

# --- Networking ---
data "oci_identity_availability_domains" "ads" {
  compartment_id = var.tenancy_ocid
}

resource "oci_core_vcn" "oke_vcn" {
  compartment_id = var.compartment_ocid
  display_name   = "oke_vcn"
  cidr_block     = "10.0.0.0/16"
}

resource "oci_core_internet_gateway" "oke_ig" {
  compartment_id = var.compartment_ocid
  vcn_id         = oci_core_vcn.oke_vcn.id
  display_name   = "oke_ig"
}

resource "oci_core_default_route_table" "oke_route_table" {
  manage_default_resource_id = oci_core_vcn.oke_vcn.default_route_table_id
  display_name               = "oke_route_table"
  route_rules {
    destination       = "0.0.0.0/0"
    network_entity_id = oci_core_internet_gateway.oke_ig.id
  }
}

resource "oci_core_subnet" "oke_nodes_subnet" {
  compartment_id = var.compartment_ocid
  vcn_id         = oci_core_vcn.oke_vcn.id
  display_name   = "oke_nodes_subnet"
  cidr_block     = "10.0.1.0/24"
  # Associates the networking rules needed for the nodes
  route_table_id    = oci_core_vcn.oke_vcn.default_route_table_id
  security_list_ids = [oci_core_vcn.oke_vcn.default_security_list_id]
  dhcp_options_id   = oci_core_vcn.oke_vcn.default_dhcp_options_id
}

resource "oci_core_subnet" "oke_lb_subnet" {
  compartment_id = var.compartment_ocid
  vcn_id         = oci_core_vcn.oke_vcn.id
  display_name   = "oke_lb_subnet"
  cidr_block     = "10.0.2.0/24"
  # Associates the networking rules needed for public load balancers
  route_table_id    = oci_core_vcn.oke_vcn.default_route_table_id
  security_list_ids = [oci_core_vcn.oke_vcn.default_security_list_id]
  dhcp_options_id   = oci_core_vcn.oke_vcn.default_dhcp_options_id
}

# --- IAM Policy for OKE Worker Nodes to access OCIR (NEWLY ADDED SECTION) ---

# This Dynamic Group automatically includes all OKE worker nodes in the specified compartment.
resource "oci_identity_dynamic_group" "oke_nodes_dynamic_group" {
  provider       = oci
  compartment_id = var.tenancy_ocid # Dynamic groups must be created in the root compartment (tenancy)
  description    = "Dynamic group for OKE worker nodes to pull images from OCIR"
  name           = "oke-nodes-dynamic-group"

  matching_rule = "ALL {instance.compartment.id = '${var.compartment_ocid}'}"
}

# This policy grants the dynamic group the necessary permissions to read from OCIR.
resource "oci_identity_policy" "oke_nodes_ocir_policy" {
  provider       = oci
  compartment_id = var.tenancy_ocid # Policies must also be created in the root compartment
  description    = "Allow OKE nodes to read container images"
  name           = "oke-nodes-ocir-policy"

  statements = [
    "Allow dynamic-group ${oci_identity_dynamic_group.oke_nodes_dynamic_group.name} to read repos in compartment id ${var.compartment_ocid}"
  ]
}

# --- OKE Cluster ---
resource "oci_containerengine_cluster" "oke_cluster" {
  compartment_id     = var.compartment_ocid
  kubernetes_version = "v1.33.1"
  name               = "ktor_oke_cluster"
  vcn_id             = oci_core_vcn.oke_vcn.id
  options {
    add_ons {
      is_kubernetes_dashboard_enabled = false
    }
    kubernetes_network_config {
      pods_cidr     = "10.244.0.0/16"
      services_cidr = "10.96.0.0/16"
    }
    # Specifies the subnet for provisioning public load balancers
    service_lb_subnet_ids = [oci_core_subnet.oke_lb_subnet.id]
  }
}

resource "oci_containerengine_node_pool" "oke_node_pool" {
  cluster_id         = oci_containerengine_cluster.oke_cluster.id
  compartment_id     = var.compartment_ocid
  kubernetes_version = oci_containerengine_cluster.oke_cluster.kubernetes_version
  name               = "amd-pool-free"
  node_shape         = "VM.Standard.A1.Flex"
  node_shape_config {
    ocpus         = 1
    memory_in_gbs = 6
  }
  node_source_details {
    image_id    = var.node_image_ocid
    source_type = "image"
  }
  node_config_details {
    placement_configs {
      availability_domain = data.oci_identity_availability_domains.ads.availability_domains[0].name
      subnet_id           = oci_core_subnet.oke_nodes_subnet.id
    }
    size = 1 # Number of worker nodes
  }
}

# --- Kubeconfig Bootstrap ---
# Fetches the kubeconfig file needed to interact with the cluster
data "oci_containerengine_cluster_kube_config" "oke_kube_config" {
  cluster_id = oci_containerengine_cluster.oke_cluster.id
}

# Extracts the certificate authority data to configure the Kubernetes provider
locals {
  kubeconfig_yaml             = yamldecode(data.oci_containerengine_cluster_kube_config.oke_kube_config.content)
  cluster_ca_certificate_data = local.kubeconfig_yaml.clusters[0].cluster["certificate-authority-data"]
}

# --- Kubernetes Application Deployment ---

resource "kubernetes_deployment" "ktor_app_deployment" {
  # Ensures this resource is managed by the Kubernetes provider configured for OKE
  provider = kubernetes.oke

  metadata {
    name = "ktor-oke-app"
    labels = {
      app = "ktor-oke-app"
    }
  }

  spec {
    replicas = 2 # Run two instances for redundancy

    selector {
      match_labels = {
        app = "ktor-oke-app"
      }
    }

    template {
      metadata {
        labels = {
          app = "ktor-oke-app"
        }
      }
      spec {
        container {
          image = var.docker_image # The image built and pushed by the workflow
          name  = "ktor-oke-app-container"
          
          port {
            container_port = 8080 # The port your application listens on
          }
        }
      }
    }
  }
}

resource "kubernetes_service" "ktor_app_service" {
  # Ensures this resource is managed by the Kubernetes provider configured for OKE
  provider = kubernetes.oke

  metadata {
    name = "ktor-oke-app-service"
  }

  spec {
    # This selector links the service to the pods managed by the deployment
    selector = {
      app = kubernetes_deployment.ktor_app_deployment.metadata[0].labels.app
    }

    port {
      port        = 80   # Expose port 80 on the load balancer
      target_port = 8080 # Route traffic to port 8080 on the containers
    }
    
    # This crucial setting provisions a public OCI Load Balancer
    type = "LoadBalancer"
  }
}
