# OKE workload identity → Oracle JDBC

[Engineering handoff and minimal reproducer](ENGINEERING-HANDOFF.md) — verified failure, responsible engineering areas, exact questions and server-side diagnostic options.

**Not working end to end:** workload-token login fails with `ORA-01017` in Java, Python and native SQL*Plus. A separate operator IAM-token login succeeds against the same financialdb endpoint; it is a control, not a fallback. Native tracing confirms TLS and PoP preparation. See [findings and remaining blocker](STATUS.md).

**Independent check:** `scripts/reference-client.py` uses Oracle's Python driver and explicit OKE SDK signer; it also returns ORA-01017. Run only in the approved OKE test pod with `reference-requirements.txt` installed, the same `OCI_REGION`, `OCI_COMPARTMENT_ID`, `OCI_DATABASE_ID`, a TCPS EZConnect+ `DB_DSN`, and the privately verified `EXPECTED_WORKLOAD_SUBJECT`. It never accepts database passwords or substitutes another principal. This is a diagnostic comparison, not a claim of certified workload-identity support. [Driver token authentication](https://python-oracledb.readthedocs.io/en/latest/user_guide/authentication_methods.html#oci-iam-token-based-authentication) · [OKE signer](https://docs.oracle.com/en-us/iaas/tools/python/latest/api/signing.html).

## Implementation

- Explicit OKE SDK identity → database-scoped proof-of-possession token → JDBC token supplier → TCPS EZConnect+.
- No password, API-key or node-principal fallback. SQL only reads session metadata from `DUAL`.
- Separate `native-resource-principal` mode tests the existing JDBC `OCI_RESOURCE_PRINCIPAL` setting; it does not automatically use our custom path.
- Versions: JDBC **23.26.3.0.0**, OCI SDK **3.97.2**, OCI JDBC provider **1.1.0**. [Sources](SOURCES.md) · [Article](blog.html) · [Operations](OPERATIONS.md).

## Build and configure

Requires Java 17+, Maven, Node.js, kubectl, an enhanced OKE cluster and IAM-enabled database. Cloud changes require approval.

```sh
cd workload-identity-oke
mvn -B -ntp verify
node --test scripts/*.test.cjs
cp config.env.example config.local.env
# Fill in non-secret target values and an immutable image digest.
set -a
. ./config.local.env
set +a
java -cp 'target/workload-identity-oke-0.1.0.jar:target/lib/*' demo.WorkloadIdentityDemo check
```

`check` is offline; it does not establish cloud access. Current automated tests: 18 Java, 15 Node.

## Database and policy prerequisites

- Review `iam-policy.example.txt`; scope access to the exact cluster, namespace, service account and database. Do not widen grants to troubleshoot login.
- The following experimental mapping already exists in financialdb, but has **not** produced a successful workload login. Oracle's service-specific and general mapping guidance conflicts; see [sources](SOURCES.md). Do not treat this as a validated deployment recipe. A new test requires approval, a verified workload subject and an unused user name:

```sql
CREATE USER OKE_JDBC_DEMO
  IDENTIFIED GLOBALLY AS 'IAM_PRINCIPAL_OCID=<verified-workload-ocid>';
GRANT CREATE SESSION TO OKE_JDBC_DEMO;
```

- Verify GLOBAL/OPEN, the exact mapped OCID and only `CREATE SESSION` without admin option; no direct roles/object grants. Do not overwrite existing users or change the identity provider.
- `WRITE_PRINCIPAL_EVIDENCE=true` in `token` mode writes only the workload subject to private pod termination metadata for mapping verification. Never commit it. Decoding a subject is not signature verification.

## Run the test

1. Set `DEMO_KUBECONFIG` to the approved cluster's private kubeconfig. After approval, provision `k8s/namespace.yaml` and the reviewed policy.
2. Build an image with `Dockerfile`, push to an approved registry, and set `DEMO_IMAGE` to its digest.
3. Generate, inspect and apply a Job:

   ```sh
   export DEMO_RUN_ID=test1
   node scripts/render-job.cjs identity > /tmp/jdbc-identity-job.json
   kubectl --kubeconfig "$DEMO_KUBECONFIG" apply -f /tmp/jdbc-identity-job.json
   kubectl --kubeconfig "$DEMO_KUBECONFIG" -n jdbc-workload-identity logs -f job/jdbc-allowed-identity-test1
   ```

4. Repeat for `token`, then `jdbc`, and separately `native-resource-principal`. Use a new run ID for reruns. Only a `jdbc` result with the expected session user establishes database login.
5. Negative control: set `DEMO_SERVICE_ACCOUNT=jdbc-denied` and run `token` against the same database. It must be denied alongside a passing allowed-account token test.
6. After the first JDBC success, test renewal with `TEST_ROUNDS=70 TEST_INTERVAL_SECONDS=60`; verify additional token acquisition and fresh physical connections across the token lifetime. Renewal is not yet tested.

**Source-build alternative:** `render-source-job.cjs` packages source in an immutable ConfigMap and builds inside the Job. Set `DEMO_IMAGE` to a trusted Maven/JDK image digest, not an application image. The tested digest is `docker.io/library/maven@sha256:f58d59b6273e785ac0a4477f6e9b5ba1d7731c75b906c0f7b34076f1851318cc`. Use it instead of `render-job.cjs` in step 3. This downloads public dependencies; prefer prebuilt images for deployment.

## Optional mTLS comparison

- Obtain explicit approval to export the sensitive certificate wallet to the exact test namespace.
- Create `jdbc-mtls-wallet` containing only `cwallet.sso`; do not overwrite an existing Secret or upload ADMIN credentials.
- Set `DEMO_USE_MTLS_WALLET=true` for JDBC modes. The mount is read-only at `/var/run/oracle-wallet`; keep the database URL in EZConnect+ format.
- Remove the temporary Secret after testing. This comparison was completed: token login still failed with `ORA-01017`. The Secret is removed; the original local wallet is retained.
