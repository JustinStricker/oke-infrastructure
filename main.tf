# 1. DEFINE PROVIDERS AND REMOTE STATE BACKEND
# This tells Terraform what tools it needs and where to store the project's state securely.
terraform {
  required_providers {
    oci = {
      source  = "oracle/oci"
      version = "~> 7.0"
    }
    kubernetes = {
      source  = "hashicorp/kubernetes"
      version = "~> 2.30"
    }
  }

  backend "oci" {
    bucket    = "your-tfstate-bucket-name" # IMPORTANT: Create this OCI bucket first.
    key       = "ktor-oke-app/terraform.tfstate"
    region    = "us-ashburn-1" # Or your desired region
    namespace = "your_tenancy_namespace"
  }
}

# 2. CONFIGURE OCI AUTHENTICATION
# This uses variables to securely authenticate with your OCI account.
provider "oci" {
  tenancy_ocid     = var.tenancy_ocid
  user_ocid        = var.user_ocid
  fingerprint      = var.fingerprint
  private_key_path = var.private_key_path
  region           = var.region
}

# 3. CONFIGURE KUBERNETES PROVIDER (THE "OPTION B" METHOD)
# Instead of using a file, we configure the provider directly with data from the OKE cluster.
# Terraform automatically waits for the cluster to be created before configuring this.
provider "kubernetes" {
  host                   = oci_containerengine_cluster.oke_cluster.endpoints[0].kubernetes
  cluster_ca_certificate = base64decode(oci_containerengine_cluster.oke_cluster.endpoints[0].ca_certificate)
  token                  = data.oci_containerengine_cluster_auth_token.oke_auth_token.token
}

# 4. DEFINE INPUT VARIABLES
# These are the parameters our configuration needs from the outside world (like GitHub Actions).
variable "tenancy_ocid" {}
variable "user_ocid" {}
variable "fingerprint" {}
variable "private_key_path" {}
variable "region" {}
variable "compartment_ocid" {}
variable "node_image_ocid" {}
variable "docker_image" {}
variable "tenancy_namespace" {} # Used by the backend block

# 5. DEFINE THE NETWORKING RESOURCES
# A Kubernetes cluster needs a Virtual Cloud Network (VCN) and subnets.
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
}

resource "oci_core_subnet" "oke_lb_subnet" {
  compartment_id = var.compartment_ocid
  vcn_id         = oci_core_vcn.oke_vcn.id
  display_name   = "oke_lb_subnet"
  cidr_block     = "10.0.2.0/24"
}

# 6. DEFINE THE OKE CLUSTER AND NODE POOL
data "oci_identity_availability_domains" "ads" {
  compartment_id = var.tenancy_ocid
}

resource "oci_containerengine_cluster" "oke_cluster" {
  compartment_id     = var.compartment_ocid
  kubernetes_version = "v1.33.1" # Choose a currently supported version
  name               = "ktor_oke_cluster"
  vcn_id             = oci_core_vcn.oke_vcn.id
  options {
    service_lb_subnet_ids = [oci_core_subnet.oke_lb_subnet.id]
  }
}

resource "oci_containerengine_node_pool" "oke_node_pool" {
  cluster_id         = oci_containerengine_cluster.oke_cluster.id
  compartment_id     = var.compartment_ocid
  kubernetes_version = oci_containerengine_cluster.oke_cluster.kubernetes_version
  name               = "default-pool"
  node_shape         = "VM.Standard.A1.Flex"
  node_shape_config { ocpus = 1; memory_in_gbs = 6 }
  node_source_details { image_id = var.node_image_ocid; source_type = "image" }
  node_config_details {
    size = 1
    placement_configs {
      availability_domain = data.oci_identity_availability_domains.ads.availability_domains[0].name
      subnet_id           = oci_core_subnet.oke_nodes_subnet.id
    }
  }
}

# 7. GENERATE THE KUBERNETES AUTH TOKEN IN MEMORY
data "oci_containerengine_cluster_auth_token" "oke_auth_token" {
  cluster_id = oci_containerengine_cluster.oke_cluster.id
}

# 8. DEFINE THE KUBERNETES APPLICATION RESOURCES
# This will create the Deployment (to run the container) and Service (to expose it).
resource "kubernetes_deployment" "ktor_app_deployment" {
  metadata {
    name   = "ktor-oke-app"
    labels = { app = "ktor-oke-app" }
  }
  spec {
    replicas = 2
    selector { match_labels = { app = "ktor-oke-app" } }
    template {
      metadata { labels = { app = "ktor-oke-app" } }
      spec {
        container {
          image = var.docker_image
          name  = "ktor-oke-app-container"
          port { container_port = 8080 }
        }
      }
    }
  }
}

resource "kubernetes_service" "ktor_app_service" {
  metadata {
    name = "ktor-oke-app-service"
  }
  spec {
    selector = { app = "ktor-oke-app" }
    port { port = 80; target_port = 8080 }
    type     = "LoadBalancer" # This creates a public IP and load balancer in OCI.
  }
}

# 9. DEFINE AN OUTPUT
# This makes it easy to find the public IP address after the deployment is complete.
output "load_balancer_ip" {
  description = "Public IP address of the Ktor application's load balancer."
  value       = try(kubernetes_service.ktor_app_service.status[0].load_balancer[0].ingress[0].ip, "creating...")
}
