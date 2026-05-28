# Deployment Plan

| Component | Where | How |
|-----------|-------|-----|
| **Ktor server** (API + PostgreSQL) | OCI OKE | Docker → OCIR → Kubernetes manifests |
| **Compose Web client** (Wasm/JS) | Cloudflare Pages | Static file deploy via Wrangler |

---

## 1. Ktor Server → OKE

### What Exists (Infrastructure — Fully Provisioned via OpenTofu)

| Component | Details |
|-----------|---------|
| OKE cluster | 4× ARM64 Ampere nodes, OCI VCN IP Native CNI, public API endpoint |
| VCN + networking | Subnets, gateways, security lists |
| IAM | Dynamic group + policy for OCIR read, volume management |
| OCIR credential provider | Installed on nodes — short-lived dynamic image pull, no static secrets |
| OCI CSI driver | Block volume provisioning (for PVCs) |

Infra lives in `infrastructure/`. All CI/CD workflows for infra are in `.github/workflows/`.

### Files Created (App-Side)

| File | Purpose |
|------|---------|
| `app/server/Dockerfile` | Multi-stage ARM64 Docker build |
| `.dockerignore` | Exclude unnecessary files from build context |
| `infrastructure/k8s/app/deployment.yaml` | Deployment (1 replica, 8080, probes) + PVC (1Gi, `oci-bv`) |
| `infrastructure/k8s/app/service.yaml` | LoadBalancer, port 80 → 8080 |
| `.github/workflows/app-ci.yml` | Build + test → deploy server to OKE on push to main |
| `.github/workflows/web-deploy.yml` | Builds Wasm/JS client → deploys to Cloudflare Pages on push to main |

#### `app/server/Dockerfile`
Multi-stage build for ARM64 (Ampere nodes):
- Stage 1: Build with `gradle:8.9.0-jdk21`, run `:app:server:installDist`
- Stage 2: Runtime with `eclipse-temurin:21-jre`, `WORKDIR /app/server`, expose 8080, `ENTRYPOINT ["/app/server/bin/server"]`

#### `.dockerignore`
Exclude `.git/`, `build/`, `.gradle/`, `*.md`, `.idea/`, `local.properties`, `.terraform/`, `data/`.

#### Kubernetes manifests — `infrastructure/k8s/app/`
- **`deployment.yaml`** — Deployment (1 replica, port 8080, liveness/readiness probes) + PVC (1Gi, `oci-bv` storage class) for SQLite at `/app/server/data`
- **`service.yaml`** — LoadBalancer, port 80 → 8080

---

## 2. Compose Web Client → Cloudflare Pages

The web client is built by Kotlin/Wasm and produces static files — no server-side rendering, no backend logic.

### Build

```bash
./gradlew :app:composeApp:wasmJsBrowserDistribution
```

Output directory:
```
app/composeApp/build/dist/wasmJs/productionExecutable/
├── index.html
├── composeApp.js
├── composeApp.wasm
├── styles.css
└── ... (JS deps, source maps)
```

### SPA routing

Compose Web handles navigation internally (sealed class `Screen` routing). All paths must serve `index.html`. Add a `_redirects` file in the build output root:

```
/*  /index.html  200
```

### Deploy

Using Cloudflare Wrangler CLI:

```bash
npx wrangler pages deploy \
  app/composeApp/build/dist/wasmJs/productionExecutable/ \
  --project-name=notable-web
```

The project is created explicitly in the CI workflow via `wrangler pages project create` before the deploy step (no-op if already exists). Subsequent runs deploy a new version (immutable, with instant rollback in the dashboard).

### CORS

Already configured in the Ktor server (`install(CORS) { anyHost() }`) — no changes needed.

### Required file

| File | Purpose |
|------|---------|
| `app/composeApp/wasmJsMain/resources/_redirects` | SPA fallback — serves `index.html` for all routes |

---

## 3. CI/CD — Two Independent Workflows

Both trigger on push to `main` but run independently with separate secret sets and scoped path filters.

### `app-ci.yml` (server → OKE)

Trigger: push to `main` (any path). Two jobs:

**`test`** — `./gradlew :app:server:build :app:server:test`

**`deploy`** (needs: test) — Full deploy pipeline:
1. `./gradlew :app:server:installDist`
2. Create OCIR repo (self-healing)
3. Derive OCIR registry + docker login
4. `docker/build-push-action` → OCIR (`linux/arm64`)
5. Generate kubeconfig via OCI CLI
6. `kubectl apply` deployment.yaml + service.yaml
7. Wait for rollout + show LoadBalancer IP

### `web-deploy.yml` (client → Cloudflare Pages)

Trigger: push to `main` scoped to web-relevant changes:

| Path filter | Rationale |
|-------------|-----------|
| `app/composeApp/**` | Web UI source, resources, build config |
| `app/shared/**` | Shared data models & API clients |
| `gradle/**` | Version catalog, wrapper, plugin changes |
| `.github/workflows/web-deploy.yml` | Workflow changes trigger themselves |

Single job — no dependency on server tests:

```yaml
deploy:
  runs-on: ubuntu-latest

  steps:
    - uses: actions/checkout@v6
    - uses: actions/setup-java@v4
      with:
        distribution: 'temurin'
        java-version: '21'
    - uses: gradle/actions/setup-gradle@v4
    - run: ./gradlew :app:composeApp:wasmJsBrowserDistribution --no-daemon
    - uses: cloudflare/wrangler-action@v3
      with:
        apiToken: ${{ secrets.CLOUDFLARE_API_TOKEN }}
        accountId: ${{ secrets.CLOUDFLARE_ACCOUNT_ID }}
        command: pages deploy app/composeApp/build/dist/wasmJs/productionExecutable/ --project-name=notable-web
```

---

## 4. GitHub Secrets

### Current (OCI)

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

### New (Cloudflare Pages)

| Secret | Status | Source |
|--------|--------|--------|
| `CLOUDFLARE_API_TOKEN` | ✅ Already set | Cloudflare Dashboard → My Profile → API Tokens → Create Token (Permissions: Cloudflare Pages → Edit) |
| `CLOUDFLARE_ACCOUNT_ID` | ✅ Already set | Cloudflare Dashboard → right sidebar → Account ID |

---

## 5. Architecture

```
┌─ Browser ──────────────────────────────────────┐
│  cloudflare.pages.dev                          │
│  ┌──────────────────────────────────────────┐  │
│  │ index.html                               │  │
│  │ ├── composeApp.js (module entry)         │  │
│  │ └── composeApp.wasm (Kotlin/Wasm)        │  │
│  │     └── App() ──┬── Compose UI           │  │
│  │                  └── Ktor HTTP Client ───┼──┼──► OCI OKE LoadBalancer
│  └──────────────────────────────────────────┘  │   (Ktor server :8080)
└────────────────────────────────────────────────┘
```

Cloudflare Pages serves the static web client at `notable-web.pages.dev` (or a custom domain). The client makes API calls to the Ktor server's LoadBalancer IP. Cloudflare's global CDN caches the static assets edge-side for fast load times worldwide.

---

## 6. Cluster Info

- **Region:** us-ashburn-1
- **Node shape:** VM.Standard.A1.Flex (ARM64, 1 OCPU, 6 GB RAM, 4 nodes)
- **K8s version:** v1.35.2
- **API endpoint:** Public (OCI VCN IP Native)
- **Cluster OCID:** Get via `tofu output cluster_id` from `infrastructure/`
