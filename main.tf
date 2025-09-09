provider "oci" {
  tenancy_ocid     = var.tenancy_ocid
  user_ocid        = var.user_ocid
  fingerprint      = var.fingerprint
  private_key      = var.private_key
  region           = var.region
}

resource "oci_containerengine_cluster" "oke_cluster" {
  compartment_id      = var.compartment_ocid
  name                = "ktor-cluster"
  kubernetes_version  = "v1.28.2"
  vcn_id              = oci_core_vcn.oke_vcn.id
  options {
    add_ons {
      is_kubernetes_dashboard_enabled = false
      is_tiller_enabled               = false
    }
  }
}

resource "oci_containerengine_node_pool" "oke_node_pool" {
  cluster_id         = oci_containerengine_cluster.oke_cluster.id
  compartment_id     = var.compartment_ocid
  name               = "free-tier-amd-pool"
  kubernetes_version = oci_containerengine_cluster.oke_cluster.kubernetes_version
  # NOTE: Using the Always Free AMD shape (VM.Standard.E2.1.Micro).
  # This shape has limited resources (1 OCPU, 1GB RAM) which may not be sufficient for some workloads.
  node_shape         = "VM.Standard.E2.1.Micro"
  node_source_details {
    image_id    = "ocid1.image.oc1.iad.amaaaaaangih7eyahd2fkief2vhj34ywop6zxc4bfxsw4mqaqkfsmwkvxcaq" 
    source_type = "image"
  }
  quantity_per_subnet = 1
  subnet_ids          = [oci_core_subnet.oke_subnet.id]
}


resource "oci_core_vcn" "oke_vcn" {
  compartment_id = var.compartment_ocid
  display_name   = "oke_vcn"
  cidr_block     = "10.0.0.0/16"
}

resource "oci_core_subnet" "oke_subnet" {
  compartment_id = var.compartment_ocid
  vcn_id         = oci_core_vcn.oke_vcn.id
  display_name   = "oke_subnet"
  cidr_block     = "10.0.1.0/24"
}

resource "oci_artifacts_container_repository" "ktor_repo" {
  compartment_id = var.compartment_ocid
  display_name = "ktor-app"
  is_public = true
}

resource "null_resource" "kubectl_apply" {
  depends_on = [oci_containerengine_node_pool.oke_node_pool]

  provisioner "local-exec" {
    command = <<-EOT
      oci ce cluster create-kubeconfig --cluster-id ${oci_containerengine_cluster.oke_cluster.id} --file $HOME/.kube/config --region ${var.region} --token-version 2.0.0
      kubectl apply -f - <<EOF
      apiVersion: apps/v1
      kind: Deployment
      metadata:
        name: ktor-app-deployment
      spec:
        replicas: 1
        selector:
          matchLabels:
            app: ktor-app
        template:
          metadata:
            labels:
              app: ktor-app
          spec:
            containers:
            - name: ktor-app
              image: "${var.region}.ocir.io/${var.tenancy_namespace}/${oci_artifacts_container_repository.ktor_repo.display_name}:latest"
              ports:
              - containerPort: 8080
      ---
      apiVersion: v1
      kind: Service
      metadata:
        name: ktor-app-service
      spec:
        type: LoadBalancer
        ports:
        - port: 80
          targetPort: 8080
        selector:
          app: ktor-app
      EOF
    EOT
  }
}


