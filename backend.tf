# --- Remote State Backend Configuration ---
#
# Prerequisites:
#   1. Run: ./scripts/bootstrap-state.sh <compartment_ocid> [cluster_name]
#      (Creates the bucket "oke-tfstate-<cluster_name>" in your compartment)
#   2. Run: tofu init
#      (Backend endpoint is hardcoded below — no -backend-config needed)
#

terraform {
  backend "s3" {
    bucket                      = "oke-tfstate-oke-infrastructure"
    key                         = "infrastructure/terraform.tfstate"
    region                      = "us-ashburn-1"
    endpoint                    = "https://idrolupgk4or.compat.objectstorage.us-ashburn-1.oraclecloud.com"
    skip_region_validation      = true
    skip_credentials_validation = true
    skip_requesting_account_id  = true
    skip_s3_checksum            = true
  }
}