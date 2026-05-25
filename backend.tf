# --- Remote State Backend Configuration ---
#
# Prerequisites:
#   1. Run the "Bootstrap Remote State" GitHub Actions workflow
#      (Creates the bucket "oke-tfstate-oke-infrastructure" in your compartment)
#   2. Run: OCI_NAMESPACE=$(oci os ns get | jq -r '.data') && \
#           OCI_REGION=us-ashburn-1 && \
#           tofu init \
#             -backend-config="region=${OCI_REGION}" \
#             -backend-config="endpoint=https://${OCI_NAMESPACE}.compat.objectstorage.${OCI_REGION}.oraclecloud.com"
#   3. Run: tofu init -migrate-state
#

terraform {
  backend "s3" {
    bucket                      = "oke-tfstate-oke-infrastructure"
    key                         = "infrastructure/terraform.tfstate"
    use_path_style              = true
    skip_region_validation      = true
    skip_credentials_validation = true
    skip_requesting_account_id  = true
    skip_s3_checksum            = true
  }
}
