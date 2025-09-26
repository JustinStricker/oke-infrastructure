terraform {
  required_providers {
    oci = {
      source  = "oracle/oci"
      version = ">= 5.0"
    }
  }
}

provider "oci" {}

variable "compartment_ocid" {
  description = "Compartment OCID"
  type        = string
}

variable "tenancy_ocid" {
  description = "Tenancy OCID"
  type        = string
}

variable "db_admin_username" {
  description = "MySQL admin username"
  type        = string
  sensitive   = true
}

variable "db_admin_password" {
  description = "MySQL admin password"
  type        = string
  sensitive   = true
}

# VCN with DNS resolution enabled (best practice)
resource "oci_core_vcn" "generated_oci_core_vcn" {
  cidr_block     = "10.0.0.0/16"
  compartment_id = var.compartment_ocid
  display_name   = "oke-vcn-quick-oke-infrastructure-a8536cb57"
  dns_label      = "okeinfrastructu"
  # Enable DNS support (optional, recommended for OKE)
  # enable_dns_support = true
  # enable_dns_hostnames = true
}

resource "oci_core_internet_gateway" "generated_oci_core_internet_gateway" {
  compartment_id = var.compartment_ocid
  display_name   = "oke-igw-quick-oke-infrastructure-a8536cb57"
  enabled        = true
  vcn_id         = oci_core_vcn.generated_oci_core_vcn.id
}

resource "oci_core_nat_gateway" "oke_nat_gateway" {
  compartment_id = var.compartment_ocid
  vcn_id         = oci_core_vcn.generated_oci_core_vcn.id
  display_name   = "oke-nat-gateway-for-nodes"
}

# Public route table with IGW for internet traffic
resource "oci_core_default_route_table" "generated_oci_core_default_route_table" {
  manage_default_resource_id = oci_core_vcn.generated_oci_core_vcn.default_route_table_id
  display_name               = "oke-public-routetable-oke-infrastructure-a8536cb57"
  route_rules {
    description       = "traffic to/from internet"
    destination       = "0.0.0.0/0"
    destination_type  = "CIDR_BLOCK"
    network_entity_id = oci_core_internet_gateway.generated_oci_core_internet_gateway.id
  }
}

# Private route table for node subnet with NAT gateway egress
resource "oci_core_route_table" "private_node_route_table" {
  compartment_id = var.compartment_ocid
  vcn_id         = oci_core_vcn.generated_oci_core_vcn.id
  display_name   = "oke-private-node-routetable"
  route_rules {
    destination       = "0.0.0.0/0"
    network_entity_id = oci_core_nat_gateway.oke_nat_gateway.id
  }
}

# Subnets

resource "oci_core_subnet" "kubernetes_api_endpoint_subnet" {
  cidr_block                 = "10.0.0.0/28"
  compartment_id             = var.compartment_ocid
  display_name               = "oke-k8sApiEndpoint-subnet-quick-oke-infrastructure-a8536cb57-regional"
  dns_label                  = "subce83bfcac"
  vcn_id                     = oci_core_vcn.generated_oci_core_vcn.id
  route_table_id             = oci_core_vcn.generated_oci_core_vcn.default_route_table_id
  security_list_ids          = [oci_core_security_list.kubernetes_api_endpoint_sec_list.id]
  prohibit_public_ip_on_vnic = false
}

resource "oci_core_subnet" "node_subnet" {
  cidr_block                 = "10.0.10.0/24"
  compartment_id             = var.compartment_ocid
  display_name               = "oke-nodesubnet-quick-oke-infrastructure-a8536cb57-regional"
  dns_label                  = "subf53e889a4"
  vcn_id                     = oci_core_vcn.generated_oci_core_vcn.id
  route_table_id             = oci_core_route_table.private_node_route_table.id
  security_list_ids          = [oci_core_security_list.node_sec_list.id]
  prohibit_public_ip_on_vnic = false # Changed from true to false to allow public IPs for troubleshooting and connectivity
}

resource "oci_core_subnet" "service_lb_subnet" {
  cidr_block                 = "10.0.20.0/24"
  compartment_id             = var.compartment_ocid
  display_name               = "oke-svclbsubnet-quick-oke-infrastructure-a8536cb57-regional"
  dns_label                  = "lbsub671fac0f2"
  vcn_id                     = oci_core_vcn.generated_oci_core_vcn.id
  route_table_id             = oci_core_vcn.generated_oci_core_vcn.default_route_table_id
  security_list_ids          = [oci_core_vcn.generated_oci_core_vcn.default_security_list_id]
  prohibit_public_ip_on_vnic = false
}

# Security Lists

resource "oci_core_security_list" "node_sec_list" {
  compartment_id = var.compartment_ocid
  vcn_id         = oci_core_vcn.generated_oci_core_vcn.id
  display_name   = "oke-nodeseclist-enhanced"

  # Egress rules - allow all necessary traffic to reach API endpoint and internet
  egress_security_rules {
    description = "Allow worker nodes to connect to Kubernetes API and internet"
    destination = "0.0.0.0/0"
    protocol    = "all"
    stateless   = false
  }

  # Ingress rules - allow control plane and intra-node communication
  ingress_security_rules {
    description = "Allow all intra-node subnet communication"
    source      = "10.0.10.0/24"
    protocol    = "all"
    stateless   = false
  }

  ingress_security_rules {
    description = "Allow traffic from Kubernetes API endpoint subnet to nodes"
    source      = "10.0.0.0/28"
    protocol    = "6"
    tcp_options {
      min = 443
      max = 443
    }
    stateless = false
  }

  ingress_security_rules {
    description = "Allow inbound SSH from anywhere"
    source      = "0.0.0.0/0"
    protocol    = "6"
    tcp_options {
      min = 22
      max = 22
    }
    stateless = false
  }
}

resource "oci_core_security_list" "kubernetes_api_endpoint_sec_list" {
  compartment_id = var.compartment_ocid
  vcn_id         = oci_core_vcn.generated_oci_core_vcn.id
  display_name   = "oke-k8sApiEndpoint-quick-oke-infrastructure-a8536cb57"

  egress_security_rules {
    description = "Allow API endpoint to communicate with nodes"
    destination = "10.0.10.0/24"
    protocol    = "6"
    tcp_options {
      min = 443
      max = 443
    }
  }

  ingress_security_rules {
    description = "Allow external access to Kubernetes API endpoint on port 6443"
    protocol    = "6"
    source      = "0.0.0.0/0"
    tcp_options {
      min = 6443
      max = 6443
    }
  }
}

# OKE Cluster Resource

resource "oci_containerengine_cluster" "generated_oci_containerengine_cluster" {
  compartment_id     = var.compartment_ocid
  kubernetes_version = "v1.33.1"
  name               = "oke-infrastructure"
  vcn_id             = oci_core_vcn.generated_oci_core_vcn.id
  type               = "BASIC_CLUSTER"

  endpoint_config {
    is_public_ip_enabled = true
    subnet_id            = oci_core_subnet.kubernetes_api_endpoint_subnet.id
  }

  options {
    admission_controller_options {
      is_pod_security_policy_enabled = false
    }
    persistent_volume_config {
      freeform_tags = {
        "OKEclusterName" = "oke-infrastructure"
      }
    }
    service_lb_config {
      freeform_tags = {
        "OKEclusterName" = "oke-infrastructure"
      }
    }
    service_lb_subnet_ids = [oci_core_subnet.service_lb_subnet.id]
  }
}

# Node Pool

resource "oci_containerengine_node_pool" "create_node_pool_details0" {
  cluster_id         = oci_containerengine_cluster.generated_oci_containerengine_cluster.id
  compartment_id     = var.compartment_ocid
  kubernetes_version = "v1.33.1"
  name               = "pool1"
  node_shape         = "VM.Standard.A1.Flex"

  node_metadata = {
    user_data = base64encode(<<-EOT
      #!/bin/bash
      curl --fail -H "Authorization: Bearer Oracle" -L0 http://169.254.169.254/opc/v2/instance/metadata/oke_init_script | base64 --decode >/var/run/oke-init.sh
      wget https://github.com/oracle-devrel/oke-credential-provider-for-ocir/releases/latest/download/oke-credential-provider-for-ocir-linux-arm64 -O /usr/local/bin/credential-provider-oke
      wget https://github.com/oracle-devrel/oke-credential-provider-for-ocir/releases/latest/download/credential-provider-config.yaml -P /etc/kubernetes/
      sudo chmod 755 /usr/local/bin/credential-provider-oke
      bash /var/run/oke-init.sh --kubelet-extra-args "--image-credential-provider-bin-dir=/usr/local/bin/ --image-credential-provider-config=/etc/kubernetes/credential-provider-config.yaml"
    EOT
    )
  }

  node_config_details {
    size = 4

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

  node_shape_config {
    memory_in_gbs = 6
    ocpus         = 1
  }

  node_source_details {
    image_id    = "ocid1.image.oc1.iad.aaaaaaaawvjqjuetmdkenuoqttvmyfsb22l7qoq4sru3u6z2dfoog4yryuea"
    source_type = "IMAGE"
  }
}

# MySQL HeatWave resource and IAM resources remain unchanged here

resource "oci_mysql_mysql_db_system" "mysql" {
  admin_username          = var.db_admin_username
  admin_password          = var.db_admin_password
  compartment_id          = var.compartment_ocid
  subnet_id               = oci_core_subnet.node_subnet.id
  availability_domain     = data.oci_identity_availability_domains.ads.availability_domains[2].name
  shape_name              = "MySQL.Free"
  display_name            = "mysql"
  data_storage_size_in_gb = 50
  port                    = 3306
  port_x                  = 33060
  crash_recovery          = "ENABLED"
  database_management     = "DISABLED"
  deletion_policy {
    is_delete_protected = false
  }
}

data "oci_identity_availability_domains" "ads" {
  compartment_id = var.tenancy_ocid
}

resource "oci_identity_dynamic_group" "oke_nodes_dynamic_group" {
  compartment_id = var.tenancy_ocid
  name           = "oke_nodes_dynamic_group_tf"
  description    = "Dynamic group for all OKE worker nodes in a compartment, managed by Terraform."
  matching_rule  = "ALL {instance.compartment.id = '${var.compartment_ocid}'}"
}

resource "oci_identity_policy" "oke_nodes_ocir_policy" {
  compartment_id = var.tenancy_ocid
  name           = "oke_nodes_ocir_policy_tf"
  description    = "Policy to allow OKE worker nodes to pull images from OCIR, managed by Terraform."
  statements     = ["Allow dynamic-group ${oci_identity_dynamic_group.oke_nodes_dynamic_group.name} to read repos in compartment id ${var.compartment_ocid}"]
}

output "mysql_db_system_private_ip" {
  description = "The private IP address of the MySQL DB System."
  value       = oci_mysql_mysql_db_system.mysql.ip_address
}
