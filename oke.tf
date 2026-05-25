# --- OKE Cluster ---

resource "oci_containerengine_cluster" "this" {
  compartment_id     = var.compartment_ocid
  kubernetes_version = var.kubernetes_version
  name               = var.cluster_name
  vcn_id             = oci_core_vcn.this.id
  type               = "BASIC_CLUSTER"

  cluster_pod_network_options {
    cni_type = "OCI_VCN_IP_NATIVE"
  }

  endpoint_config {
    is_public_ip_enabled = true
    subnet_id            = oci_core_subnet.kubernetes_api_endpoint.id
  }

  options {
    admission_controller_options {
      is_pod_security_policy_enabled = false
    }
    persistent_volume_config {
      freeform_tags = {
        "OKEclusterName" = var.cluster_name
      }
    }
    service_lb_config {
      freeform_tags = {
        "OKEclusterName" = var.cluster_name
      }
    }
    service_lb_subnet_ids = [oci_core_subnet.service_lb.id]
  }
}

# --- OKE Node Pool ---

resource "oci_containerengine_node_pool" "this" {
  cluster_id         = oci_containerengine_cluster.this.id
  compartment_id     = var.compartment_ocid
  kubernetes_version = var.kubernetes_version
  name               = "pool1"
  node_shape         = var.node_shape

  node_metadata = {
    user_data = base64encode(<<-EOT
      #!/bin/bash
      curl --fail -H "Authorization: Bearer Oracle" -L0 http://169.254.169.254/opc/v2/instance/metadata/oke_init_script | base64 --decode >/var/run/oke-init.sh

      wget https://github.com/oracle-devrel/oke-credential-provider-for-ocir/releases/download/v0.1.1/oke-credential-provider-for-ocir-linux-arm64 -O /usr/local/bin/credential-provider-oke
      wget https://github.com/oracle-devrel/oke-credential-provider-for-ocir/releases/download/v0.1.1/credential-provider-config.yaml -P /etc/kubernetes/

      chmod 755 /usr/local/bin/credential-provider-oke
      bash /var/run/oke-init.sh --kubelet-extra-args "--image-credential-provider-bin-dir=/usr/local/bin/ --image-credential-provider-config=/etc/kubernetes/credential-provider-config.yaml"
    EOT
    )
  }

  freeform_tags = {
    "OKEnodePoolName" = "pool1"
  }

  initial_node_labels {
    key   = "name"
    value = var.cluster_name
  }

  node_config_details {
    size = var.node_pool_size
    placement_configs {
      availability_domain = data.oci_identity_availability_domains.this.availability_domains[0].name
      subnet_id           = oci_core_subnet.node.id
    }
    placement_configs {
      availability_domain = data.oci_identity_availability_domains.this.availability_domains[1].name
      subnet_id           = oci_core_subnet.node.id
    }
    placement_configs {
      availability_domain = data.oci_identity_availability_domains.this.availability_domains[2].name
      subnet_id           = oci_core_subnet.node.id
    }
    freeform_tags = {
      "OKEnodePoolName" = "pool1"
    }
    node_pool_pod_network_option_details {
      cni_type       = "OCI_VCN_IP_NATIVE"
      pod_subnet_ids = [oci_core_subnet.node.id]
    }
  }

  node_eviction_node_pool_settings {
    eviction_grace_duration = "PT60M"
  }

  node_shape_config {
    memory_in_gbs = var.node_memory_gbs
    ocpus         = var.node_ocpus
  }

  node_source_details {
    image_id    = data.oci_core_images.this.images[0].id
    source_type = "IMAGE"
  }
}

# --- Data Sources ---

data "oci_identity_availability_domains" "this" {
  compartment_id = var.compartment_ocid
}

data "oci_core_images" "this" {
  compartment_id           = var.compartment_ocid
  operating_system         = "Oracle Linux"
  operating_system_version = "8"
  shape                    = var.node_shape
  sort_by                  = "TIMECREATED"
  sort_order               = "DESC"
}

# --- OCI Object Storage namespace (for backup bucket) ---

data "oci_objectstorage_namespace" "this" {
  compartment_id = var.compartment_ocid
}