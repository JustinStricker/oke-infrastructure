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

NAMESPACE="${1:-postgres}"
REGION="${2:-${OCI_REGION:-${OCI_CLI_REGION:-us-ashburn-1}}}"
MANIFEST="k8s/postgres/cluster.yaml"

if [ ! -f "${MANIFEST}" ]; then
    echo "ERROR: Manifest not found at ${MANIFEST}"
    echo "Run this script from the repository root."
    exit 1
fi

cleanup() {
    local exit_code=$?
    if [ $exit_code -eq 0 ]; then
        return
    fi
    echo ""
    echo "=== DEPLOYMENT FAILED (exit code $exit_code) — Cleaning up ==="

    # Step 1: Delete PostgreSQL Cluster CR (so CNPG stops managing it)
    if kubectl get namespace "${NAMESPACE}" &>/dev/null 2>&1; then
        echo "Deleting PostgreSQL cluster..."
        kubectl delete -f "${MANIFEST}" -n "${NAMESPACE}" --wait=true 2>/dev/null || true
        echo "Deleting ObjectStore CR..."
        kubectl delete objectstore postgres-cluster-backups -n "${NAMESPACE}" --wait=true 2>/dev/null || true
        echo "Deleting PVCs..."
        kubectl delete pvc -n "${NAMESPACE}" --all --wait=true 2>/dev/null || true
        echo "Deleting namespace '${NAMESPACE}'..."
        kubectl delete namespace "${NAMESPACE}" --wait=true 2>/dev/null || true
    else
        echo "Namespace '${NAMESPACE}' does not exist — skipping cluster cleanup."
    fi

    # Step 2: Uninstall CNPG operator
    if helm list -n cnpg-system &>/dev/null 2>&1; then
        echo "Uninstalling CNPG operator..."
        helm uninstall cnpg -n cnpg-system --wait 2>/dev/null || true
    else
        echo "CNPG operator not found — skipping."
    fi
    kubectl delete namespace cnpg-system --wait=true 2>/dev/null || true

    echo "=== Cleanup complete ==="
    echo ""
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
kubectl apply -f https://github.com/cert-manager/cert-manager/releases/download/v1.16.2/cert-manager.yaml
kubectl wait --for=condition=Available deployment cert-manager -n cert-manager --timeout=120s

echo ""
echo "=== Installing Barman Cloud Plugin ==="
kubectl create namespace cnpg-system --dry-run=client -o yaml | kubectl apply -f -
kubectl apply -f https://github.com/cloudnative-pg/plugin-barman-cloud/releases/download/v0.12.0/manifest.yaml
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
