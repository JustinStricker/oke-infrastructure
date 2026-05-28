# Deployment Plan: Ktor Server → OKE

## What Exists (Infrastructure — Fully Provisioned via OpenTofu)

| Component | Details |
|-----------|---------|
| OKE cluster | 4× ARM64 Ampere nodes, OCI VCN IP Native CNI, public API endpoint |
| VCN + networking | Subnets, gateways, security lists |
| IAM | Dynamic group + policy for OCIR read, volume management |
| OCIR credential provider | Installed on nodes — short-lived dynamic image pull, no static secrets |
| OCI CSI driver | Block volume provisioning (for PVCs) |

Infra lives in `infrastructure/`. All CI/CD workflows for infra are in `.github/workflows/`.

## Files Created (App-Side)

| File | Purpose |
|------|---------|
| `app/server/Dockerfile` | Multi-stage ARM64 Docker build |
| `.dockerignore` | Exclude unnecessary files from build context |
| `infrastructure/k8s/app/deployment.yaml` | Deployment (1 replica, 8080, probes) + PVC (1Gi, `oci-bv`) |
| `infrastructure/k8s/app/service.yaml` | LoadBalancer, port 80 → 8080 |
| `.github/workflows/ci.yml` | Build + test on push/PR to main |
| `.github/workflows/deploy-server.yml` | Build image → push OCIR → deploy to OKE |

### 1. `app/server/Dockerfile`
Multi-stage build for ARM64 (Ampere nodes):
- Stage 1: Build with `gradle:8.9.0-jdk21`, run `:app:server:installDist`
- Stage 2: Runtime with `eclipse-temurin:21-jre`, `WORKDIR /app/server`, expose 8080, `ENTRYPOINT ["/app/server/bin/server"]`

### 2. `.dockerignore`
Exclude `.git/`, `build/`, `.gradle/`, `*.md`, `.idea/`, `local.properties`, `.terraform/`, `data/`.

### 3. Kubernetes manifests — `infrastructure/k8s/app/`
- **`deployment.yaml`** — Deployment (1 replica, port 8080, liveness/readiness probes) + PVC (1Gi, `oci-bv` storage class) for SQLite at `/app/server/data`
- **`service.yaml`** — LoadBalancer, port 80 → 8080

### 4. GitHub Actions workflows — `.github/workflows/`

#### `ci.yml`
Trigger: push/PR to main. Runs `./gradlew :app:server:build :app:server:test`.

#### `deploy-server.yml`
Trigger: push to main. Full pipeline:

1. `./gradlew :app:server:installDist`
2. `oci artifacts container repository create` (self-healing — creates if missing)
3. Derive OCIR registry + namespace at runtime → `docker login`
4. `docker/build-push-action` with `linux/arm64` → OCIR (tagged with `${{ github.sha }}` + `latest`)
5. Generate kubeconfig via `oci ce cluster create-kubeconfig` (same pattern as `deploy-postgresql.yml`)
6. `kubectl apply -f infrastructure/k8s/app/`
7. Wait for rollout + show LoadBalancer IP

The kubeconfig is generated dynamically — **no static `KUBECONFIG_RAW` secret needed**:
```yaml
- name: Get Cluster OCID
  run: echo "cluster_id=$(tofu output -raw cluster_id)" >> $GITHUB_OUTPUT

- name: Configure kubeconfig
  run: |
    oci ce cluster create-kubeconfig \
      --cluster-id ${{ steps.tf_output.outputs.cluster_id }} \
      --file $HOME/.kube/config \
      --region ${{ secrets.OCI_REGION }}
```

OCIR login derives `region_key` and `tenancy_namespace` at runtime, needing only two GitHub secrets:
```yaml
- name: Derive OCIR config and log in
  run: |
    NAMESPACE=$(oci os ns get | jq -r '.data')
    REGION_KEY=$(oci iam region list | jq -r '.data[] | select(.name=="'"$OCI_CLI_REGION"'") | .key' | tr '[:upper:]' '[:lower:]')
    echo "${{ secrets.OCI_AUTH_TOKEN }}" | \
      docker login -u "${NAMESPACE}/${{ secrets.OCIR_USER_NAME }}" \
        --password-stdin "${REGION_KEY}.ocir.io"
    echo "REGISTRY=${REGION_KEY}.ocir.io" >> $GITHUB_ENV
    echo "REPO=${NAMESPACE}/demo-server" >> $GITHUB_ENV
```

## GitHub Secrets Status

| Secret | Status | Source |
|--------|--------|--------|
| `OCI_USER_OCID` | ✅ Already set | — |
| `OCI_TENANCY_OCID` | ✅ Already set | — |
| `OCI_FINGERPRINT` | ✅ Already set | — |
| `OCI_PRIVATE_KEY` | ✅ Already set | — |
| `OCI_REGION` | ✅ Already set | — |
| `OCI_COMPARTMENT_OCID` | ✅ Already set | — |
| `OCI_AUTH_TOKEN` | ❌ **Need to add** | OCI Console → User Settings → Auth Tokens → Generate Token |
| `OCIR_USER_NAME` | ❌ **Need to add** | Your OCI username (typically your email, e.g. `justin@example.com`) |

## Cluster Info

- **Region:** us-ashburn-1
- **Node shape:** VM.Standard.A1.Flex (ARM64, 1 OCPU, 6 GB RAM, 4 nodes)
- **K8s version:** v1.35.2
- **API endpoint:** Public (OCI VCN IP Native)
- **Cluster OCID:** Get via `tofu output cluster_id` from `infrastructure/`
