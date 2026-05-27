# This file is intentionally empty.
#
# All infrastructure resources have been split across the following files:
#
#   providers.tf       — Provider configuration and required_version
#   variables.tf       — Input variables
#   networking.tf      — VCN, subnets, security lists, gateways, route tables
#   oke.tf             — OKE cluster, node pool, and data sources
#   iam.tf             — Dynamic group and OCIR policy
#   backup.tf          — Object Storage bucket for PostgreSQL backups
#   backend.tf         — Remote state backend configuration (disabled by default)
#   outputs.tf         — Output values
#   terraform.tfvars.example — Example variable values