#!/bin/bash
# ------------------------------------------------------------------
# Deploy PostgreSQL (CNPG operator + cluster) with automatic cleanup
# on failure. Idempotent — safe to re-run.
#
# Usage:
#   ./scripts/deploy-postgres.sh [namespace] [region]
#
# Defaults:
#   namespace: postgres
#   region:    us-ashburn-1
#
# Env vars (optional overrides):
#   OCI_REGION, OCI_CLI_REGION
# ------------------------------------------------------------------

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
NAMESPACE="${1:-postgres}"
REGION="${2:-${OCI_REGION:-${OCI_CLI_REGION:-us-ashburn-1}}}"
MANIFEST="k8s/postgres/cluster.yaml"

if [ ! -f "${MANIFEST}" ]; then
    echo "ERROR: Manifest not found at ${MANIFEST}"
    echo "Run this script from the repository root."
    exit 1
fi

# Pre-deploy: clean up any residual state from a prior failed run
if kubectl get namespace cnpg-system &>/dev/null 2>&1; then
    echo "Pre-deploy: cleaning up residual state..."
    "${SCRIPT_DIR}/cleanup-cnpg.sh" "${NAMESPACE}"
    "${SCRIPT_DIR}/wait-for-namespace-gone.sh" cnpg-system 120
fi

cleanup() {
    local exit_code=$?
    if [ $exit_code -eq 0 ]; then
        return
    fi
    echo ""
    echo "=== DEPLOYMENT FAILED (exit code $exit_code) — Cleaning up ==="
    "${SCRIPT_DIR}/cleanup-cnpg.sh" "${NAMESPACE}"
}

trap cleanup EXIT

echo "=== Deploying PostgreSQL ==="
echo "Namespace: ${NAMESPACE}"
echo "Region:    ${REGION}"
echo ""

# --- Configure kubeaccess ---
echo "Getting cluster OCID from OpenTofu..."
CLUSTER_ID=$(tofu output -raw cluster_id)
echo "Cluster OCID: ${CLUSTER_ID}"

echo "Configuring kubeconfig..."
oci ce cluster create-kubeconfig \
    --cluster-id "${CLUSTER_ID}" \
    --file "${HOME}/.kube/config" \
    --region "${REGION}" \
    --token-version 2.0.0

echo "Verifying cluster access..."
kubectl get nodes

# --- Install cert-manager & Barman Cloud Plugin ---
echo ""
echo "=== Installing cert-manager ==="
echo "Cleaning up orphaned cert-manager cluster-scoped resources..."
kubectl get clusterrole -o name 2>/dev/null | grep -E '^clusterrole\.rbac\.authorization\.k8s\.io/cert-manager-' | xargs -r kubectl delete --ignore-not-found
kubectl get clusterrolebinding -o name 2>/dev/null | grep -E '^clusterrolebinding\.rbac\.authorization\.k8s\.io/cert-manager-' | xargs -r kubectl delete --ignore-not-found
kubectl get role --all-namespaces --no-headers 2>/dev/null | awk '$2 ~ /^cert-manager/ {print $1, $2}' | while read ns name; do kubectl delete role -n "$ns" "$name" --ignore-not-found 2>/dev/null || true; done
kubectl get rolebinding --all-namespaces --no-headers 2>/dev/null | awk '$2 ~ /^cert-manager/ {print $1, $2}' | while read ns name; do kubectl delete rolebinding -n "$ns" "$name" --ignore-not-found 2>/dev/null || true; done
helm repo add jetstack https://charts.jetstack.io 2>/dev/null || true
helm repo update
helm upgrade --install cert-manager jetstack/cert-manager \
    --namespace cert-manager \
    --create-namespace \
    --set crds.enabled=true \
    --wait \
    --timeout 3m

echo ""
echo "=== Installing Barman Cloud Plugin ==="
kubectl create namespace cnpg-system --dry-run=client -o yaml | kubectl apply -f -
kubectl apply -f https://github.com/cloudnative-pg/plugin-barman-cloud/releases/download/v0.12.0/manifest.yaml
kubectl wait --for=condition=Ready --timeout=60s \
  certificate/barman-cloud-client \
  certificate/barman-cloud-server \
  -n cnpg-system
kubectl rollout status deployment -n cnpg-system barman-cloud --timeout=120s

# --- Install CNPG Operator ---
echo ""
echo "=== Installing CNPG Operator ==="
helm repo add cnpg https://cloudnative-pg.github.io/charts 2>/dev/null || true
helm repo update
helm upgrade --install cnpg cnpg/cloudnative-pg \
    --namespace cnpg-system \
    --create-namespace \
    --wait \
    --timeout 5m

echo "Verifying CNPG operator..."
kubectl get pods -n cnpg-system

# --- Deploy PostgreSQL Cluster ---
echo ""
echo "=== Deploying PostgreSQL Cluster ==="
kubectl create namespace "${NAMESPACE}" --dry-run=client -o yaml | kubectl apply -f -
kubectl apply -f "${MANIFEST}" -n "${NAMESPACE}"

echo "Waiting for PostgreSQL cluster to be ready..."
kubectl wait --for=condition=Ready \
    cluster/postgres-cluster \
    -n "${NAMESPACE}" \
    --timeout=300s

# --- Configure Backups ---
echo ""
echo "=== Configuring Backups ==="
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
if [ -f "${SCRIPT_DIR}/setup-backup.sh" ]; then
  "${SCRIPT_DIR}/setup-backup.sh" "${NAMESPACE}" "${REGION}"
else
  echo "WARNING: setup-backup.sh not found — backups not configured."
  echo "Run manually: ./scripts/setup-backup.sh ${NAMESPACE} ${REGION}"
fi

# --- Success ---
echo ""
echo "=== Deployment Successful ==="
echo ""
echo "Pods:"
kubectl get pods -n "${NAMESPACE}"
echo ""
echo "Services:"
kubectl get svc -n "${NAMESPACE}"
echo ""
echo "Connect:"
echo "  kubectl port-forward svc/postgres-cluster-rw 5432:5432 -n ${NAMESPACE}"
echo "  psql -h localhost -U postgres"
echo ""
echo "Password:"
echo "  kubectl get secret postgres-cluster-app -n ${NAMESPACE} -o jsonpath='{.data.password}' | base64 -d"
