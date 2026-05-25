#!/bin/bash
# ------------------------------------------------------------------
# Configure PostgreSQL backups to OCI Object Storage.
#
# Creates the cnpg-s3-creds Kubernetes secret and patches the
# Cluster's endpoint URL with the correct OCI Object Storage
# S3-compatible endpoint.
#
# Prerequisites:
#   - kubectl configured for the OKE cluster
#   - OCI CLI installed and configured
#   - AWS_ACCESS_KEY_ID and AWS_SECRET_ACCESS_KEY set in environment
#     (OCI Customer Secret Key for S3-compatible API access)
#
# Usage:
#   ./scripts/setup-backup.sh [namespace] [region] [cluster_name]
#
# Defaults:
#   namespace:    postgres
#   region:       us-ashburn-1
#   cluster_name: oke-infrastructure
# ------------------------------------------------------------------

set -euo pipefail

NAMESPACE="${1:-postgres}"
REGION="${2:-${OCI_REGION:-${OCI_CLI_REGION:-us-ashburn-1}}}"
CLUSTER_NAME="${3:-oke-infrastructure}"
BUCKET_NAME="oke-postgres-backups-${CLUSTER_NAME}"
SECRET_NAME="cnpg-s3-creds"

echo "=== Configuring PostgreSQL Backups ==="
echo "Namespace:    ${NAMESPACE}"
echo "Region:       ${REGION}"
echo "Bucket:       ${BUCKET_NAME}"
echo "Secret:       ${SECRET_NAME}"
echo ""

if [ -z "${AWS_ACCESS_KEY_ID:-}" ] || [ -z "${AWS_SECRET_ACCESS_KEY:-}" ]; then
  echo "ERROR: AWS_ACCESS_KEY_ID and AWS_SECRET_ACCESS_KEY must be set."
  echo "These are your OCI Customer Secret Key values for S3-compatible API access."
  exit 1
fi

# Derive the Object Storage S3-compatible endpoint
echo "Looking up Object Storage namespace..."
OS_NAMESPACE=$(oci os ns get | jq -r '.data')
ENDPOINT="${OS_NAMESPACE}.compat.objectstorage.${REGION}.oraclecloud.com"
echo "Endpoint: https://${ENDPOINT}"
echo ""

# Create or update the S3 credentials secret
echo "Creating/updating Kubernetes secret '${SECRET_NAME}'..."
kubectl create secret generic "${SECRET_NAME}" \
  --namespace "${NAMESPACE}" \
  --from-literal=access-key="${AWS_ACCESS_KEY_ID}" \
  --from-literal=secret-key="${AWS_SECRET_ACCESS_KEY}" \
  --dry-run=client -o yaml | kubectl apply -f -

# Patch the PostgreSQL cluster with the correct endpoint URL
echo "Patching PostgreSQL cluster endpoint URL..."
kubectl patch cluster postgres-cluster \
  --namespace "${NAMESPACE}" \
  --type='json' \
  -p="[{\"op\": \"replace\", \"path\": \"/spec/backup/barmanObjectStore/endpointURL\", \"value\": \"https://${ENDPOINT}\"}]"

echo ""
echo "=== Backup Configuration Complete ==="
echo ""
echo "Verify backup status:"
echo "  kubectl get cluster postgres-cluster -n ${NAMESPACE} -o yaml | grep endpointURL"
