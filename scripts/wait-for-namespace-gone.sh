#!/bin/bash
# ------------------------------------------------------------------
# Wait for a Kubernetes namespace to be fully removed.
# Tries to strip finalizers each iteration. Exits 1 on timeout.
#
# Usage:
#   ./scripts/wait-for-namespace-gone.sh <namespace> [timeout_seconds]
#
# Default timeout: 120 seconds
# ------------------------------------------------------------------

set -euo pipefail

NAMESPACE="${1:?Usage: $0 <namespace> [timeout_seconds]}"
TIMEOUT="${2:-120}"
END=$((SECONDS + TIMEOUT))

# ──────────────────────────────────────────────────────────────────
# Force-clear every namespaced resource inside a stuck namespace
# and any APIService that points to it.  Patching the namespace's
# own finalizers is rarely enough — the blockers are usually
# CRD instances with finalizers whose controller is gone.
# ──────────────────────────────────────────────────────────────────
force_clear_namespace_resources() {
    local ns="$1"

    kubectl get apiservice --no-headers \
            -o custom-columns="NAME:.metadata.name,NS:.spec.service.namespace" \
            2>/dev/null \
        | awk -v ns="$ns" '$2 == ns {print $1}' \
        | while read -r api; do
            kubectl delete apiservice "$api" --ignore-not-found 2>/dev/null || true
        done

    kubectl api-resources --verbs=list --namespaced -o name 2>/dev/null \
        | while read -r resource; do
            kubectl get "$resource" -n "$ns" -o name 2>/dev/null \
                | while read -r obj; do
                    kubectl patch "$obj" -n "$ns" \
                        -p '{"metadata":{"finalizers":[]}}' \
                        --type=merge 2>/dev/null || true
                    kubectl delete "$obj" -n "$ns" \
                        --force --grace-period=0 2>/dev/null || true
                done
        done
}

while [ $SECONDS -lt $END ]; do
    if ! kubectl get namespace "$NAMESPACE" &>/dev/null 2>&1; then
        echo "Namespace '$NAMESPACE' is gone."
        exit 0
    fi

    # Strip finalizers from the namespace itself
    kubectl patch namespace "$NAMESPACE" \
        -p '{"metadata":{"finalizers":[]}}' \
        --type=merge 2>/dev/null || true

    # Force-clear every resource inside (handles CRD instances,
    # APIServices, etc. whose controllers are gone)
    force_clear_namespace_resources "$NAMESPACE" &>/dev/null || true

    sleep 5
done

echo "ERROR: Namespace '$NAMESPACE' still present after ${TIMEOUT}s timeout." >&2
echo "Inspect with: kubectl get namespace $NAMESPACE -o yaml" >&2
exit 1
