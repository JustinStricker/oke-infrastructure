# --- Dynamic Group for OKE Worker Nodes ---

resource "oci_identity_dynamic_group" "oke_nodes" {
  compartment_id = var.tenancy_ocid
  name           = "oke_nodes_dynamic_group_tf"
  description    = "Dynamic group for all OKE worker nodes in a compartment, managed by OpenTofu."
  matching_rule  = "ALL {instance.compartment.id = '${var.compartment_ocid}'}"
}

# --- Policy for OCIR Access and CSI Driver ---

resource "oci_identity_policy" "oke_nodes" {
  compartment_id = var.tenancy_ocid
  name           = "oke_nodes_policy_tf"
  description    = "Policy for OKE worker nodes: OCIR pull, CSI volume management, and network access."
  statements = [
    "Allow dynamic-group ${oci_identity_dynamic_group.oke_nodes.name} to read repos in compartment id ${var.compartment_ocid}",
    "Allow dynamic-group ${oci_identity_dynamic_group.oke_nodes.name} to manage volume-family in compartment id ${var.compartment_ocid}",
    "Allow dynamic-group ${oci_identity_dynamic_group.oke_nodes.name} to use virtual-network-family in compartment id ${var.compartment_ocid}",
    "Allow dynamic-group ${oci_identity_dynamic_group.oke_nodes.name} to read instance-family in compartment id ${var.compartment_ocid}"
  ]
}
