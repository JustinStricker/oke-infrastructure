#!/bin/bash
# ------------------------------------------------------------------
# Clean up CloudNativePG + cert-manager — tear down PostgreSQL
# cluster, uninstall Helm releases, and force-delete namespaces
# with a bounded wait (no infinite loops).
#
# Addresses the root cause of stuck namespaces: cert-manager
# webhooks that block namespace finalization.
#
# Usage:
#   ./scripts/cleanup-cnpg.sh [namespace] [timeout_seconds]
#
# Defaults:
#   namespace: postgres
#   timeout:   60
#
# Must be run from the repository root.
# ------------------------------------------------------------------

set -euo pipefail

NAMESPACE="${1:-postgres}"
TIMEOUT="${2:-120}"
MANIFEST="k8s/postgres/cluster.yaml"

echo "=== Cleaning up CNPG + cert-manager ==="
echo "Namespace: ${NAMESPACE}"
echo "Timeout:   ${TIMEOUT}s"

# Phase 1: Delete cluster-scoped webhooks
# These are the root cause of stuck Terminating namespaces — the
# API server tries to call the webhook to validate operations in
# the namespace, but the webhook pod is gone → hang forever.
echo ""
echo "[1/5] Removing cert-manager webhooks..."
kubectl delete validatingwebhookconfiguration cert-manager-webhook --ignore-not-found 2>/dev/null || true
kubectl delete mutatingwebhookconfiguration cert-manager-webhook --ignore-not-found 2>/dev/null || true

# Phase 2: Delete resources in the postgres namespace
echo ""
echo "[2/5] Cleaning up PostgreSQL cluster resources..."
if kubectl get namespace "${NAMESPACE}" &>/dev/null 2>&1; then
    if [ -f "${MANIFEST}" ]; then
        kubectl delete -f "${MANIFEST}" -n "${NAMESPACE}" --wait=true --timeout=120s 2>/dev/null || \
        kubectl patch cluster postgres-cluster \
            -n "${NAMESPACE}" \
            -p '{"metadata":{"finalizers":[]}}' --type=merge 2>/dev/null || true
    fi
    kubectl delete objectstore postgres-cluster-backups -n "${NAMESPACE}" --wait=true 2>/dev/null || true
    kubectl delete pvc -n "${NAMESPACE}" --all --wait=true 2>/dev/null || true
else
    echo "Namespace '${NAMESPACE}' does not exist — skipping."
fi

# Phase 3: Uninstall Helm releases (cleanly removes their CRDs, webhooks, etc.)
echo ""
echo "[3/5] Uninstalling Helm releases..."
helm uninstall cnpg -n cnpg-system --timeout=2m --wait 2>/dev/null || echo "  CNPG release not found — skipping."
helm uninstall cert-manager -n cert-manager --timeout=2m --wait 2>/dev/null || echo "  cert-manager release not found — skipping."

# Phase 4: Clean up orphaned cluster-scoped resources
# These survive namespace deletion and block future Helm installs.
echo ""
echo "[4/5] Cleaning up orphaned cert-manager cluster-scoped resources..."
kubectl get clusterrole -o name 2>/dev/null | grep -E '^clusterrole\.rbac\.authorization\.k8s\.io/cert-manager-' | xargs -r kubectl delete --ignore-not-found 2>/dev/null || true
kubectl get clusterrolebinding -o name 2>/dev/null | grep -E '^clusterrolebinding\.rbac\.authorization\.k8s\.io/cert-manager-' | xargs -r kubectl delete --ignore-not-found 2>/dev/null || true
kubectl get role --all-namespaces --no-headers 2>/dev/null | awk '$2 ~ /^cert-manager/ {print $1, $2}' | while read ns name; do kubectl delete role -n "$ns" "$name" --ignore-not-found 2>/dev/null || true; done
kubectl get rolebinding --all-namespaces --no-headers 2>/dev/null | awk '$2 ~ /^cert-manager/ {print $1, $2}' | while read ns name; do kubectl delete rolebinding -n "$ns" "$name" --ignore-not-found 2>/dev/null || true; done

# Phase 5: Delete namespaces with bounded wait
echo ""
echo "[5/5] Deleting namespaces..."
for ns in "${NAMESPACE}" cnpg-system cert-manager; do
    if ! kubectl get namespace "${ns}" &>/dev/null 2>&1; then
        echo "  Namespace '${ns}' does not exist — skipping."
        continue
    fi

    echo "  Deleting namespace '${ns}'..."
    kubectl delete namespace "${ns}" --timeout=30s --ignore-not-found 2>/dev/null || \
    kubectl patch namespace "${ns}" -p '{"metadata":{"finalizers":[]}}' --type=merge 2>/dev/null || true

    # Bounded wait
    for i in $(seq 1 $((TIMEOUT / 5))); do
        if ! kubectl get namespace "${ns}" &>/dev/null 2>&1; then
            echo "  Namespace '${ns}' deleted successfully."
            break
        fi
        echo "  Waiting for namespace '${ns}' to be removed... ($((i * 5))s)"
        sleep 5
    done

    if kubectl get namespace "${ns}" &>/dev/null 2>&1; then
        echo "  WARNING: Namespace '${ns}' still present after ${TIMEOUT}s timeout." >&2
    fi
done

echo ""
echo "=== Cleanup complete ==="
