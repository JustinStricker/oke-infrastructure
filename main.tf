name: Build and Deploy to OKE

on:
  push:
    branches: [ "main" ]

jobs:
  build-and-deploy:
    runs-on: ubuntu-latest
    env:
      OCI_CLI_TENANCY: ${{ secrets.OCI_TENANCY_OCID }}
      OCI_CLI_USER: ${{ secrets.OCI_USER_OCID }}
      OCI_CLI_FINGERPRINT: ${{ secrets.OCI_FINGERPRINT }}
      OCI_CLI_KEY_CONTENT: ${{ secrets.OCI_PRIVATE_KEY }}
      OCI_CLI_REGION: ${{ secrets.OCI_REGION }}

      TF_VAR_tenancy_ocid: ${{ secrets.OCI_TENANCY_OCID }}
      TF_VAR_user_ocid: ${{ secrets.OCI_USER_OCID }}
      TF_VAR_fingerprint: ${{ secrets.OCI_FINGERPRINT }}
      TF_VAR_private_key: ${{ secrets.OCI_PRIVATE_KEY }}
      TF_VAR_region: ${{ secrets.OCI_REGION }}

    steps:
      - name: Checkout Repository
        uses: actions/checkout@v3

      - name: Setup OCI Config File
        run: |
          set -eo pipefail
          mkdir -p ~/.oci
          echo "${{ secrets.OCI_PRIVATE_KEY }}" > ~/.oci/key.pem
          chmod 600 ~/.oci/key.pem
          cat > ~/.oci/config <<-EOF
          [DEFAULT]
          user=${{ secrets.OCI_USER_OCID }}
          fingerprint=${{ secrets.OCI_FINGERPRINT }}
          tenancy=${{ secrets.OCI_TENANCY_OCID }}
          region=${{ secrets.OCI_REGION }}
          key_file=~/.oci/key.pem
          EOF

      - name: Get or create an OCIR Repository
        id: get-ocir-repository
        uses: oracle-actions/get-ocir-repository@v1.3.0
        with:
          name: ktor-oke-app
          compartment: ${{ secrets.OCI_COMPARTMENT_OCID }}

      - name: Log into OCIR
        uses: oracle-actions/login-ocir@v1.3.0
        id: login-ocir
        with:
          auth_token: ${{ secrets.OCI_AUTH_TOKEN }}

      - name: Cache Gradle
        uses: actions/cache@v3
        with:
          path: ~/.gradle/caches
          key: gradle-${{ runner.os }}-${{ hashFiles('**/*.gradle*', '**/gradle-wrapper.properties') }}
          restore-keys: gradle-${{ runner.os }}-

      - name: Build with Gradle
        uses: gradle/gradle-build-action@v2
        with:
          gradle-version: '8.5'
          arguments: shadowJar

      - name: Build and Push Docker Image
        id: build-image
        run: |
          IMAGE_TAG=$GITHUB_SHA
          IMAGE_URL="${{ steps.get-ocir-repository.outputs.repo_path }}:$IMAGE_TAG"
          docker build -t $IMAGE_URL .
          docker push $IMAGE_URL
          echo "image_url=$IMAGE_URL" >> $GITHUB_OUTPUT

      - name: Setup Terraform
        uses: hashicorp/setup-terraform@v2
      
      - name: Install OCI CLI
        run: |
          curl -L -o oci-cli.sh https://raw.githubusercontent.com/oracle/oci-cli/master/scripts/install/install.sh
          bash oci-cli.sh --accept-all-defaults
          echo "/home/runner/bin" >> $GITHUB_PATH
          /home/runner/bin/oci --version
          
      - name: Terraform Init
        run: terraform init
        
      - name: Terraform Apply
        run: |
          terraform apply -auto-approve \
            -var="docker_image=${{ steps.build-image.outputs.image_url }}" \
            -var="node_image_ocid=${{ secrets.OCI_NODE_IMAGE_OCID }}" \
            -var="compartment_ocid=${{ secrets.OCI_COMPARTMENT_OCID }}" \
            -var="tenancy_namespace=${{ steps.get-ocir-repository.outputs.namespace }}"

# --- Variable Definitions ---
variable "tenancy_ocid" {
  description = "OCI tenancy OCID"
  type        = string
}

variable "user_ocid" {
  description = "OCI user OCID"
  type        = string
}

variable "fingerprint" {
  description = "API key fingerprint"
  type        = string
}

variable "private_key" {
  description = "Private API key content"
  type        = string
  sensitive   = true
}

variable "region" {
  description = "OCI region"
  type        = string
}

variable "compartment_ocid" {
  description = "Target compartment OCID"
  type        = string
}

variable "node_image_ocid" {
  description = "OKE node image OCID"
  type        = string
}

variable "docker_image" {
  description = "Docker image URL"
  type        = string
}

variable "tenancy_namespace" {
  description = "OCIR namespace"
  type        = string
}

# --- Terraform Backend & Providers ---
terraform {
  backend "oci" {
    bucket    = "ktor-oke-app-tfstate"
    key       = "ktor-oke/terraform.tfstate"
    region    = "us-ashburn-1"
    namespace = "idrolupgk4or"
  }

  required_providers {
    oci        = { source = "oracle/oci", version = ">= 5.0" }
    kubectl    = { source = "gavinbunney/kubectl", version = "1.14.0" }
    kubernetes = { source = "hashicorp/kubernetes", version = ">= 2.20" }
    local      = { source = "hashicorp/local", version = "2.5.1" }
  }
}

provider "oci" {
  tenancy_ocid = var.tenancy_ocid
  user_ocid    = var.user_ocid
  fingerprint  = var.fingerprint
  private_key  = var.private_key
  region       = var.region
}

# --- Networking ---
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

# --- OKE Cluster ---
resource "oci_containerengine_cluster" "oke_cluster" {
  compartment_id     = var.compartment_ocid
  kubernetes_version = "v1.28.2" # Note: Updated to a more recent, valid version
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
}

# --- Kubeconfig Bootstrap ---
data "oci_containerengine_cluster_kube_config" "oke_kube_config" {
  cluster_id = oci_containerengine_cluster.oke_cluster.id
}

locals {
  kubeconfig_yaml             = yamldecode(data.oci_containerengine_cluster_kube_config.oke_kube_config.content)
  cluster_ca_certificate_data = local.kubeconfig_yaml.clusters[0].cluster["certificate-authority-data"]
}

provider "kubernetes" {
  alias = "oke"

  host = oci_containerengine_cluster.oke_cluster.endpoints[0].kubernetes

  cluster_ca_certificate = base64decode(local.cluster_ca_certificate_data)

  exec {
    api_version = "client.authentication.k8s.io/v1beta1"
    command     = "oci"
    args        = ["ce", "cluster", "generate-token", "--cluster-id", oci_containerengine_cluster.oke_cluster.id]
  }
}

# --- Kubernetes Application Deployment (ADDED SECTION) ---

resource "kubernetes_deployment" "ktor_app_deployment" {
  provider = kubernetes.oke # Ensures this resource is created on the OKE cluster

  metadata {
    name = "ktor-oke-app"
    labels = {
      app = "ktor-oke-app"
    }
  }

  spec {
    replicas = 2 # Run 2 instances of the application for availability

    selector {
      match_labels = {
        app = "ktor-oke-app"
      }
    }

    template {
      metadata {
        labels = {
          app = "ktor-oke-app"
        }
      }
      spec {
        containers {
          image = var.docker_image # Uses the image URL from your GitHub Action
          name  = "ktor-oke-app-container"
          ports {
            # This is the port your Ktor application listens on inside the container
            container_port = 8080 
          }
        }
      }
    }
  }
}

resource "kubernetes_service" "ktor_app_service" {
  provider = kubernetes.oke # Ensures this resource is created on the OKE cluster

  metadata {
    name = "ktor-oke-app-service"
  }

  spec {
    selector = {
      app = kubernetes_deployment.ktor_app_deployment.metadata[0].labels.app
    }
    ports {
      port        = 80   # The port the public load balancer will listen on
      target_port = 8080 # Route traffic from the load balancer to the container's port
    }
    # This tells OCI to provision a public load balancer to expose the application
    type = "LoadBalancer" 
  }
}
