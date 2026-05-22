# --- Remote State Backend Configuration ---
#
# Uncomment and configure this block AFTER bootstrapping the OCI Object Storage bucket.
# See scripts/bootstrap-state.sh for the setup steps.
#
# Prerequisites:
#   1. Run: scripts/bootstrap-state.sh
#   2. This creates the bucket "oke-tfstate-<cluster_name>" in your compartment
#   3. Then uncomment this block, run: tofu init -migrate-state
#
# terraform {
#   backend "s3" {
#     bucket                      = "oke-tfstate-oke-infrastructure"
#     key                         = "infrastructure/terraform.tfstate"
#     region                      = "us-ashburn-1"
#     endpoint                    = "https://<namespace>.compat.objectstorage.us-ashburn-1.oraclecloud.com"
#     skip_region_validation      = true
#     skip_credentials_validation = true
#     skip_requesting_account_id  = true
#     skip_s3_checksum            = true
#   }
# }