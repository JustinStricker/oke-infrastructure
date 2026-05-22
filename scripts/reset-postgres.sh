#!/bin/bash
# ------------------------------------------------------------------
# Reset PostgreSQL cluster — tears down and recreates the CNPG cluster.
# Useful during development when you need to iterate on cluster config.
#
# Prerequisites:
#   - kubectl configured for the OKE cluster
#
# Usage:
#   ./scripts/reset-postgres.sh [namespace]
#
# Default namespace: postgres
# ------------------------------------------------------------------

set -euo pipefail

NAMESPACE="${1:-postgres}"
MANIFEST="k8s/postgres/cluster.yaml"

if [ ! -f "${MANIFEST}" ]; then
  echo "ERROR: Manifest file not found: ${MANIFEST}"
  echo "Run this script from the repository root."
  exit 1
fi

echo "=== Resetting PostgreSQL Cluster ==="
echo "Namespace: ${NAMESPACE}"
echo "Manifest:  ${MANIFEST}"
echo ""

# Check if the namespace exists
if kubectl get namespace "${NAMESPACE}" &>/dev/null; then
  echo "Deleting PostgresCluster CRD..."
  kubectl delete -f "${MANIFEST}" -n "${NAMESPACE}" --wait=true 2>/dev/null || true

  echo "Waiting for PVCs to be released..."
  sleep 5

  # Check for remaining PVCs
  REMAINING=$(kubectl get pvc -n "${NAMESPACE}" -o name 2>/dev/null || true)
  if [ -n "${REMAINING}" ]; then
    echo "Deleting remaining PVCs..."
    kubectl delete pvc -n "${NAMESPACE}" --all --wait=true 2>/dev/null || true
  fi
else
  echo "Creating namespace '${NAMESPACE}'..."
  kubectl create namespace "${NAMESPACE}"
fi

echo ""
echo "Applying PostgresCluster manifest..."
kubectl apply -f "${MANIFEST}" -n "${NAMESPACE}"

echo ""
echo "=== Reset Complete ==="
echo ""
echo "Watch progress:"
echo "  kubectl get pods -n ${NAMESPACE} -w"
echo ""
echo "Connect via port-forward:"
echo "  kubectl port-forward svc/postgres-cluster-rw 5432:5432 -n ${NAMESPACE}"