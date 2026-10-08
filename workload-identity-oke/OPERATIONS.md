# Operating and revisiting the evaluation

Read [STATUS.md](STATUS.md) before running anything. The repository creates no cloud resources automatically. The test has no public listener, ingress or LoadBalancer and does not write application data.

## Preflight

1. Verify the operator's OCI account and tenancy, not just a familiar CLI profile name. Set an explicit region and use a separate kubeconfig for the exact approved cluster. Never overwrite a working default context.
2. Read back the cluster's enhanced type/state and inspect nodes with `kubectl --kubeconfig "$DEMO_KUBECONFIG" get nodes`. Inspect existing resources in the proposed namespace before creating anything.
3. Review the existing policies and confirm the test account is not accidentally permitted by a broad pre-existing policy. IAM is additive: a new narrow policy cannot remove an existing broad grant.
4. Resolve database IAM enablement, the supported workload mapping and TLS reachability separately. Do not switch an existing database's external identity provider to make a demo work.
5. Review `config.local.env`, ensure it contains no passwords or tokens, and pin `DEMO_IMAGE` by digest. The example is intentionally not runnable until placeholders are replaced.

For **local administrator verification**, use only the requested repository-root private `.env`: `OCI_DB_ADMIN_PASSWORD` for ADMIN, `OCI_TNS_ADMIN` for the selected wallet, and `OCI_DB_URL` for the verified TCPS EZConnect+ endpoint. `OCI_DB_USERNAME` can name an application user instead; do not pair that username with the ADMIN password. Do not source this private file into a test-pod environment. ADMIN/password success proves that connection path, not OKE token authentication.

If comparing mTLS in OKE, obtain separate approval for copying `cwallet.sso` into the exact test namespace's `jdbc-mtls-wallet` Secret and removing it afterward. It contains private-key material. The optional renderer mounts only that file read-only; no database password is uploaded. Stop rather than overwrite a pre-existing Secret. The original local wallet is not deleted during temporary Secret cleanup.

## Test sequence and evidence

Use the README procedure in order: identity → token → JDBC → denied-account control → native-provider comparison → renewal. Explicit OKE mode constructs the OKE provider directly; it does not use a credential-discovery chain. The native comparison is separate and must not inherit externally supplied resource-principal credentials.

Inspect a bounded Job without dumping its environment:

```sh
kubectl --kubeconfig "$DEMO_KUBECONFIG" -n jdbc-workload-identity get jobs,pods
kubectl --kubeconfig "$DEMO_KUBECONFIG" -n jdbc-workload-identity logs job/jdbc-allowed-identity-manual
```

Logs deliberately omit raw exception messages, security tokens, keys and full identity claims. Correlate time and service-account identity in OCI Audit where available, and have the database administrator independently verify the resulting session identity. Never enable verbose SDK/JDBC tracing in a shared log collector to troubleshoot tokens.

The `acquisition` count measures database-token acquisition, not OKE identity refresh. Renewal testing must cover the relevant lifetimes and open new physical connections. A cached live SQL session proves neither refresh path.

## Failure triage

| Symptom | Investigate first | Do not do |
|---|---|---|
| Kubernetes API refused / cluster not found | Exact kubeconfig endpoint, cluster inventory, operator access | Recreate the cluster or treat a stale endpoint as a service outage |
| Image pull failure | Image digest, registry access, node architecture | Put registry credentials in the application environment |
| Pod identity failure | Enhanced cluster, service account, projected token, SDK/provider configuration | Fall back to node instance principal or local API keys |
| Database-token HTTP 401/403/404 | Caller identity, principal support, exact policy/scope and audit evidence | Assume a broad policy fixes an unsupported principal |
| JDBC error after token success | Supported mapping, IAM enablement, TLS hostname/service, driver version | Disable TLS identity checks or add an admin password |
| Wrong session user | Database mapping and identity correlation | Count connection establishment as success |
| Negative control succeeds | Pre-existing broad grants, manifest service account, identity correlation | Report least privilege as verified |
| Renewal fails | Actual token expiration, fresh connection results, acquisition counter | Declare success from an already-open connection |

Record incident onset, scope, observed errors, recovery and remaining uncertainty in STATUS.md. Do not label a service outage without service-side evidence.

## Cleanup — explicit approval and exact targets

Jobs have a two-hour maximum configured deadline and a 24-hour finished-job TTL. Save sanitized evidence before the TTL removes Jobs. Policies and service accounts do not expire with a Job.

Before cleanup, inspect the dedicated namespace and confirm that it contains only this evaluation's resources. Delete only named evaluation Jobs and the two service accounts using the explicit kubeconfig/namespace, then remove the empty dedicated namespace. Remove only the evaluation policy by its recorded OCID after approval; do not alter other policy statements. Remove the test image only after confirming no Jobs need it. Any approved database user/mapping needs its own reviewed rollback, preserving existing users and data.

The current database addition is `OKE_JDBC_DEMO` only. For a separately approved rollback, verify the exact financialdb target, the stored workload mapping, zero owned objects and no active sessions first. Then drop only that user **without CASCADE**; stop if Oracle reports owned objects rather than deleting them. No rollback was executed during the login investigation. Do not drop or alter the pre-existing `TOKEN_DEMO` user.

No automatic teardown command is provided for shared cloud infrastructure. Resource IDs and rollback records belong in a private operator inventory, not this repository.
