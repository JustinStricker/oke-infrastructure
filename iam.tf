# --- Dynamic Group for OKE Worker Nodes ---

resource "oci_identity_dynamic_group" "oke_nodes" {
  compartment_id = var.tenancy_ocid
  name           = "oke_nodes_dynamic_group_tf"
  description    = "Dynamic group for all OKE worker nodes in a compartment, managed by OpenTofu."
  matching_rule  = "ALL {instance.compartment.id = '${var.compartment_ocid}'}"
}

# --- Policy for OCIR Access ---

resource "oci_identity_policy" "oke_nodes_ocir" {
  compartment_id = var.tenancy_ocid
  name           = "oke_nodes_ocir_policy_tf"
  description    = "Policy to allow OKE worker nodes to pull images from OCIR, managed by OpenTofu."
  statements = [
    "Allow dynamic-group ${oci_identity_dynamic_group.oke_nodes.name} to read repos in compartment id ${var.compartment_ocid}"
  ]
}
