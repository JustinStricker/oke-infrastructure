# Security

## Credential Hygiene

A full audit of all tracked files and git history was performed on 2026-05-27.

**No hardcoded API keys, passwords, tokens, or private keys** exist in the codebase. All secrets are handled through proper channels:

- **OCI provider** (`providers.tf`): Configured with no embedded credentials — relies on environment variables or instance metadata principal.
- **Terraform variables** (`variables.tf`): Sensitive variables (`compartment_ocid`, `tenancy_ocid`) have no default values and must be supplied at runtime via `.tfvars`, environment variables, or GitHub Actions secrets.
- **`.gitignore`**: Properly ignores `*.tfvars`, `*.pem`, `*.tfstate`, `.terraform/`.
- **`terraform.tfvars.example`**: Uses placeholder values only — no real OCIDs.
- **GitHub Actions workflows**: All six workflows reference `${{ secrets.* }}` exclusively. No secrets are hardcoded in YAML.
- **Git history**: Every commit touching `.tf` files, `.tfvars`, and workflow files was inspected. No secrets were ever committed. The former HeatWave MySQL setup used `var.db_admin_password` (a Terraform variable), never a literal value.

## Known Security Concerns

### 1. SSH Access Open to Internet

**File:** `networking.tf:191`  
**Rule:** Inbound SSH (port 22) from `0.0.0.0/0` on the worker node security list.

Any IP can attempt SSH connections to worker nodes. While OKE-managed nodes use SSH keys, this broad access increases attack surface.

**Recommendation:** Restrict the source CIDR to your organization's public IP range, or remove the rule entirely (OKE does not require it for normal operation).

### 2. Public Kubernetes API Endpoint

**File:** `oke.tf:15`  
**Setting:** `is_public_ip_enabled = true`

The cluster's Kubernetes API server is exposed to the public internet. Anyone who obtains a valid kubeconfig token can reach it.

**Recommendation:** Set `is_public_ip_enabled = false` and access the API through a bastion host, VPN, or OCI Service Connector. If a public endpoint is required, pair it with an OCI WAF or IP allow-list at the network level.

### 3. Static Backup Credentials Injected into Cluster

**File:** `scripts/setup-backup.sh:57-61`

The script creates a Kubernetes Secret named `cnpg-s3-creds` containing `AWS_ACCESS_KEY_ID` and `AWS_SECRET_ACCESS_KEY` (OCI Customer Secret Keys for S3-compatible Object Storage access). These credentials:

- Are read from environment variables at deploy time
- Persist in the cluster as long as the namespace exists
- Can be read by any pod with access to the Kubernetes Secrets API

**Recommendation:** Migrate to **OCI Instance Principal** authentication. OKE worker nodes in the dynamic group (`oke_nodes_dynamic_group_tf`) can authenticate to Object Storage without any static keys:

1. Add a policy in `iam.tf` granting the dynamic group `manage objects` on the backup bucket.
2. Remove the `kubectl create secret generic` call and the `s3Credentials` block from the ObjectStore CR in `setup-backup.sh`.
3. Remove `AWS_ACCESS_KEY_ID` and `AWS_SECRET_ACCESS_KEY` from all GitHub workflow `env:` blocks and documentation.

The barman-cloud plugin sidecar running on worker nodes inherits the node's instance principal automatically — no secrets stored anywhere.

## Secret Flow Diagram

```
GitHub Actions (secrets.*)
        │
        ▼
  ┌─────────────────┐
  │  OCI Provider    │  ◄── OCI_USER_OCID, OCI_TENANCY_OCID,
  │  (OpenTofu)      │       OCI_FINGERPRINT, OCI_PRIVATE_KEY
  └────────┬────────┘
           │
           ▼
  ┌─────────────────┐
  │  OKE Cluster     │  ◄── compartment_ocid, tenancy_ocid
  │  + Node Pool     │       (TF_VAR_* environment variables)
  └────────┬────────┘
           │
           ▼
  ┌─────────────────┐
  │  CNPG Backups    │  ◄── AWS_ACCESS_KEY_ID / AWS_SECRET_ACCESS_KEY
  │  (K8s Secret)    │       *(concern #3 — see above)*
  └─────────────────┘
```

## Scope of This Audit

The following were examined:

- All `.tf` files (providers, variables, networking, OKE, IAM, backup, backend, outputs)
- `.gitignore`
- `terraform.tfvars.example`
- All `.github/workflows/*.yml` (6 files)
- All `scripts/*.sh` (6 files)
- `k8s/postgres/cluster.yaml`, `k8s/postgres/install-operator.sh`
- `Makefile`
- `README.md`
- Full git history (`--all` branches) for every tracked file
