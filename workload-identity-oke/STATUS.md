# Evaluation status

Last updated: **2026-10-08**. This is an engineering evaluation, not a production-support certification.

## Outcome

**OKE identity and financialdb-scoped token acquisition PASS; the denied-account control PASSes by rejection. JDBC login still FAILS with ORA-01017 after the approved database mapping was created.** `OKE_JDBC_DEMO` is GLOBAL/OPEN, mapped to the verified workload OCID, with exactly `CREATE SESSION` and no admin option, roles or object grants. Existing users, passwords, application data and identity-provider settings were preserved.

| Check | Observed outcome | Evidence / limit |
|---|---|---|
| Public JDBC provider review | Completed | Released OCI provider 1.1.0 and pinned public source select the generic resource-principal builder, not an explicit OKE builder. Runtime equivalence is not established. |
| Maven compile, package and tests | PASS | 15 JUnit tests, including synthetic token-copy/wipe regression; 2026-10-08 |
| Kubernetes Job generator tests | PASS | 11 Node tests, including isolation, negative account, digest pinning, source bundle and private evidence opt-in |
| Offline configuration executable | PASS | Ran packaged Java `check` with non-secret example inputs; no network/database call |
| Blog rendering | PASS | Headless Chromium at 1360px and 390px; image loaded, local links resolve, no page overflow; screenshots visually reviewed |
| Intended OCI tenancy | Located | Operator read-only inventory confirmed the requested tenancy; resource identifiers kept outside Git |
| Current enhanced OKE cluster | Reachable | ACTIVE; Kubernetes v1.36.0; two Ready nodes on read-only inspection |
| Target Autonomous Database | Located | AVAILABLE; optional mTLS advertised. This does not establish IAM configuration, mapping or SQL connectivity. |
| Explicit OKE identity inside a pod | PASS | `jdbc-allowed-identity-live1`; 18:11:20 UTC |
| Database-scoped token issuance | PASS | `jdbc-allowed-token-live1`; 18:12:47 UTC; exact financialdb scope |
| Approved database mapping | PASS | Read-back at 19:04:29 UTC: GLOBAL, exact workload OCID, CREATE SESSION only; existing TOKEN_DEMO preserved |
| Post-mapping JDBC session | FAIL | Final-source `jdbc-allowed-jdbc-mappedfinal`; 19:18:20 UTC; ORA-01017; no successful workload SQL session |
| Negative service-account control | PASS (rejected) | Post-mapping `jdbc-denied-token-mapped1`; 19:04:23 UTC; identity succeeds, token request returns HTTP 404 NotAuthorizedOrNotFound |
| Token identity/scope diagnostics | PASS (limited) | Fresh token subject hash equals the mapped subject; workload type, unexpired token, past issue time, database/compartment scope present. These local checks do not establish database acceptance. |
| Native `OCI_RESOURCE_PRINCIPAL` comparison | FAIL | `jdbc-allowed-native-resource-principal-live1`; 18:16:09 UTC; ORA-18726; provider acquisition fails |
| Native-provider cause | CONFIRMED | Diagnostic rerun identifies missing `OCI_RESOURCE_PRINCIPAL_VERSION`; value never logged |
| Private workload subject capture | PASS | `jdbc-allowed-token-evidence1`; 18:20:18 UTC; workload OCID only in private pod termination metadata, no JWT/key |
| Token renewal across physical connections | NOT RUN | Requires initial login success and observed subsequent token acquisition |

## Open decisions and prerequisites

1. **ADMIN access recovered:** after the operator corrected the password and wallet, the read-only ADMIN retry succeeded against financialdb. This verification used wallet-free TCPS EZConnect+ on port 1521, so it verifies the corrected credential and TLS connection, not the replacement wallet.
2. ADMIN inspection confirms `identity_provider_type=OCI_IAM`. With explicit approval, created `OKE_JDBC_DEMO` using the privately verified workload subject and granted only CREATE SESSION. Existing `TOKEN_DEMO` and the identity provider were preserved. Read-back verified the exact mapping and absence of additional direct grants.
3. Resolve the remaining server rejection before testing renewal. A correct-looking mapping and token issuance do not prove supported end-to-end workload authentication. Do not add broad IAM/database grants, substitute node credentials, or restart the shared database to conceal or guess at the failure.
4. Deployment approval was received. Kubernetes authenticated the existing native OCI operator in the intended tenancy; it is not the separate federated email-account profile, whose credentials failed. No claim is made that the federated profile performed these operations.
5. Docker was unavailable; the live evaluation used a public, digest-pinned Maven container to build the source in a bounded Job. No image registry credential was added. Repository visibility remains private; internal correspondence is excluded.

## Deployment inventory (non-secret summary)

- Existing cluster: **financial-demo**, Frankfurt; namespace **jdbc-workload-identity**.
- Existing database: **financialdb**; server-authenticated TLS endpoint, port **1521**. The optional mutual-TLS endpoint is different; no wallet is mounted in test pods.
- Service accounts: **jdbc-allowed**, **jdbc-denied**; no added cluster-wide RBAC permissions.
- Policy: **jdbc-workload-identity-financialdb**, scoped to the exact cluster/namespace/allowed service account and financialdb. Existing policies were preserved. Root-policy inspection found no statements containing `workload`; the live negative control provides the stronger authorization evidence.
- Dedicated database user: **OKE_JDBC_DEMO**, exclusive workload-OCID mapping; only **CREATE SESSION**, no admin option, no direct role or object grants. This is the only database DDL performed by this evaluation.
- Image: `docker.io/library/maven@sha256:f58d59b6273e785ac0a4477f6e9b5ba1d7731c75b906c0f7b34076f1851318cc`.
- Initial source bundle SHA-256 prefix: `8b143189e727`; exact source digest is recorded on each Job's `evaluation.source.sha256` annotation. Later diagnostic Jobs use a new immutable source bundle.
- Final-source post-mapping retest bundle SHA-256 prefix: `8a4474259fc7`; includes the workload-subject guard and synthetic token-copy regression, without temporary metadata logging.
- No LoadBalancer, public service, new cluster, database restart or inventory mutation was performed. Finished Jobs expire after 24 hours; namespace, policy, service accounts and source ConfigMaps remain until reviewed cleanup.

## Current diagnosis

| Hypothesis | Confidence | Evidence / gap |
|---|---|---|
| Dedicated expected database user is missing | Resolved | Approved global user exists, OPEN, with exact subject mapping and CREATE SESSION only. Post-creation JDBC still fails. |
| Stale/wrong workload subject or expired token | Not observed | Fresh token subject hash matches the mapped subject; issue/expiry checks pass; intended database and compartment appear in scope. |
| Input wiping destroys the JDBC token | Ruled out for pinned driver | Synthetic regression confirms AccessToken retains a separate copy after the original character array is cleared. |
| Database rejects this workload token | Confirmed symptom; cause unresolved | Scoped token issuance succeeds; JDBC returns ORA-01017. ADMIN and FINANCIAL password sessions succeed separately. Neither proves workload-token support. |
| Native JDBC resource-principal acquisition is not configured for this OKE pod | High, 8/10 | Separate unchanged-provider Job fails with ORA-18726, before a SQL session; no resource-principal environment was injected. |
| Current financialdb or cluster outage | Low, 1/10 | Nodes Ready; workload token tests complete; existing FINANCIAL application login succeeds. |

Read-only evidence includes the OKE discovery/workload-identity collectors, exact policy inspection, Job results, and a separate FINANCIAL application session. The collector's keyword-based “anomalies” include normal token projections; they are not independently confirmed incidents.

## Incident and interruption log

### 2026-10-08 — approved workload mapping created; JDBC rejection persists

- Matched the allowed pod's captured workload subject to the private operator inventory before creating the global user. No password was assigned.
- Created only OKE_JDBC_DEMO, granted CREATE SESSION without admin option, and verified no direct role, table or column grants. Existing TOKEN_DEMO authentication/account state is unchanged.
- Initial read-back incorrectly compared the mapping prefix case-sensitively. Oracle stored it in lowercase; corrected the verifier and confirmed the exact OCID. This was a verification defect, not a failed CREATE USER.
- Post-mapping Jobs `mapped1` (19:04:25), `mapped2` (19:07:03), `token1` (19:09:30) and `claims1` (19:13:36 UTC) all returned ORA-01017. Adding the explicit OCI_TOKEN property did not fix it and was not retained. The fresh-token diagnostic verified subject/scope/time checks without logging tokens or keys; temporary metadata diagnostics were not retained in application source.
- Final-source `jdbc-allowed-jdbc-mappedfinal` returned ORA-01017 at 19:18:20 UTC after passing the 15-test build. Local Maven (15 tests) and Node manifest tests (11 tests) also passed.
- The denied-account retest returned HTTP 404 NotAuthorizedOrNotFound as expected. Limited database audit inspection found a failed LOGON with return code 1017 but no explanatory detail; no debug tracing was enabled.
- A separate operator-IAM control was stopped before token acquisition because its existing name mapping could not be independently correlated through the operator IAM lookup. It supplies no evidence of successful token login and was never substituted into the OKE test.
- No database restart, identity-provider change, extra privilege, password reset or application-data mutation was attempted. Renewal remains NOT RUN because no workload SQL session has succeeded. Next escalation should include the sanitized Job times/error codes, pinned versions and privately correlated principal/scope for Oracle investigation.

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
