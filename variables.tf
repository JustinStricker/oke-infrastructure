variable "tenancy_ocid" {
  type        = string
  description = "The OCID of the tenancy."
}

variable "user_ocid" {
  type        = string
  description = "The OCID of the user."
}

variable "fingerprint" {
  type        = string
  description = "The fingerprint of the API key."
}

variable "private_key" {
  type        = string
  description = "The content of the private API key."
  sensitive   = true
}

variable "region" {
  type        = string
  description = "The OCI region to create resources in."
}

variable "compartment_ocid" {
  type        = string
  description = "The OCID of the compartment to create resources in."
}

variable "node_image_ocid" {
  type        = string
  description = "The OCID of the OKE node image to use."
}

variable "docker_image" {
  type        = string
  description = "The full URL of the Docker image to deploy."
}

variable "tenancy_namespace" {
  type = string
  description = "The OCI tenancy namespace for OCIR."
}
