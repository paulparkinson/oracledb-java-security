# Evaluation status

Last updated: **2026-10-08**. This is an engineering evaluation, not a production-support certification.

## Outcome

**OKE identity and financialdb-scoped token acquisition PASS; the denied-account control PASSes by rejection. JDBC login FAILS with ORA-01017.** An isolated namespace, two service accounts, test Jobs/source ConfigMaps and one narrowly scoped policy have been deployed with approval. No database users, passwords, data or identity-provider settings were changed.

| Check | Observed outcome | Evidence / limit |
|---|---|---|
| Public JDBC provider review | Completed | Released OCI provider 1.1.0 and pinned public source select the generic resource-principal builder, not an explicit OKE builder. Runtime equivalence is not established. |
| Maven compile, package and tests | PASS | 14 JUnit tests, zero failures/errors; 2026-10-08 |
| Kubernetes Job generator tests | PASS | 11 Node tests, including isolation, negative account, digest pinning, source bundle and private evidence opt-in |
| Offline configuration executable | PASS | Ran packaged Java `check` with non-secret example inputs; no network/database call |
| Blog rendering | PASS | Headless Chromium at 1360px and 390px; image loaded, local links resolve, no page overflow; screenshots visually reviewed |
| Intended OCI tenancy | Located | Operator read-only inventory confirmed the requested tenancy; resource identifiers kept outside Git |
| Current enhanced OKE cluster | Reachable | ACTIVE; Kubernetes v1.36.0; two Ready nodes on read-only inspection |
| Target Autonomous Database | Located | AVAILABLE; optional mTLS advertised. This does not establish IAM configuration, mapping or SQL connectivity. |
| Explicit OKE identity inside a pod | PASS | `jdbc-allowed-identity-live1`; 18:11:20 UTC |
| Database-scoped token issuance | PASS | `jdbc-allowed-token-live1`; 18:12:47 UTC; exact financialdb scope |
| JDBC session with expected identity | FAIL | `jdbc-allowed-jdbc-live1`; 18:16:14 UTC; ORA-01017; no successful SQL session |
| Negative service-account control | PASS (rejected) | `jdbc-denied-token-live1`; 18:15:04 UTC; identity succeeds, token request returns HTTP 404 NotAuthorizedOrNotFound |
| Native `OCI_RESOURCE_PRINCIPAL` comparison | FAIL | `jdbc-allowed-native-resource-principal-live1`; 18:16:09 UTC; ORA-18726; provider acquisition fails |
| Native-provider cause | CONFIRMED | Diagnostic rerun identifies missing `OCI_RESOURCE_PRINCIPAL_VERSION`; value never logged |
| Private workload subject capture | PASS | `jdbc-allowed-token-evidence1`; 18:20:18 UTC; workload OCID only in private pod termination metadata, no JWT/key |
| Token renewal across physical connections | NOT RUN | Requires initial login success and observed subsequent token acquisition |

## Open decisions and prerequisites

1. **ADMIN access recovered:** after the operator corrected the password and wallet, the read-only ADMIN retry succeeded against financialdb. This verification used wallet-free TCPS EZConnect+ on port 1521, so it verifies the corrected credential and TLS connection, not the replacement wallet.
2. ADMIN inspection confirms `identity_provider_type=OCI_IAM`. `TOKEN_DEMO` exists with GLOBAL authentication; `OKE_JDBC_DEMO` does not exist. Preserve that existing user and the current identity provider. Oracle documents exclusive `IAM_PRINCIPAL_OCID` mappings, and an A-Team article applies this to OKE workloads. Validate the actual workload subject against that mechanism; do not invent a dynamic-group mapping.
3. Create only an explicitly approved dedicated mapping with CREATE SESSION, if the database configuration supports it, and rerun JDBC, negative and renewal tests. No schema mapping has been created yet.
4. Deployment approval was received. Kubernetes authenticated the existing native OCI operator in the intended tenancy; it is not the separate federated email-account profile, whose credentials failed. No claim is made that the federated profile performed these operations.
5. Docker was unavailable; the live evaluation used a public, digest-pinned Maven container to build the source in a bounded Job. No image registry credential was added. Repository visibility remains private; internal correspondence is excluded.

## Deployment inventory (non-secret summary)

- Existing cluster: **financial-demo**, Frankfurt; namespace **jdbc-workload-identity**.
- Existing database: **financialdb**; server-authenticated TLS endpoint, port **1521**. The optional mutual-TLS endpoint is different; no wallet is mounted in test pods.
- Service accounts: **jdbc-allowed**, **jdbc-denied**; no added cluster-wide RBAC permissions.
- Policy: **jdbc-workload-identity-financialdb**, scoped to the exact cluster/namespace/allowed service account and financialdb. Existing policies were preserved. Root-policy inspection found no statements containing `workload`; the live negative control provides the stronger authorization evidence.
- Image: `docker.io/library/maven@sha256:f58d59b6273e785ac0a4477f6e9b5ba1d7731c75b906c0f7b34076f1851318cc`.
- Initial source bundle SHA-256 prefix: `8b143189e727`; exact source digest is recorded on each Job's `evaluation.source.sha256` annotation. Later diagnostic Jobs use a new immutable source bundle.
- No LoadBalancer, public service, new cluster, database restart or inventory mutation was performed. Finished Jobs expire after 24 hours; namespace, policy, service accounts and source ConfigMaps remain until reviewed cleanup.

## Current diagnosis

| Hypothesis | Confidence | Evidence / gap |
|---|---|---|
| Dedicated expected database user is missing | Confirmed | ADMIN inspection now succeeds: OCI_IAM is enabled, but OKE_JDBC_DEMO is absent. Creating the approved mapping and rerunning JDBC is still required to establish end-to-end success. |
| Native JDBC resource-principal acquisition is not configured for this OKE pod | High, 8/10 | Separate unchanged-provider Job fails with ORA-18726, before a SQL session; no resource-principal environment was injected. |
| Current financialdb or cluster outage | Low, 1/10 | Nodes Ready; workload token tests complete; existing FINANCIAL application login succeeds. |

Read-only evidence includes the OKE discovery/workload-identity collectors, exact policy inspection, Job results, and a separate FINANCIAL application session. The collector's keyword-based “anomalies” include normal token projections; they are not independently confirmed incidents.

## Incident and interruption log

### 2026-10-08 — ADMIN access recovered

- Operator reported correcting an old wallet and an incorrect password.
- Read-only ADMIN connection now succeeds against the intended financialdb. The check uses server-authenticated TLS without a wallet; replacement-wallet validation is outside this result.
- Confirmed OCI_IAM is already enabled and the proposed OKE_JDBC_DEMO user is absent. Existing TOKEN_DEMO is GLOBAL; it was not modified.
- Requested explicit approval to create only OKE_JDBC_DEMO, mapped to the privately verified workload subject, with CREATE SESSION only. No database mutation has been performed; previous workload JDBC and native-provider failures remain the last observed results.

### 2026-10-08 — database-side verification blocked

- The GCP repository's credentials target a different database, not financialdb. They were not used in any OKE workload.
- The financial setup file targets the correct financialdb, but its stored ADMIN login also returns ORA-01017. Its FINANCIAL application login works; this is not evidence of a database outage.
- The application user lacks access to the administrative parameter view. No privilege escalation, password reset, identity-provider replacement or database restart was attempted.
- Requested the current ADMIN credential through the existing private configuration file, not chat.
- Retried after the operator updated the file at 18:21:31 UTC; ADMIN still returned ORA-01017. Checked parsing for stray quotes, variable expressions and escape characters; none were present. Further login attempts paused pending verification to avoid repeated failed logins.

### 2026-10-08 — stale local Kubernetes context

- Symptom: the laptop's previously selected Kubernetes API endpoint refused connections; its cluster lookup returned 404.
- Finding: current OCI inventory contained a different active enhanced cluster. A separate temporary kubeconfig reached that cluster and both nodes were Ready.
- Action: generated a separate local kubeconfig; did not change the user's default context or any cluster resource.
- Classification: stale local access configuration. **Not evidence of an outage of the current cluster.**

### 2026-10-08 — offline implementation corrections

- Initial compilation used a method not exposed by the chosen SDK provider; corrected to the signing-identity API and rebuilt.
- Initial OCID validation incorrectly rejected region hyphens; corrected and reran all tests.
- These were local implementation defects, not cloud incidents.

## Recording the next live run

Record UTC start/end, Git commit, image digest, dependency versions, mode, Job name, positive/negative service account, exit code, sanitized stage result and independently correlated OCI audit/database evidence. Record exact error codes without tokens or raw exception bodies. Keep resource OCIDs and full identity claims in a private operator inventory outside Git.

Only change a row from NOT RUN to PASS after observing that stage. Token issuance alone must not change JDBC or renewal status. On unsupported identity/mapping, record the failing boundary and ask Oracle for the supported integration; do not widen permissions or use a node principal to manufacture success.
