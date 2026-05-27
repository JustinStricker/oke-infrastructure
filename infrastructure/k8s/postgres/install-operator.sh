#!/bin/bash
# ------------------------------------------------------------------
# Install the CloudNativePG (CNPG) operator into the OKE cluster.
#
# Prerequisites:
#   - kubectl configured for the OKE cluster
#   - helm installed
#
# Usage:
#   ./k8s/postgres/install-operator.sh
# ------------------------------------------------------------------

set -euo pipefail

NAMESPACE="cnpg-system"
RELEASE_NAME="cnpg"
CHART_REPO="https://cloudnative-pg.github.io/charts"

echo "=== Installing CloudNativePG Operator ==="

# Add the CNPG Helm repository
if ! helm repo list | grep -q "cloudnative-pg"; then
  echo "Adding CNPG Helm repository..."
  helm repo add cnpg "${CHART_REPO}"
fi

echo "Updating Helm repositories..."
helm repo update

# Create namespace if it doesn't exist
if ! kubectl get namespace "${NAMESPACE}" &>/dev/null; then
  echo "Creating namespace '${NAMESPACE}'..."
  kubectl create namespace "${NAMESPACE}"
fi

# Install or upgrade the operator
echo "Installing/upgrading CNPG operator..."
helm upgrade --install "${RELEASE_NAME}" cnpg/cloudnative-pg \
  --namespace "${NAMESPACE}" \
  --create-namespace \
  --wait \
  --timeout 5m

echo ""
echo "=== CNPG Operator Installed Successfully ==="
echo ""
echo "Verify the operator is running:"
echo "  kubectl get pods -n ${NAMESPACE}"
echo ""
echo "Deploy PostgreSQL cluster:"
echo "  kubectl apply -f k8s/postgres/cluster.yaml -n postgres"
echo ""
echo "Or use the reset script:"
echo "  ./scripts/reset-postgres.sh"