#!/bin/bash
# ------------------------------------------------------------------
# Bootstrap script: creates the OCI Object Storage bucket for remote state.
#
# Prerequisites:
#   - OCI CLI installed and configured (authenticated)
#   - jq installed
#
# Usage:
#   ./scripts/bootstrap-state.sh <compartment_ocid> [cluster_name]
#
# Example:
#   ./scripts/bootstrap-state.sh ocid1.compartment.oc1..aaaa... oke-infrastructure
# ------------------------------------------------------------------

set -euo pipefail

if ! command -v jq &>/dev/null; then
  echo "ERROR: jq is required but not installed."
  echo "Install it with: brew install jq  (macOS)  or  apt install jq  (Linux)"
  exit 1
fi

COMPARTMENT_OCID="${1:?Usage: $0 <compartment_ocid> [cluster_name]}"
CLUSTER_NAME="${2:-oke-infrastructure}"
BUCKET_NAME="oke-tfstate-${CLUSTER_NAME}"

echo "=== Bootstrapping Remote State Backend ==="
echo "Compartment: ${COMPARTMENT_OCID}"
echo "Bucket:      ${BUCKET_NAME}"
echo ""

# Get the Object Storage namespace
NAMESPACE=$(oci os ns get | jq -r '.data')
echo "Object Storage namespace: ${NAMESPACE}"

# Create the bucket
if oci os bucket get --namespace "${NAMESPACE}" --bucket-name "${BUCKET_NAME}" &>/dev/null; then
  echo "Bucket '${BUCKET_NAME}' already exists. Skipping creation."
else
  echo "Creating bucket '${BUCKET_NAME}'..."
  oci os bucket create \
    --compartment-id "${COMPARTMENT_OCID}" \
    --name "${BUCKET_NAME}" \
    --namespace "${NAMESPACE}" \
    --public-access-type "NoPublicAccess" \
    --storage-tier "Standard" \
    --versioning "Enabled"
  echo "Bucket created successfully."
fi

echo ""
echo "=== Bootstrap Complete ==="
echo ""
echo "Next steps:"
echo "  1. Edit backend.tf and uncomment the 'terraform { backend \"s3\" { ... } }' block"
echo "  2. Update the endpoint URL in backend.tf with your namespace:"
echo "     https://${NAMESPACE}.compat.objectstorage.<region>.oraclecloud.com"
echo "  3. Run: tofu init -migrate-state"
echo "     (This migrates local state to the remote bucket)"