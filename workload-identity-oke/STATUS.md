# Evaluation status

Last updated: **2026-10-08**. This is an engineering evaluation, not a production-support certification.

## Outcome

**Implementation and offline tests pass. End-to-end OKE → database token → JDBC authentication is NOT YET VERIFIED.** No test workloads, policies or database mappings have been created by this evaluation.

| Check | Observed outcome | Evidence / limit |
|---|---|---|
| Public JDBC provider review | Completed | Released OCI provider 1.1.0 and pinned public source select the generic resource-principal builder, not an explicit OKE builder. Runtime equivalence is not established. |
| Maven compile, package and tests | PASS | 12 JUnit configuration tests, zero failures/errors; 2026-10-08 |
| Kubernetes Job generator tests | PASS | 8 Node tests, including isolation, negative account, digest pinning and secret exclusion |
| Offline configuration executable | PASS | Ran packaged Java `check` with non-secret example inputs; no network/database call |
| Blog rendering | PASS | Headless Chromium at 1360px and 390px; image loaded, local links resolve, no page overflow; screenshots visually reviewed |
| Intended OCI tenancy | Located | Operator read-only inventory confirmed the requested tenancy; resource identifiers kept outside Git |
| Current enhanced OKE cluster | Reachable | ACTIVE; Kubernetes v1.36.0; two Ready nodes on read-only inspection |
| Target Autonomous Database | Located | AVAILABLE; optional mTLS advertised. This does not establish IAM configuration, mapping or SQL connectivity. |
| Explicit OKE identity inside a pod | NOT RUN | Isolated deployment approval pending |
| Database-scoped token issuance | NOT RUN | Scoped policy approval and service compatibility unverified |
| JDBC session with expected identity | NOT RUN | Requires token success and a supported database mapping |
| Negative service-account control | NOT RUN | Must be interpreted against a successful matching positive control |
| Native `OCI_RESOURCE_PRINCIPAL` comparison | NOT RUN | Do not infer behavior from compilation/source inspection |
| Token renewal across physical connections | NOT RUN | Requires initial login success and observed subsequent token acquisition |

## Open decisions and prerequisites

1. Approve a dedicated namespace, two test service accounts, bounded Jobs and narrowly scoped IAM policy on the existing enhanced cluster. No new cluster is needed.
2. Confirm the effective operator principal before cloud mutation. One existing CLI profile can perform discovery but its configured user lookup returned 404; the profile whose user matches the requested email returned 401. This is not resolved by finding the email in the tenancy's user list.
3. Confirm an approved image registry and push/pull mechanism. Never embed registry passwords in manifests or the image.
4. Establish whether the identity-dataplane/database combination supports this workload principal and what database mapping is documented. OKE identities cannot currently join dynamic groups. No substitute mapping is invented here.
5. Keep the repository private pending clearance. Internal email text and participant details are excluded.

## Incident and interruption log

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
