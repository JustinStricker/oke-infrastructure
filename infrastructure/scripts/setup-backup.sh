#!/bin/bash
# ------------------------------------------------------------------
# Configure PostgreSQL backups to OCI Object Storage.
#
# Creates the cnpg-s3-creds Kubernetes secret and the Barman Cloud
# ObjectStore CR for the CNPG Barman Cloud Plugin.
#
# Prerequisites:
#   - kubectl configured for the OKE cluster
#   - OCI CLI installed and configured
#   - AWS_ACCESS_KEY_ID and AWS_SECRET_ACCESS_KEY set in environment
#     (OCI Customer Secret Key for S3-compatible API access)
#   - cert-manager installed in the cluster
#   - Barman Cloud plugin (plugin-barman-cloud) installed
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
OBJECTSTORE_NAME="postgres-cluster-backups"

echo "=== Configuring PostgreSQL Backups ==="
echo "Namespace:    ${NAMESPACE}"
echo "Region:       ${REGION}"
echo "Bucket:       ${BUCKET_NAME}"
echo "Secret:       ${SECRET_NAME}"
echo "ObjectStore:  ${OBJECTSTORE_NAME}"
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

# Ensure the namespace exists (idempotent)
kubectl create namespace "${NAMESPACE}" --dry-run=client -o yaml | kubectl apply -f -

# Create or update the S3 credentials secret
echo "Creating/updating Kubernetes secret '${SECRET_NAME}'..."
kubectl create secret generic "${SECRET_NAME}" \
  --namespace "${NAMESPACE}" \
  --from-literal=access-key="${AWS_ACCESS_KEY_ID}" \
  --from-literal=secret-key="${AWS_SECRET_ACCESS_KEY}" \
  --dry-run=client -o yaml | kubectl apply -f -

# Create or update the ObjectStore CR for the Barman Cloud Plugin
echo "Creating/updating ObjectStore '${OBJECTSTORE_NAME}'..."
kubectl apply --namespace "${NAMESPACE}" -f - <<EOF
apiVersion: barmancloud.cnpg.io/v1
kind: ObjectStore
metadata:
  name: ${OBJECTSTORE_NAME}
spec:
  configuration:
    destinationPath: "s3://${BUCKET_NAME}/"
    endpointURL: "https://${ENDPOINT}"
    s3Credentials:
      accessKeyId:
        name: ${SECRET_NAME}
        key: access-key
      secretAccessKey:
        name: ${SECRET_NAME}
        key: secret-key
    wal:
      compression: gzip
  retentionPolicy: "30d"
EOF

echo ""
echo "=== Backup Configuration Complete ==="
echo ""
echo "Verify backup status:"
echo "  kubectl get objectstore ${OBJECTSTORE_NAME} -n ${NAMESPACE} -o yaml"
