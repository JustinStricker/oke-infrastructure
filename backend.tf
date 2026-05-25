# --- Remote State Backend Configuration ---
#
# Prerequisites:
#   1. Run the "Bootstrap Remote State" GitHub Actions workflow
#      (Creates the bucket "oke-tfstate-oke-infrastructure" in your compartment)
#   2. Update the endpoint URL below with your Object Storage namespace
#      (The bootstrap workflow prints the correct URL)
#   3. Run: tofu init -migrate-state
#

terraform {
  backend "s3" {
    bucket                      = "oke-tfstate-oke-infrastructure"
    key                         = "infrastructure/terraform.tfstate"
    region                      = "us-ashburn-1"
    endpoint                    = "https://idrolupgk4or.compat.objectstorage.us-ashburn-1.oraclecloud.com"
    use_path_style              = true
    skip_region_validation      = true
    skip_credentials_validation = true
    skip_requesting_account_id  = true
    skip_s3_checksum            = true
  }
}
