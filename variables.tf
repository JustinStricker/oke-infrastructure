variable "tenancy_ocid" {
  description = "The OCID of the tenancy."
}

variable "user_ocid" {
  description = "The OCID of the user."
}

variable "fingerprint" {
  description = "The fingerprint of the API key."
}

variable "private_key" {
  description = "The private key for the API."
  sensitive   = true
}

variable "region" {
  description = "The OCI region."
}

variable "compartment_ocid" {
  description = "The OCID of the compartment."
}

variable "tenancy_namespace" {
  description = "The OCI tenancy namespace for OCIR."
}
