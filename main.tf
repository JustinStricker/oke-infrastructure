# Terraform block to define provider requirements
terraform {
  required_providers {
    oci = {
      source  = "oracle/oci"
      version = ">= 5.0"
    }
  }
}

# Provider configuration reverted to simple block.
# Assumes authentication is handled outside of this file (e.g., environment variables).
provider "oci" {}

# --- Input Variables ---

variable "compartment_ocid" {
  description = "The OCID of the compartment where resources will be created."
  type        = string
}

variable "kubernetes_version" {
  description = "The version of Kubernetes to deploy for the OKE cluster."
  type        = string
  default     = "v1.28.2" # Using a more current, stable version
}

# --- Networking Resources ---

resource "oci_core_vcn" "generated_oci_core_vcn" {
  cidr_block     = "10.0.0.0/16"
  compartment_id = var.compartment_ocid
  display_name   = "oke-vcn-automated"
  dns_label      = "okevcnauto"
}

resource "oci_core_internet_gateway" "generated_oci_core_internet_gateway" {
  compartment_id = var.compartment_ocid
  display_name   = "oke-igw-automated"
  enabled        = true
  vcn_id         = oci_core_vcn.generated_oci_core_vcn.id
}

resource "oci_core_default_route_table" "generated_oci_core_default_route_table" {
  manage_default_resource_id = oci_core_vcn.generated_oci_core_vcn.default_route_table_id

  route_rules {
    destination       = "0.0.0.0/0"
    destination_type  = "CIDR_BLOCK"
    network_entity_id = oci_core_internet_gateway.generated_oci_core_internet_gateway.id
  }
}

# --- Subnets ---

resource "oci_core_subnet" "kubernetes_api_endpoint_subnet" {
  cidr_block                 = "10.0.0.0/28"
  compartment_id             = var.compartment_ocid
  display_name               = "oke-k8s-api-subnet"
  dns_label                  = "k8sapi"
  vcn_id                     = oci_core_vcn.generated_oci_core_vcn.id
  route_table_id             = oci_core_vcn.generated_oci_core_vcn.default_route_table_id
  security_list_ids          = [oci_core_security_list.kubernetes_api_endpoint_sec_list.id]
  prohibit_public_ip_on_vnic = false
}

resource "oci_core_subnet" "node_subnet" {
  cidr_block                 = "10.0.10.0/24"
  compartment_id             = var.compartment_ocid
  display_name               = "oke-node-subnet"
  dns_label                  = "nodes"
  vcn_id                     = oci_core_vcn.generated_oci_core_vcn.id
  route_table_id             = oci_core_vcn.generated_oci_core_vcn.default_route_table_id
  security_list_ids          = [oci_core_security_list.node_sec_list.id]
  prohibit_public_ip_on_vnic = false
}

resource "oci_core_subnet" "service_lb_subnet" {
  cidr_block                 = "10.0.20.0/24"
  compartment_id             = var.compartment_ocid
  display_name               = "oke-lb-subnet"
  dns_label                  = "lbs"
  vcn_id                     = oci_core_vcn.generated_oci_core_vcn.id
  route_table_id             = oci_core_vcn.generated_oci_core_vcn.default_route_table_id
  security_list_ids          = [oci_core_vcn.generated_oci_core_vcn.default_security_list_id] # Using default for simplicity, can be customized
  prohibit_public_ip_on_vnic = false
}

# --- Security Lists ---
# Reverted to the original, more detailed security list definitions.

resource "oci_core_security_list" "node_sec_list" {
  compartment_id = var.compartment_ocid
  vcn_id         = oci_core_vcn.generated_oci_core_vcn.id
  display_name   = "oke-nodeseclist-quick-oke-infrastructure-a8536cb57"

  egress_security_rules {
    description      = "Allow pods on one worker node to communicate with pods on other worker nodes"
    destination      = "10.0.10.0/24"
    destination_type = "CIDR_BLOCK"
    protocol         = "all"
    stateless        = "false"
  }
  egress_security_rules {
    description      = "Access to Kubernetes API Endpoint"
    destination      = "10.0.0.0/28"
    destination_type = "CIDR_BLOCK"
    protocol         = "6"
    stateless        = "false"
  }
  egress_security_rules {
    description      = "Kubernetes worker to control plane communication"
    destination      = "10.0.0.0/28"
    destination_type = "CIDR_BLOCK"
    protocol         = "6"
    stateless        = "false"
  }
  egress_security_rules {
    description      = "Path discovery"
    destination      = "10.0.0.0/28"
    destination_type = "CIDR_BLOCK"
    icmp_options {
      code = "4"
      type = "3"
    }
    protocol  = "1"
    stateless = "false"
  }
  egress_security_rules {
    description      = "Allow nodes to communicate with OKE to ensure correct start-up and continued functioning"
    destination      = "all-iad-services-in-oracle-services-network"
    destination_type = "SERVICE_CIDR_BLOCK"
    protocol         = "6"
    stateless        = "false"
  }
  egress_security_rules {
    description      = "ICMP Access from Kubernetes Control Plane"
    destination      = "0.0.0.0/0"
    destination_type = "CIDR_BLOCK"
    icmp_options {
      code = "4"
      type = "3"
    }
    protocol  = "1"
    stateless = "false"
  }
  egress_security_rules {
    description      = "Worker Nodes access to Internet"
    destination      = "0.0.0.0/0"
    destination_type = "CIDR_BLOCK"
    protocol         = "all"
    stateless        = "false"
  }
  ingress_security_rules {
    description = "Allow pods on one worker node to communicate with pods on other worker nodes"
    protocol    = "all"
    source      = "10.0.10.0/24"
    stateless   = "false"
  }
  ingress_security_rules {
    description = "Path discovery"
    icmp_options {
      code = "4"
      type = "3"
    }
    protocol  = "1"
    source    = "10.0.0.0/28"
    stateless = "false"
  }
  ingress_security_rules {
    description = "TCP access from Kubernetes Control Plane"
    protocol    = "6"
    source      = "10.0.0.0/28"
    stateless   = "false"
  }
  ingress_security_rules {
    description = "Inbound SSH traffic to worker nodes"
    protocol    = "6"
    source      = "0.0.0.0/0"
    stateless   = "false"
  }
}

resource "oci_core_security_list" "kubernetes_api_endpoint_sec_list" {
  compartment_id = var.compartment_ocid
  vcn_id         = oci_core_vcn.generated_oci_core_vcn.id
  display_name   = "oke-k8sApiEndpoint-quick-oke-infrastructure-a8536cb57"

  egress_security_rules {
    description      = "Allow Kubernetes Control Plane to communicate with OKE"
    destination      = "all-iad-services-in-oracle-services-network"
    destination_type = "SERVICE_CIDR_BLOCK"
    protocol         = "6"
    stateless        = "false"
  }
  egress_security_rules {
    description      = "All traffic to worker nodes"
    destination      = "10.0.10.0/24"
    destination_type = "CIDR_BLOCK"
    protocol         = "6"
    stateless        = "false"
  }
  egress_security_rules {
    description      = "Path discovery"
    destination      = "10.0.10.0/24"
    destination_type = "CIDR_BLOCK"
    icmp_options {
      code = "4"
      type = "3"
    }
    protocol  = "1"
    stateless = "false"
  }
  ingress_security_rules {
    description = "External access to Kubernetes API endpoint"
    protocol    = "6"
    source      = "0.0.0.0/0"
    stateless   = "false"
  }
  ingress_security_rules {
    description = "Kubernetes worker to Kubernetes API endpoint communication"
    protocol    = "6"
    source      = "10.0.10.0/24"
    stateless   = "false"
  }
  ingress_security_rules {
    description = "Kubernetes worker to control plane communication"
    protocol    = "6"
    source      = "10.0.10.0/24"
    stateless   = "false"
  }
  ingress_security_rules {
    description = "Path discovery"
    icmp_options {
      code = "4"
      type = "3"
    }
    protocol  = "1"
    source    = "10.0.10.0/24"
    stateless = "false"
  }
}

# --- OKE Cluster Resources ---

resource "oci_containerengine_cluster" "generated_oci_containerengine_cluster" {
  compartment_id     = var.compartment_ocid
  kubernetes_version = "v1.33.1" # Reverted to original version
  name               = "oke-cluster-automated"
  vcn_id             = oci_core_vcn.generated_oci_core_vcn.id
  endpoint_config {
    subnet_id            = oci_core_subnet.kubernetes_api_endpoint_subnet.id
    is_public_ip_enabled = true
  }
  options {
    service_lb_subnet_ids = [oci_core_subnet.service_lb_subnet.id]
  }
}

resource "oci_containerengine_node_pool" "node_pool" {
  cluster_id         = oci_containerengine_cluster.generated_oci_containerengine_cluster.id
  compartment_id     = var.compartment_ocid
  kubernetes_version = "v1.33.1" # Reverted to original version
  name               = "nodepool1"
  node_shape         = "VM.Standard.A1.Flex"
  node_shape_config {
    memory_in_gbs = 6
    ocpus         = 1
  }
  node_source_details {
    image_id    = "ocid1.image.oc1.iad.aaaaaaaawvjqjuetmdkenuoqttvmyfsb22l7qoq4sru3u6z2dfoog4yryuea" # Example Image OCID, consider using a variable
    source_type = "IMAGE"
  }
  node_config_details {
    size = 2 # Starting with a smaller node count
    placement_configs {
      availability_domain = "SpPm:US-ASHBURN-AD-1"
      subnet_id           = oci_core_subnet.node_subnet.id
    }
    placement_configs {
      availability_domain = "SpPm:US-ASHBURN-AD-2"
      subnet_id           = oci_core_subnet.node_subnet.id
    }
    placement_configs {
      availability_domain = "SpPm:US-ASHBURN-AD-3"
      subnet_id           = oci_core_subnet.node_subnet.id
    }
  }
}
