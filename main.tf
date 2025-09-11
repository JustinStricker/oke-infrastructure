terraform {
  required_providers {
    oci = {
      source  = "oracle/oci"
      version = "7.17.0"
    }
    kubernetes = {
      source  = "hashicorp/kubernetes"
      version = "2.31.0"
    }
    local = {
      source  = "hashicorp/local"
      version = "2.5.1"
    }
  }

  backend "oci" {
    bucket    = "ktor-oke-app-tfstate"
    key       = "ktor-oke/terraform.tfstate"
    region    = "us-ashburn-1"
    namespace = "idrolupgk4or"
  }
}

provider "oci" {
  tenancy_ocid     = var.tenancy_ocid
  user_ocid        = var.user_ocid
  fingerprint      = var.fingerprint
  private_key_path = var.private_key_path
  region           = var.region
}

provider "kubernetes" {
  config_path = local_file.kubeconfig_file.filename
}

variable "tenancy_ocid" {}
variable "user_ocid" {}
variable "fingerprint" {}
variable "private_key_path" {}
variable "region" {}
variable "compartment_ocid" {}
variable "node_image_ocid" {}
variable "docker_image" {}
variable "tenancy_namespace" {}

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
  compartment_id    = var.compartment_ocid
  vcn_id            = oci_core_vcn.oke_vcn.id
  display_name      = "oke_nodes_subnet"
  cidr_block        = "10.0.1.0/24"
  route_table_id    = oci_core_vcn.oke_vcn.default_route_table_id
  security_list_ids = [oci_core_vcn.oke_vcn.default_security_list_id]
  dhcp_options_id   = oci_core_vcn.oke_vcn.default_dhcp_options_id
}

resource "oci_core_subnet" "oke_lb_subnet" {
  compartment_id    = var.compartment_ocid
  vcn_id            = oci_core_vcn.oke_vcn.id
  display_name      = "oke_lb_subnet"
  cidr_block        = "10.0.2.0/24"
  route_table_id    = oci_core_vcn.oke_vcn.default_route_table_id
  security_list_ids = [oci_core_vcn.oke_vcn.default_security_list_id]
  dhcp_options_id   = oci_core_vcn.oke_vcn.default_dhcp_options_id
}

resource "oci_identity_dynamic_group" "oke_nodes_dynamic_group" {
  compartment_id = var.tenancy_ocid
  description    = "Dynamic group for OKE worker nodes"
  name           = "oke-nodes-dynamic-group"
  matching_rule  = "ALL {instance.compartment.id = '${var.compartment_ocid}', resource.type = 'instance'}"
}

resource "oci_identity_policy" "oke_nodes_ocir_policy" {
  compartment_id = var.tenancy_ocid
  description    = "Allow OKE nodes to read container images from OCIR"
  name           = "oke-nodes-ocir-policy"
  statements     = [
    "Allow dynamic-group ${oci_identity_dynamic_group.oke_nodes_dynamic_group.name} to read repos in tenancy"
  ]
}

resource "oci_containerengine_cluster" "oke_cluster" {
  compartment_id     = var.compartment_ocid
  kubernetes_version = "v1.33.1"
  name               = "ktor_oke_cluster"
  vcn_id             = oci_core_vcn.oke_vcn.id
  options {
    add_ons { is_kubernetes_dashboard_enabled = false }
    kubernetes_network_config {
      pods_cidr     = "10.244.0.0/16"
      services_cidr = "10.96.0.0/16"
    }
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
    size = 1
  }
  depends_on = [oci_identity_policy.oke_nodes_ocir_policy]
}

data "oci_containerengine_cluster_kube_config" "oke_kube_config" {
  cluster_id = oci_containerengine_cluster.oke_cluster.id
}

resource "local_file" "kubeconfig_file" {
  content  = data.oci_containerengine_cluster_kube_config.oke_kube_config.content
  filename = "${path.module}/kubeconfig"
}

resource "kubernetes_deployment" "ktor_app_deployment" {
  metadata {
    name   = "ktor-oke-app"
    labels = { app = "ktor-oke-app" }
  }
  spec {
    replicas = 2
    selector {
      match_labels = { app = "ktor-oke-app" }
    }
    template {
      metadata {
        labels = { app = "ktor-oke-app" }
      }
      spec {
        container {
          image = var.docker_image
          name  = "ktor-oke-app-container"
          port {
            container_port = 8080
          }
        }
      }
    }
  }
  depends_on = [oci_containerengine_node_pool.oke_node_pool]
}

resource "kubernetes_service" "ktor_app_service" {
  metadata {
    name = "ktor-oke-app-service"
  }
  spec {
    selector = {
      app = kubernetes_deployment.ktor_app_deployment.metadata[0].labels.app
    }
    port {
      port        = 80
      target_port = 8080
    }
    type = "LoadBalancer"
  }
  depends_on = [oci_containerengine_node_pool.oke_node_pool]
}

output "load_balancer_ip" {
  description = "Public IP address of the Ktor application's load balancer."
  value = try(
    kubernetes_service.ktor_app_service.status[0].load_balancer[0].ingress[0].ip,
    "creating..."
  )
}
