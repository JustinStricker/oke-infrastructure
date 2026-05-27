# Architecture Review: oke-infrastructure

> Date: 2026-05-27

## Overview

This is a **monorepo** with two major components:

1. **`app/`** — A Kotlin Multiplatform (KMP) notes & tasks app called **"Notable"**, targeting Android, iOS, Desktop (JVM), and Web (WasmJS). Uses Compose Multiplatform, Room 3.0, Ktor (client + server), JWT auth, and a custom bidirectional sync engine with three-tier visibility (Local/Private/Public).

2. **`infrastructure/`** — OCI (Oracle Cloud) infrastructure provisioned via OpenTofu (Terraform fork): VCN, OKE cluster (ARM Ampere nodes), IAM policies, Object Storage backups. PostgreSQL is deployed inside the cluster via CloudNativePG (CNPG) operator. CI/CD via 6 GitHub Actions workflows.

---

## App Architecture

### Architecture: MVVM + Repository + Decorator

```
┌─────────────────────────────────────────────────────────────────────┐
│                      composeApp (UI Layer)                          │
│                                                                     │
│  Screens (Composables) ───> ViewModels ───> Repositories           │
│  NotesListScreen           NotesViewModel   SyncingNotesRepository │
│  TasksScreen               TasksViewModel   SyncingTasksRepository │
│  SettingsScreen            SyncViewModel                           │
│  ExploreScreen             ExploreViewModel                        │
│  NoteEditorScreen                                                  │
│  ExplorePostDetailScreen                                           │
└──────────────────────────┬──────────────────────────────────────────┘
                           │
┌──────────────────────────▼──────────────────────────────────────────┐
│                      shared Module (Data Layer)                     │
│                                                                     │
│  Data Models:  Note, Task, Visibility, SyncResponse                │
│  API Clients:  NoteClient, TaskClient (extends BaseClient)         │
│  Settings:     AppSettings (multiplatform-settings)                │
│  Server Config: expect/actual for getServerUrl()                   │
└──────────────────────────┬──────────────────────────────────────────┘
                           │
┌──────────────────────────▼──────────────────────────────────────────┐
│                 composeApp/src (Local Persistence)                  │
│                                                                     │
│  Room 3.0 Database:  AppDatabase (expect/actual constructors)       │
│  DAOs:                NoteDao, TaskDao                              │
│  Entities:            NoteEntity, TaskEntity                        │
│  Room Repos:          RoomNotesRepository, RoomTasksRepository      │
│  TypeConverters:      Converters (List<String>, Visibility)         │
└──────────────────────────┬──────────────────────────────────────────┘
                           │
┌──────────────────────────▼──────────────────────────────────────────┐
│                      server (Ktor Backend)                          │
│                                                                     │
│  Application.kt         → Module configuration, DI, routing        │
│  AuthService/AuthRoutes → JWT login (admin/password)               │
│  NotesService/Routes    → CRUD + toggle-task                       │
│  TasksService/Routes    → CRUD                                      │
│  SyncRoutes             → GET /sync/notes, /sync/tasks             │
│  PublicRoutes           → GET /public/posts (paginated, no auth)   │
│  DatabaseFactory/Tables → Exposed ORM, SQLite                     │
│  AuthExtensions         → ownerId() helper for JWT principal       │
└─────────────────────────────────────────────────────────────────────┘
```

### Key Patterns

| Pattern | Usage |
|---|---|
| **Repository** | Interfaces define the contract; Room-backed and network-aware implementations per domain |
| **Decorator** | `Syncing*Repository` wraps local Room repos with network-aware behavior (server-first writes, dirty fallback, sync pulls) |
| **MVVM** | ViewModels use `MutableStateFlow`/`StateFlow` with `collectAsState()` in composables |
| **Type-safe Navigation** | Sealed class `Screen` hierarchy with `@Serializable` and `toRoute()`/`composable<>()` |
| **expect/actual** | Platform-specific DB construction, server URL defaults |

### Module Structure

```
app/
├── composeApp/                  # Multiplatform UI client (Android, iOS, JVM, WasmJS)
│   └── src/
│       ├── commonMain/          # Shared UI + business logic
│       ├── androidMain/         # Android-specific (MainActivity, DB)
│       ├── iosMain/             # iOS-specific (MainViewController, DB)
│       ├── jvmMain/             # JVM desktop entry point
│       └── wasmJsMain/          # Web entry point
├── shared/                      # Shared data layer (models, API clients, settings)
├── server/                      # Ktor backend
└── iosApp/                      # iOS entry point (Swift)
```

### App Strengths

- Well-organized modular structure with clear separation of concerns
- Thoughtful sync engine design (server-authoritative, dirty tracking for offline resilience)
- Rich custom Markdown parser supporting headings, code blocks, tables, checkboxes, etc.
- Three-tier visibility model (LOCAL / PRIVATE / PUBLIC) with appropriate access controls
- Good test coverage for data model mapping and sync integration (4 test files)
- Clean, consistent Kotlin style with modern idioms (kotlin.uuid.Uuid, flows, coroutines)

### App Weaknesses

| Issue | Severity | Location |
|---|---|---|
| **Hardcoded JWT secret** | Critical | `server/.../auth/JwtConfig.kt` — `"my-super-secret-key-that-should-be-in-env-vars"` |
| **Hardcoded admin credentials** | Critical | `server/.../auth/AuthService.kt` — `VALID_USERNAME = "admin"`, `VALID_PASSWORD = "password"` |
| **No ViewModel tests** | Medium | NotesViewModel, TasksViewModel, SyncViewModel, ExploreViewModel untested |
| **No server-side tests** | Medium | Test directory exists but no test files |
| **No UI/composable tests** | Medium | No Compose UI tests |
| **Error swallowing in catch blocks** | Medium | Multiple empty `catch (_: Exception) { }` blocks |
| **Manual DI boilerplate** | Low | Koin removed; dependencies wired inline with `remember {}` in App() |
| **No light theme** | Low | Only dark color scheme defined |
| **NoteClient/TaskClient overlap** | Low | NoteClient handles both notes and tasks API; TaskClient duplicates some endpoints |
| **Task tags not synced** | Low | Server-side `task_tags` table missing |

---

## Infrastructure Architecture

### Provisioned Resources

| Layer | Resources |
|---|---|
| **Networking** | VCN (10.0.0.0/16), Internet Gateway, Service Gateway, 3 subnets (API endpoint, nodes, LB), route tables, 2 security lists |
| **OKE Cluster** | Basic cluster, OCI VCN IP Native CNI, public API endpoint, 4x VM.Standard.A1.Flex (ARM, 1 OCPU, 6GB RAM) |
| **IAM** | Dynamic group (all instances in compartment), policy (OCIR read, volume management, VNet use) |
| **Backups** | OCI Object Storage bucket (versioned, `prevent_destroy`) |
| **Remote State** | S3-compatible OCI Object Storage backend |
| **PostgreSQL** | CloudNativePG operator, 1 instance, 10Gi, Barman Cloud WAL archiving |

### Infrastructure Strengths

- Well-organized OpenTofu code with single-responsibility files
- Excellent documentation (README with architecture diagram, thorough SECURITY.md)
- Strong CI/CD patterns: confirmation gates for destroy, state backup before apply, idempotent imports
- Shell scripts use `set -euo pipefail`, bounded waits, idempotent patterns
- Free-tier eligible (Ampere ARM shapes)
- OCIR credential provider (dynamic short-lived creds, not static image pull secrets)
- `prevent_destroy` on backup bucket prevents accidental data loss

### Infrastructure Weaknesses

| Issue | Severity | Location |
|---|---|---|
| **SSH open to world** | Critical | `networking.tf:191` — port 22 from `0.0.0.0/0` to worker nodes |
| **Public K8s API endpoint** | Critical | `oke.tf:15` — `is_public_ip_enabled = true` |
| **Static S3 creds in K8s secret** | Critical | `scripts/setup-backup.sh` — AWS keys persisted in cluster, any pod with Secrets API access can read (acknowledged in SECURITY.md) |
| **No state locking** | High | OCI Object Storage lacks DynamoDB-equivalent; concurrent runs could corrupt state |
| **All subnets allow public IPs** | High | `prohibit_public_ip_on_vnic = false` on all 3 subnets; nodes should be private |
| **Dynamic group too broad** | High | Matches ALL instances in compartment, not just OKE nodes |
| **API endpoint ingress from 0.0.0.0/0** | High | `networking.tf:229` |
| **No scheduled drift detection** | Medium | `drift-check.yml` is manual-only |
| **Duplicated cert-manager cleanup** | Medium | Same 10-line block in Makefile, deploy-postgres.sh, and deploy-postgresql.yml |
| **reset-postgres.sh duplicates deploy-postgres.sh** | Medium | Should call deploy-postgres.sh instead of reimplementing |
| **No CI for KMP app** | Medium | Workflows are infrastructure-only; app/ has no CI pipeline |
| **No PR plan comments** | Low | Apply workflow runs `tofu plan` but doesn't post output to PR |
| **Hardcoded version strings** | Low | CSI driver version `v1.34.0` hardcoded in multiple places |
| **No tagging strategy** | Low | No consistent `defined_tags` for cost tracking |
| **Single node pool** | Low | No taints/tolerations to isolate PostgreSQL workloads |
| **Production CNPG config commented out** | Low | HA affinity config, `instances: 3` are commented out |

---

## Recommendations

### Critical (Address Immediately)

1. **Move JWT secret to environment variable** — `JwtConfig.kt` should read from an env var or config file, not hardcode.
2. **Move admin credentials to environment variable** — `AuthService.kt` should validate against env-configured credentials.
3. **Restrict SSH access** — `networking.tf:191`: change source from `0.0.0.0/0` to organization CIDR or remove the rule.
4. **Move to private API endpoint** — `oke.tf:15`: set `is_public_ip_enabled = false`, access via bastion/VPN.
5. **Migrate to instance principal for backups** — Remove static S3 credentials from K8s secret; use the dynamic group policy for Object Storage access.

### High Priority

6. **Make node subnet private** — Set `prohibit_public_ip_on_vnic = true` on the node subnet.
7. **Add state locking** — Implement a locking mechanism (consider OCI Resource Manager or a lock provider).
8. **Add scheduled drift detection** — Add a `schedule` trigger to `drift-check.yml`.
9. **Restrict dynamic group rule** — Narrow to match only OKE-launched instances (by tag or shape).
10. **Add ViewModel and server tests** — Cover the untested ViewModels and server endpoints.

### Medium Priority

11. **Deduplicate cert-manager cleanup** — Extract shared cleanup logic into a single script.
12. **Consolidate reset-postgres.sh and deploy-postgres.sh** — Have reset-postgres.sh call deploy-postgres.sh.
13. **Post plan output to PRs** — Add a step in `apply.yml` to comment plan on pull requests.
14. **Add CI for KMP app** — Introduce build/test workflows for the app module.
15. **Remove error swallowing** — Handle or log exceptions in empty catch blocks.
16. **Add tagging strategy** — Implement consistent `defined_tags` for cost tracking and resource identification.

### Low Priority

17. **Cache `oci os ns get`** — Reduce redundant API calls in workflows.
18. **Parameterize version strings** — CSI driver and other pinned versions should be variables.
19. **Enable production CNPG config** — HA settings with `instances: 3` for production.
20. **Add node taints for PostgreSQL** — Isolate PostgreSQL from generic workloads.
21. **Add light theme** — Implement the planned light color scheme.
22. **Clean up task_tags sync** — Add server-side task_tags table for complete task tag support.
