# This block configures the required providers for this Terraform project.
# It specifies the source and version for each provider.
terraform {
  required_providers {
    # The official OCI provider from Oracle.
    oci = {
      source  = "oracle/oci"
      version = "5.19.0"
    }
    # The community-supported kubectl provider.
    kubectl = {
      source  = "gavinbunney/kubectl"
      version = "1.14.0"
    }
    # The official Kubernetes provider.
    kubernetes = {
      source  = "hashicorp/kubernetes"
      version = "2.23.0"
    }
    # Provider to manage local files.
    local = {
      source  = "hashicorp/local"
      version = "2.4.0"
    }
  }
}

# Configure the Oracle Cloud Infrastructure (OCI) provider with credentials.
# These values are passed in as variables from the GitHub Actions workflow.
provider "oci" {
  tenancy_ocid     = var.tenancy_ocid
  user_ocid        = var.user_ocid
  fingerprint      = var.fingerprint
  private_key      = var.private_key
  region           = var.region
}

# Data source to fetch the kubeconfig for the OKE cluster.
data "oci_containerengine_cluster_kube_config" "oke_kubeconfig" {
  cluster_id = oci_containerengine_cluster.oke_cluster.id
}

# Resource to save the fetched kubeconfig content to a local file.
# This bridges the gap between the data source (content) and the providers (path).
resource "local_file" "kubeconfig_file" {
  content  = data.oci_containerengine_cluster_kube_config.oke_kubeconfig.content
  filename = "${path.module}/kubeconfig"
}

# Configure the Kubernetes provider using the path to the local kubeconfig file.
provider "kubernetes" {
  config_path = local_file.kubeconfig_file.filename
}

# Configure the kubectl provider using the path to the local kubeconfig file.
provider "kubectl" {
  config_path = local_file.kubeconfig_file.filename
}

# --- Networking Resources ---

# Fetches the list of availability domains in the region.
data "oci_identity_availability_domains" "ads" {
  compartment_id = var.tenancy_ocid
}

# Creates a Virtual Cloud Network (VCN) for the cluster.
resource "oci_core_vcn" "oke_vcn" {
  compartment_id = var.compartment_ocid
  display_name   = "oke_vcn"
  cidr_block     = "10.0.0.0/16"
}

# Creates an Internet Gateway to allow traffic to and from the internet.
resource "oci_core_internet_gateway" "oke_ig" {
  compartment_id = var.compartment_ocid
  vcn_id         = oci_core_vcn.oke_vcn.id
  display_name   = "oke_ig"
}

# Creates a default route table for the VCN.
resource "oci_core_default_route_table" "oke_route_table" {
  manage_default_resource_id = oci_core_vcn.oke_vcn.default_route_table_id
  display_name               = "oke_route_table"
  route_rules {
    destination       = "0.0.0.0/0"
    network_entity_id = oci_core_internet_gateway.oke_ig.id
  }
}

# Creates a subnet for the OKE worker nodes.
resource "oci_core_subnet" "oke_nodes_subnet" {
  compartment_id     = var.compartment_ocid
  vcn_id             = oci_core_vcn.oke_vcn.id
  display_name       = "oke_nodes_subnet"
  cidr_block         = "10.0.1.0/24"
  route_table_id     = oci_core_vcn.oke_vcn.default_route_table_id
  security_list_ids  = [oci_core_vcn.oke_vcn.default_security_list_id]
  dhcp_options_id    = oci_core_vcn.oke_vcn.default_dhcp_options_id
}

# Creates a subnet for the Kubernetes load balancers.
resource "oci_core_subnet" "oke_lb_subnet" {
  compartment_id     = var.compartment_ocid
  vcn_id             = oci_core_vcn.oke_vcn.id
  display_name       = "oke_lb_subnet"
  cidr_block         = "10.0.2.0/24"
  route_table_id     = oci_core_vcn.oke_vcn.default_route_table_id
  security_list_ids  = [oci_core_vcn.oke_vcn.default_security_list_id]
  dhcp_options_id    = oci_core_vcn.oke_vcn.default_dhcp_options_id
}

# --- OKE Cluster Resources ---

# Creates the Oracle Kubernetes Engine (OKE) cluster.
resource "oci_containerengine_cluster" "oke_cluster" {
  compartment_id     = var.compartment_ocid
  kubernetes_version = "1.31.10" # Updated to match the image you found
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
    service_lb_subnet_ids = [oci_core_subnet.oke_lb_subnet.id]
  }
}

# Creates the node pool for the OKE cluster. These are the worker machines.
resource "oci_containerengine_node_pool" "oke_node_pool" {
  cluster_id         = oci_containerengine_cluster.oke_cluster.id
  compartment_id     = var.compartment_ocid
  kubernetes_version = oci_containerengine_cluster.oke_cluster.kubernetes_version
  name               = "amd-pool-free"
  node_shape         = "VM.Standard.A1.Flex" # Using the "Always Free" Ampere shape
  node_shape_config {
    ocpus               = 1 # Specifying "Always Free" tier OCPUs
    memory_in_gbs       = 6 # Specifying "Always Free" tier memory
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
    size = 1 # Number of nodes in the pool
  }
}

# --- Kubernetes Deployment Resources ---

# Creates a Kubernetes namespace for the application.
resource "kubernetes_namespace" "app_ns" {
  metadata {
    name = "ktor-app"
  }
}

# Creates a Kubernetes deployment for the Ktor application.
resource "kubernetes_deployment" "ktor_app_deployment" {
  metadata {
    name      = "ktor-app-deployment"
    namespace = kubernetes_namespace.app_ns.metadata[0].name
  }
  spec {
    replicas = 1
    selector {
      match_labels = {
        app = "ktor-app"
      }
    }
    template {
      metadata {
        labels = {
          app = "ktor-app"
        }
      }
      spec {
        container {
          image = var.docker_image
          name  = "ktor-app-container"
          port {
            container_port = 8080
          }
        }
      }
    }
  }
  # This deployment depends on the node pool being ready.
  depends_on = [oci_containerengine_node_pool.oke_node_pool]
}

# Creates a Kubernetes service to expose the application.
# This will create a public load balancer.
resource "kubernetes_service" "ktor_app_service" {
  metadata {
    name      = "ktor-app-service"
    namespace = kubernetes_namespace.app_ns.metadata[0].name
  }
  spec {
    selector = {
      app = kubernetes_deployment.ktor_app_deployment.spec[0].template[0].metadata[0].labels.app
    }
    port {
      port        = 80
      target_port = 8080
    }
    type = "LoadBalancer"
  }
}

# Data source to get the status of the Kubernetes service after it's deployed.
# This is needed to retrieve the load balancer's public IP address.
data "kubernetes_service" "ktor_service" {
  metadata {
    name      = kubernetes_service.ktor_app_service.metadata[0].name
    namespace = kubernetes_namespace.app_ns.metadata[0].name
  }
  depends_on = [
    kubernetes_service.ktor_app_service
  ]
}


