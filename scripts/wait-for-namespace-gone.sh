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

while [ $SECONDS -lt $END ]; do
    if ! kubectl get namespace "$NAMESPACE" &>/dev/null 2>&1; then
        echo "Namespace '$NAMESPACE' is gone."
        exit 0
    fi
    # Strip finalizers in case the namespace is stuck
    kubectl patch namespace "$NAMESPACE" \
        -p '{"metadata":{"finalizers":[]}}' \
        --type=merge 2>/dev/null || true
    sleep 5
done

echo "ERROR: Namespace '$NAMESPACE' still present after ${TIMEOUT}s timeout." >&2
echo "Inspect with: kubectl get namespace $NAMESPACE -o yaml" >&2
exit 1
