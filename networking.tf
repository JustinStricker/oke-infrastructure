# --- VCN ---

resource "oci_core_vcn" "this" {
  cidr_block     = "10.0.0.0/16"
  compartment_id = var.compartment_ocid
  display_name   = "oke-vcn-${var.cluster_name}"
  dns_label      = "okeinfrastructu"
}

# --- Internet Gateway ---

resource "oci_core_internet_gateway" "this" {
  compartment_id = var.compartment_ocid
  display_name   = "oke-igw-${var.cluster_name}"
  enabled        = true
  vcn_id         = oci_core_vcn.this.id
}

# --- Service Gateway (required for OKE worker nodes to register) ---

data "oci_core_services" "all_region_services" {
  filter {
    name   = "name"
    values = ["All .* Services In Oracle Services Network"]
    regex  = true
  }
}

resource "oci_core_service_gateway" "this" {
  compartment_id = var.compartment_ocid
  display_name   = "oke-svcgw-${var.cluster_name}"
  vcn_id         = oci_core_vcn.this.id
  services {
    service_id = data.oci_core_services.all_region_services.services[0].id
  }
}

# --- Default Route Table (public subnets) ---

resource "oci_core_default_route_table" "this" {
  manage_default_resource_id = oci_core_vcn.this.default_route_table_id
  display_name               = "oke-public-routetable-${var.cluster_name}"

  route_rules {
    description       = "Traffic to/from internet"
    destination       = "0.0.0.0/0"
    destination_type  = "CIDR_BLOCK"
    network_entity_id = oci_core_internet_gateway.this.id
  }
  route_rules {
    description       = "Traffic to/from Oracle Services Network (OKE node registration)"
    destination       = data.oci_core_services.all_region_services.services[0].cidr_block
    destination_type  = "SERVICE_CIDR_BLOCK"
    network_entity_id = oci_core_service_gateway.this.id
  }
}

# --- Subnets ---

resource "oci_core_subnet" "kubernetes_api_endpoint" {
  cidr_block                 = "10.0.0.0/28"
  compartment_id             = var.compartment_ocid
  display_name               = "oke-k8sApiEndpoint-subnet-${var.cluster_name}-regional"
  dns_label                  = "subce83bfcac"
  vcn_id                     = oci_core_vcn.this.id
  route_table_id             = oci_core_vcn.this.default_route_table_id
  security_list_ids          = [oci_core_security_list.kubernetes_api_endpoint.id]
  prohibit_public_ip_on_vnic = false
}

resource "oci_core_subnet" "node" {
  cidr_block                 = "10.0.10.0/24"
  compartment_id             = var.compartment_ocid
  display_name               = "oke-nodesubnet-${var.cluster_name}-regional"
  dns_label                  = "subf53e889a4"
  vcn_id                     = oci_core_vcn.this.id
  route_table_id             = oci_core_vcn.this.default_route_table_id
  security_list_ids          = [oci_core_security_list.node.id]
  prohibit_public_ip_on_vnic = false
}

resource "oci_core_subnet" "service_lb" {
  cidr_block                 = "10.0.20.0/24"
  compartment_id             = var.compartment_ocid
  display_name               = "oke-svclbsubnet-${var.cluster_name}-regional"
  dns_label                  = "lbsub671fac0f2"
  vcn_id                     = oci_core_vcn.this.id
  route_table_id             = oci_core_vcn.this.default_route_table_id
  security_list_ids          = [oci_core_vcn.this.default_security_list_id]
  prohibit_public_ip_on_vnic = false
}

# --- Security Lists ---

resource "oci_core_security_list" "node" {
  compartment_id = var.compartment_ocid
  vcn_id         = oci_core_vcn.this.id
  display_name   = "oke-nodeseclist-${var.cluster_name}"

  egress_security_rules {
    description      = "Allow pods on one worker node to communicate with pods on other worker nodes"
    destination      = "10.0.10.0/24"
    destination_type = "CIDR_BLOCK"
    protocol         = "all"
    stateless        = false
  }
  egress_security_rules {
    description      = "Access to Kubernetes API Endpoint"
    destination      = "10.0.0.0/28"
    destination_type = "CIDR_BLOCK"
    protocol         = "6"
    stateless        = false
  }
  egress_security_rules {
    description      = "Kubernetes worker to control plane communication"
    destination      = "10.0.0.0/28"
    destination_type = "CIDR_BLOCK"
    protocol         = "6"
    stateless        = false
  }
  egress_security_rules {
    description      = "Path discovery"
    destination      = "10.0.0.0/28"
    destination_type = "CIDR_BLOCK"
    icmp_options {
      code = 4
      type = 3
    }
    protocol  = "1"
    stateless = false
  }
  egress_security_rules {
      description      = "Allow nodes to communicate with OKE to ensure correct start-up and continued functioning"
      destination      = data.oci_core_services.all_region_services.services[0].cidr_block
      destination_type = "SERVICE_CIDR_BLOCK"
    protocol         = "6"
    stateless        = false
  }
  egress_security_rules {
    description      = "ICMP Access from Kubernetes Control Plane"
    destination      = "0.0.0.0/0"
    destination_type = "CIDR_BLOCK"
    icmp_options {
      code = 4
      type = 3
    }
    protocol  = "1"
    stateless = false
  }
  egress_security_rules {
    description      = "Worker Nodes access to Internet"
    destination      = "0.0.0.0/0"
    destination_type = "CIDR_BLOCK"
    protocol         = "all"
    stateless        = false
  }
  ingress_security_rules {
    description = "Allow pods on one worker node to communicate with pods on other worker nodes"
    protocol    = "all"
    source      = "10.0.10.0/24"
    stateless   = false
  }
  ingress_security_rules {
    description = "Path discovery"
    icmp_options {
      code = 4
      type = 3
    }
    protocol  = "1"
    source    = "10.0.0.0/28"
    stateless = false
  }
  ingress_security_rules {
    description = "TCP access from Kubernetes Control Plane"
    protocol    = "6"
    source      = "10.0.0.0/28"
    stateless   = false
  }
  ingress_security_rules {
    description = "Inbound SSH traffic to worker nodes"
    protocol    = "6"
    source      = "0.0.0.0/0"
    stateless   = false
  }
}

resource "oci_core_security_list" "kubernetes_api_endpoint" {
  compartment_id = var.compartment_ocid
  vcn_id         = oci_core_vcn.this.id
  display_name   = "oke-k8sApiEndpoint-${var.cluster_name}"

  egress_security_rules {
      description      = "Allow Kubernetes Control Plane to communicate with OKE"
      destination      = data.oci_core_services.all_region_services.services[0].cidr_block
      destination_type = "SERVICE_CIDR_BLOCK"
    protocol         = "6"
    stateless        = false
  }
  egress_security_rules {
    description      = "All traffic to worker nodes"
    destination      = "10.0.10.0/24"
    destination_type = "CIDR_BLOCK"
    protocol         = "6"
    stateless        = false
  }
  egress_security_rules {
    description      = "Path discovery"
    destination      = "10.0.10.0/24"
    destination_type = "CIDR_BLOCK"
    icmp_options {
      code = 4
      type = 3
    }
    protocol  = "1"
    stateless = false
  }
  ingress_security_rules {
    description = "External access to Kubernetes API endpoint"
    protocol    = "6"
    source      = "0.0.0.0/0"
    stateless   = false
  }
  ingress_security_rules {
    description = "Kubernetes worker to Kubernetes API endpoint communication"
    protocol    = "6"
    source      = "10.0.10.0/24"
    stateless   = false
  }
  ingress_security_rules {
    description = "Kubernetes worker to control plane communication"
    protocol    = "6"
    source      = "10.0.10.0/24"
    stateless   = false
  }
  ingress_security_rules {
    description = "Path discovery"
    icmp_options {
      code = 4
      type = 3
    }
    protocol  = "1"
    source    = "10.0.10.0/24"
    stateless = false
  }
}