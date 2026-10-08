# Operations

See [STATUS.md](STATUS.md) for the goal, blocker and next diagnostic checks.

## Configuration

- Cluster: `financial-demo`, Frankfurt. Namespace: `jdbc-workload-identity`. Use its explicit private kubeconfig; do not change the default context.
- Database: `financialdb`. Workload user: `OKE_JDBC_DEMO`, with only `CREATE SESSION`.
- Local ADMIN check: root `.env` fields `OCI_DB_ADMIN_PASSWORD`, `OCI_TNS_ADMIN`, `OCI_DB_URL`. Keep that file and all credentials out of pods and Git.
- Pod test: non-secret `config.local.env`; TCPS EZConnect+ only. No password, API-key or node-principal fallback.

## Inspect a test

```sh
kubectl --kubeconfig "$DEMO_KUBECONFIG" -n jdbc-workload-identity get jobs,pods
kubectl --kubeconfig "$DEMO_KUBECONFIG" -n jdbc-workload-identity logs job/JOB_NAME
```

- Keep only result, error code, relevant versions and a failing-test timestamp. No troubleshooting diary or raw tokens/keys.
- `ORA-01017` after token issuance is an authentication failure, not proof of a bad wallet, a database outage or a particular product defect.
- Request targeted authentication diagnostics securely; do not enable unrestricted token tracing in shared logs.

## Cleanup

- Temporary `jdbc-mtls-wallet` Secret: already removed; local wallet preserved.
- Finished Jobs expire after 24 hours. Namespace, service accounts, policy and source ConfigMaps remain.
- Obtain approval before removing those resources. Resolve exact targets first; preserve shared workloads.
- Database rollback, if approved: verify the exact user/mapping, no active sessions and zero owned objects, then drop only `OKE_JDBC_DEMO` without CASCADE. Preserve existing users.
