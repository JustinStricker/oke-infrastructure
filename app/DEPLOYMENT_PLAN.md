# OCI OKE Deployment Plan — Demo Notes & Tasks App

## Objective
Fully automated CI/CD pipeline for the Demo Notes & Tasks App — a Kotlin Multiplatform project with a Ktor server backend and Compose Multiplatform frontend targeting Android, iOS, JVM Desktop, and Web (WasmJs).

Deploy the **Ktor Server** to Oracle Cloud Infrastructure (OCI) Container Engine for Kubernetes (OKE), and the **WasmJs Web frontend** to Cloudflare Pages.

---

## Architecture Overview

```
[Developer] ──▶ [GitHub Repo (main)]
                     │
                     ├─ push/PR ──▶ [CI Pipeline]
                     │                  ├─ :shared:build
                     │                  ├─ :server:build + test
                     │                  ├─ :composeApp:compileKotlinJvm
                     │                  └─ :composeApp:compileKotlinWasmJs
                     │
                     ├─ push main ──▶ [Server CD Pipeline]
                     │                   ├─ gradle :server:installDist
                     │                   ├─ docker build/push → OCIR
                     │                   └─ kubectl apply → OKE
                     │
                     └─ push main ──▶ [Web CD Pipeline]
                                        ├─ gradle :composeApp:wasmJsBrowserDistribution
                                        └─ wrangler pages deploy → Cloudflare

[OCI OKE Cluster]
      │
      ├─ Deployment: 1× demo-server pod
      ├─ Service: LoadBalancer → port 80 → pod 8080
      └─ Volume: PersistentVolumeClaim → /app/server/data (SQLite DB)

[Cloudflare Pages]
      └─ Serves wasmJs production build → public URL
```

---

## Project Build Context

| Module | Description | Build Task | Output |
|--------|------------|------------|--------|
| `:server` | Ktor backend (JVM) | `./gradlew :server:installDist` | `server/build/install/server/` (distribution with start script) |
| `:shared` | Shared models + API clients (KMP) | `./gradlew :shared:build` | Multiplatform artifacts |
| `:composeApp` | Compose Multiplatform UI | Various per target | Android APK, iOS framework, JVM run, WasmJs site |

### Key Server Details
- **Main class:** `com.example.demo.ApplicationKt`
- **Port:** 8080 (overridable via `SERVER_PORT` env var)
- **Framework:** Ktor with Netty engine
- **Database:** SQLite via Exposed JDBC (stored at `data/database.db` relative to working directory)
- **Plugin:** Uses `application` plugin (not Shadow) — produces distribution with shell/bat start scripts

---

## Phase 1: CI Pipeline

**File:** `.github/workflows/ci.yml`

Triggers on **push to main** and **pull request to main**. Runs build and tests for all modules.

```yaml
name: CI

on:
  push:
    branches: [ main ]
  pull_request:
    branches: [ main ]

jobs:
  build-and-test:
    name: Build and Test
    runs-on: ubuntu-latest
    steps:
      - name: Checkout Repository
        uses: actions/checkout@v4

      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          java-version: 21
          distribution: 'temurin'

      - name: Cache Gradle Dependencies
        uses: actions/cache@v4
        with:
          path: |
            ~/.gradle/caches
            ~/.gradle/wrapper
          key: ${{ runner.os }}-gradle-${{ hashFiles('**/*.gradle*', '**/gradle-wrapper.properties', '**/libs.versions.toml') }}
          restore-keys: |
            ${{ runner.os }}-gradle-

      - name: Grant execute permission for Gradle wrapper
        run: chmod +x gradlew

      - name: Build shared module
        run: ./gradlew :shared:build

      - name: Build server module
        run: ./gradlew :server:build

      - name: Run server tests
        run: ./gradlew :server:test

      - name: Compile-check JVM Desktop frontend
        run: ./gradlew :composeApp:compileKotlinJvm

      - name: Compile-check WasmJs web frontend
        run: ./gradlew :composeApp:compileKotlinWasmJs
```

> **Note:** Android and iOS builds require Android SDK / macOS runners and are not included here. They can be added as matrix jobs on separate runners if needed.

---

## Phase 2: Server Dockerfile

**File:** `server/Dockerfile`

Multi-stage build optimized for ARM64 (OCI Ampere A1 nodes).

```dockerfile
# Stage 1: Cache Gradle dependencies
FROM gradle:8.9.0-jdk21 AS cache
RUN mkdir -p /home/gradle/cache_home
ENV GRADLE_USER_HOME=/home/gradle/cache_home

# Copy only build configuration files for layer caching
COPY build.gradle.kts settings.gradle.kts gradle.properties /home/gradle/app/
COPY gradle/ /home/gradle/app/gradle/
COPY server/build.gradle.kts /home/gradle/app/server/
COPY shared/build.gradle.kts /home/gradle/app/shared/

WORKDIR /home/gradle/app
RUN gradle :server:dependencies --no-daemon

# Stage 2: Build the server distribution
FROM gradle:8.9.0-jdk21 AS build
COPY --from=cache /home/gradle/cache_home /home/gradle/.gradle
COPY --chown=gradle:gradle . /home/gradle/src
WORKDIR /home/gradle/src
RUN gradle :server:installDist --no-daemon

# Stage 3: Minimal JRE runtime image
FROM eclipse-temurin:21-jre AS runtime
EXPOSE 8080
RUN mkdir -p /app/server/data

COPY --from=build /home/gradle/src/server/build/install/server /app/server

# The server writes SQLite data to its working directory /app/server
WORKDIR /app/server
VOLUME /app/server/data

ENTRYPOINT ["/app/server/bin/server"]
```

> **Note:** The `application` plugin generates `server/build/install/server/` containing `bin/server` (start script), `lib/` (classpath JARs), and `cfg/` (config). No Shadow/fat-JAR needed.

---

## Phase 2.5: Dockerfile Optimizations

### `.dockerignore`

Add a `.dockerignore` file at the repository root to exclude unnecessary files from the Docker build context, speeding up builds:

```
.git/
iosApp/
composeApp/node_modules/
composeApp/build/
server/build/
data/
.gradle/
*.md
```

### Dockerfile Validation

Test the Dockerfile locally before pushing to CI:

```bash
docker build -f server/Dockerfile -t demo-server:test .
docker run -p 8080:8080 demo-server:test
```

If the multi-stage Gradle cache stage (`gradle :server:dependencies`) fails due to missing Kotlin plugins, simplify to a single-stage build:

```dockerfile
FROM gradle:8.9.0-jdk21 AS build
COPY --chown=gradle:gradle . /home/gradle/src
WORKDIR /home/gradle/src
RUN gradle :server:installDist --no-daemon

FROM eclipse-temurin:21-jre AS runtime
EXPOSE 8080
RUN mkdir -p /app/server/data
COPY --from=build /home/gradle/src/server/build/install/server /app/server
WORKDIR /app/server
VOLUME /app/server/data
ENTRYPOINT ["/app/server/bin/server"]
```

---

## Phase 3: Kubernetes Manifests

**Directory:** `k8s/`

### `k8s/deployment.yaml`

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: demo-server
  labels:
    app: demo-server
spec:
  replicas: 1
  selector:
    matchLabels:
      app: demo-server
  template:
    metadata:
      labels:
        app: demo-server
    spec:
      containers:
      - name: demo-server
        # CI/CD will replace 'your-ocir-repo/demo-server:latest' with the actual image tag
        image: your-ocir-repo/demo-server:latest
        ports:
        - containerPort: 8080
          protocol: TCP
        env:
        - name: SERVER_PORT
          value: "8080"
        livenessProbe:
          httpGet:
            path: /
            port: 8080
          initialDelaySeconds: 10
          periodSeconds: 15
        readinessProbe:
          httpGet:
            path: /
            port: 8080
          initialDelaySeconds: 5
          periodSeconds: 10
        resources:
          requests:
            memory: "256Mi"
            cpu: "250m"
          limits:
            memory: "512Mi"
            cpu: "500m"
        volumeMounts:
        - name: data-volume
          mountPath: /app/server/data
      volumes:
      - name: data-volume
        persistentVolumeClaim:
          claimName: demo-server-data-pvc
---
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: demo-server-data-pvc
spec:
  accessModes:
    - ReadWriteOnce
  resources:
    requests:
      storage: 1Gi
```

### `k8s/service.yaml`

```yaml
apiVersion: v1
kind: Service
metadata:
  name: demo-server-service
  annotations:
    service.beta.kubernetes.io/oci-load-balancer-shape: "flexible"
    service.beta.kubernetes.io/oci-load-balancer-shape-flex-min: "10"
    service.beta.kubernetes.io/oci-load-balancer-shape-flex-max: "100"
    service.beta.kubernetes.io/oci-load-balancer-health-check-retries: "3"
    service.beta.kubernetes.io/oci-load-balancer-health-check-interval: "10"
    service.beta.kubernetes.io/oci-load-balancer-health-check-timeout: "5"
    service.beta.kubernetes.io/oci-load-balancer-health-check-protocol: "HTTP"
    service.beta.kubernetes.io/oci-load-balancer-health-check-port: "8080"
    service.beta.kubernetes.io/oci-load-balancer-health-check-path: "/"
spec:
  type: LoadBalancer
  ports:
    - port: 80
      targetPort: 8080
      protocol: TCP
  selector:
    app: demo-server
```

### `k8s/README.md` (optional, for reference)

| File | Purpose |
|------|---------|
| `deployment.yaml` | Deployment (1 replica) + PVC (1Gi for SQLite) |
| `service.yaml` | OCI LoadBalancer, port 80 → 8080 |

Apply all manifests:
```bash
kubectl apply -f k8s/
```

---

## Phase 4: Server CD Pipeline

**File:** `.github/workflows/deploy-server.yml`

Triggers on **push to main**. Builds the Docker image, pushes to OCI Container Registry (OCIR), and deploys to OKE.

```yaml
name: Deploy Server to OKE

on:
  push:
    branches: [ main ]

env:
  OCIR_REGISTRY: ${{ secrets.OCIR_REGION_KEY }}.ocir.io
  OCIR_REPO: ${{ secrets.OCIR_TENANCY_NAMESPACE }}/demo-server

jobs:
  build-and-deploy:
    name: Build, Push, and Deploy
    if: github.event_name == 'push' && github.ref == 'refs/heads/main'
    runs-on: ubuntu-latest
    steps:
      - name: Checkout Repository
        uses: actions/checkout@v4

      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          java-version: 21
          distribution: 'temurin'

      - name: Cache Gradle Dependencies
        uses: actions/cache@v4
        with:
          path: |
            ~/.gradle/caches
            ~/.gradle/wrapper
          key: ${{ runner.os }}-gradle-${{ hashFiles('**/*.gradle*', '**/gradle-wrapper.properties', '**/libs.versions.toml') }}

      - name: Build server distribution
        run: ./gradlew :server:installDist --no-daemon

      - name: Set up Docker Buildx
        uses: docker/setup-buildx-action@v3

      - name: Log in to OCI Container Registry
        uses: docker/login-action@v3
        with:
          registry: ${{ env.OCIR_REGISTRY }}
          username: ${{ secrets.OCIR_TENANCY_NAMESPACE }}/${{ secrets.OCIR_USER_NAME }}
          password: ${{ secrets.OCI_AUTH_TOKEN }}

      - name: Build and push ARM64 Docker image
        uses: docker/build-push-action@v5
        with:
          context: .
          file: server/Dockerfile
          push: true
          platforms: linux/arm64
          tags: |
            ${{ env.OCIR_REGISTRY }}/${{ env.OCIR_REPO }}:${{ github.sha }}
            ${{ env.OCIR_REGISTRY }}/${{ env.OCIR_REPO }}:latest
          cache-from: type=gha
          cache-to: type=gha,mode=max

      - name: Install OCI CLI
        run: |
          curl -L -o oci_cli_install.sh https://raw.githubusercontent.com/oracle/oci-cli/master/scripts/install/install.sh
          bash oci_cli_install.sh --accept-all-defaults
          # Note: OCI API key secrets (OCI_USER_OCID, OCI_TENANCY_OCID, OCI_FINGERPRINT, OCI_PRIVATE_KEY)
          # must be configured as GitHub secrets. The oracle-actions/configure-kubectl-oke step below
          # will use them automatically via environment variables.

      - name: Configure kubectl for OKE
        uses: oracle-actions/configure-kubectl-oke@v1.5.0
        with:
          cluster: ${{ secrets.OKE_CLUSTER_OCID }}

      - name: Update image tag in deployment manifest
        run: |
          sed -i 's|your-ocir-repo/demo-server:latest|${{ env.OCIR_REGISTRY }}/${{ env.OCIR_REPO }}:${{ github.sha }}|g' k8s/deployment.yaml

      - name: Apply Kubernetes manifests
        run: kubectl apply -f k8s/
```

---

## Phase 5: Frontend (WasmJs) CD Pipeline

**File:** `.github/workflows/deploy-web.yml`

Builds the WasmJs frontend and deploys to Cloudflare Pages.

```yaml
name: Deploy Web Frontend to Cloudflare Pages

on:
  push:
    branches: [ main ]

jobs:
  deploy:
    name: Build and Deploy WasmJs
    runs-on: ubuntu-latest
    steps:
      - name: Checkout Repository
        uses: actions/checkout@v4

      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          java-version: 21
          distribution: 'temurin'

      - name: Cache Gradle Dependencies
        uses: actions/cache@v4
        with:
          path: |
            ~/.gradle/caches
            ~/.gradle/wrapper
          key: ${{ runner.os }}-gradle-${{ hashFiles('**/*.gradle*', '**/gradle-wrapper.properties', '**/libs.versions.toml') }}

      - name: Build WasmJs production distribution
        run: ./gradlew :composeApp:wasmJsBrowserDistribution --no-daemon

      - name: Deploy to Cloudflare Pages
        uses: cloudflare/wrangler-action@v3
        with:
          apiToken: ${{ secrets.CLOUDFLARE_API_TOKEN }}
          command: pages deploy composeApp/build/dist/wasmJs/productionExecutable --project-name=demo-notes-tasks
```

> **Note:** If the wasmJs production build depends on NPM packages (e.g., the SQLite web worker), ensure `composeApp/package.json` and any linked local packages are in the repository. The Gradle wasmJs task will resolve NPM dependencies automatically.

---

## Phase 6: GitHub Secrets Reference

The following secrets must be configured in the GitHub repository (Settings → Secrets and variables → Actions):

### OCI / OCIR / OKE Secrets (Server Deployment)

| Secret | Description | Source |
|--------|-------------|--------|
| `OCIR_REGION_KEY` | OCI region key (e.g. `iad`, `phx`, `lhr`) | OCI Console → Regions |
| `OCIR_TENANCY_NAMESPACE` | OCI object storage namespace (tenancy namespace) | OCI Console → Tenancy Details |
| `OCIR_USER_NAME` | OCI username for registry auth | OCI Console → My Profile |
| `OCI_AUTH_TOKEN` | Auth token for OCIR `docker login` | OCI Console → User Settings → Auth Tokens |
| `OCI_USER_OCID` | OCID of the OCI user | OCI Console → My Profile |
| `OCI_TENANCY_OCID` | OCID of the OCI tenancy | OCI Console → Tenancy Details |
| `OCI_FINGERPRINT` | Fingerprint of the OCI API key | OCI Console → User Settings → API Keys |
| `OCI_PRIVATE_KEY` | Content of the private key `.pem` file | Generated when creating API key |
| `OCI_REGION` | OCI region name (e.g. `us-ashburn-1`) | OCI Console → Regions |
| `OKE_CLUSTER_OCID` | OCID of the OKE cluster | OCI Console → Developer Services → Kubernetes Clusters |

### Cloudflare Secrets (Web Deployment)

| Secret | Description | Source |
|--------|-------------|--------|
| `CLOUDFLARE_API_TOKEN` | Cloudflare API token with Pages write permission | Cloudflare Dashboard → My Profile → API Tokens |

---

## Phase 7: OCI Infrastructure Setup

### 7.1 OCI Container Registry (OCIR)
1. Navigate to **OCI Console → Developer Services → Container Registry**
2. Create a repository named `demo-server`
3. Make it **Private**
4. Note the full path: `<region-key>.ocir.io/<tenancy-namespace>/demo-server`

### 7.2 OKE Cluster
1. Navigate to **OCI Console → Developer Services → Kubernetes Clusters (OKE)**
2. Click **Create Cluster → Quick Create**
3. Select **ARM-based** (VM.Standard.A1.Flex) for free tier eligibility
4. Set **Number of nodes:** 2–3
5. Wait for cluster provisioning (~5 minutes)
6. Copy the **Cluster OCID** for the `OKE_CLUSTER_OCID` secret

### 7.3 IAM User & API Key
1. In **OCI Console → Identity → Users**, select your user
2. Go to **API Keys → Add API Key**
3. Download the private key `.pem` file
4. Copy the **fingerprint** shown after upload
5. Set the contents of the `.pem` file as `OCI_PRIVATE_KEY` secret in GitHub

### 7.4 Auth Token for Docker Login
1. In **OCI Console → Identity → Users → User Details**
2. Click **Auth Tokens → Generate Token**
3. Copy the generated token immediately (shown once)
4. Set as `OCI_AUTH_TOKEN` secret in GitHub

### 7.5 Network Security
Ensure the VCN created by OKE has:
- **Public subnet** for the LoadBalancer (ingress on port 80/443 from 0.0.0.0/0)
- **Private subnet** for worker nodes (ingress from load balancer subnet on port 8080)
- Health check: OCI Load Balancer will ping `/` on port 8080

---

## Phase 8: Local Validation

Before pushing to CI, validate locally:

### Build and test server
```bash
./gradlew :server:build :server:test
```

### Build server distribution
```bash
./gradlew :server:installDist
```

### Build and run Docker image locally
```bash
# Build the image
docker build -f server/Dockerfile -t demo-server:latest .

# Run it (SQLite data persists in ./data on host)
mkdir -p data
docker run -p 8080:8080 -v $(pwd)/data:/app/server/data demo-server:latest

# Verify
curl http://localhost:8080/
# Should respond: "Notes server is running"
```

### Build WasmJs web frontend
```bash
./gradlew :composeApp:wasmJsBrowserDistribution
# Output: composeApp/build/dist/wasmJs/productionExecutable/
```

---

## Phase 9: Verification & Rollout

### 9.1 After First Deployment
1. **Check OKE pods:**
   ```bash
   kubectl get pods
   # Expected: 1/1 Running
   ```
2. **Check service and get Load Balancer IP:**
   ```bash
   kubectl get svc demo-server-service
   # EXTERNAL-IP will provision after ~2 minutes
   ```
3. **Verify health endpoint:**
   ```bash
   curl http://<EXTERNAL-IP>/
   # Response: "Notes server is running"
   ```
4. **Test authenticated endpoints:**
   ```bash
   curl -X POST http://<EXTERNAL-IP>/auth/login \
     -H "Content-Type: application/json" \
     -d '{"username":"admin","password":"password"}'
   # Response: {"token":"..."}
   ```
5. **Test public routes:**
   ```bash
   curl http://<EXTERNAL-IP>/public/posts
   # Response: {"posts":[],"total":0,"offset":0}
   ```

### 9.2 Web Frontend Verification
1. Check Cloudflare Pages dashboard for deployment status
2. Open the Pages URL in a browser
3. Verify the app loads and can connect to the server (enter the Load Balancer IP in Settings)

### 9.3 Rollback
If a deployment fails:
- **Docker image:** The previous `:latest` tag is overwritten. Re-run the workflow on a previous commit.
- **Kubernetes:** Use `kubectl rollout undo deployment/demo-server` to revert to the previous replica set.
- **Cloudflare Pages:** Use the Cloudflare dashboard to publish a previous deployment.
- **Database:** Rolling back a pod does NOT revert SQLite schema changes on the PVC. If a deployment includes database migrations, you must manually restore the SQLite database from a backup or re-run previous migrations in reverse. Consider taking a snapshot of the PVC before deploying schema changes.

> **⚠️ Important:** 1-replica mode avoids concurrent-write corruption, but the PVC's SQLite database is a single point of data — back it up before any deployment that alters the schema. Use `kubectl cp` or configure automated PVC backups via OCI Block Volume backups.

---

## Phase 10: Future Considerations

| Topic | Suggestion |
|-------|-----------|
| **Android builds** | Add matrix job with `ubuntu-latest` + Android SDK via `android-hash/setup-android` action |
| **iOS builds** | Add macOS runner job with Xcode and `./gradlew :composeApp:linkDebugFrameworkIosArm64` |
| **Database upgrade** | Switch from SQLite to PostgreSQL or Oracle DB using OCI Autonomous Database for production HA |
| **Kubernetes-native DB** | Deploy CloudNativePG (CNPG) operator for managed PostgreSQL on OKE |
| **SSL/TLS** | Add cert-manager + Let's Encrypt for HTTPS on the Load Balancer |
| **DNS** | Map a custom domain to the Load Balancer IP via OCI DNS or Cloudflare |
| **Monitoring** | Add Prometheus + Grafana (or OCI Monitoring) for pod health and request metrics |
| **GitHub branch protection** | Require CI checks to pass before merging PRs to `main` |
| **Multi-environment** | Add staging environment (e.g., `staging` branch deploys to a separate OKE namespace) |
| **Secrets management** | Add Kubernetes `Secret` resources for DB credentials, JWT keys, etc. Use OCI Vault or External Secrets Operator for production |
| **SQLite → PostgreSQL** | Critical for multi-replica HA. Move to PostgreSQL via OCI Autonomous Database or CloudNativePG operator to safely support 2+ replicas |

---

## Summary of Files to Create

| File | Description |
|------|-------------|
| `.github/workflows/ci.yml` | Build and test all modules on push/PR |
| `.github/workflows/deploy-server.yml` | Build Docker image, push to OCIR, deploy to OKE |
| `.github/workflows/deploy-web.yml` | Build WasmJs, deploy to Cloudflare Pages |
| `server/Dockerfile` | Multi-stage Docker build for the Ktor server |
| `k8s/deployment.yaml` | Kubernetes Deployment (1 replica) + PVC (1Gi) |
| `k8s/service.yaml` | OCI LoadBalancer service on port 80 → 8080 |
| `.dockerignore` | Excludes unnecessary files from Docker build context |

The existing `DEPLOYMENT_PLAN.md` has been fully replaced with this comprehensive plan. Toggle to **Act mode** if you'd like me to write out all the new files (workflows, Dockerfile, k8s manifests, .dockerignore) into the project.
