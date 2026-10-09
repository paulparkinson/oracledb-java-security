# OKE workload identity → Oracle Database

**Goal:** Java in OKE connects to financialdb using the pod's workload identity—no database password, API key, or node-principal fallback.

**Status: goal not accomplished.** Workload identity and token issuance work with the original policy, but database login fails. The proposed `target.id` correction was tested; it did not fix login.

## What we tested

| Target / test | Result |
| --- | --- |
| **financialdb — Autonomous Serverless** | Workload login failed in JDBC, Python thin and native SQL*Plus, including JDBC with/without mTLS. A separate operator IAM-token login succeeded against the same endpoint. |
| **Isolated OCI Base Database PDB** | Workload Python login failed after correcting scope to the PDB, including a repeat after >10 minutes. Operator IAM-token login succeeded from OKE with the same driver/endpoint. Server alert identified `RESOURCE_AUTHORIZATION_ERROR` (error 10). |
| **financialdb — policy retest (October 9)** | `target.id` alone caused token issuance to fail with HTTP 404 `NotAuthorizedOrNotFound`, including after >10 minutes. Both exact-database statements together restored token issuance, but JDBC still returned `ORA-01017`, including after >10 minutes. The original policy was restored; the denied service account remained blocked. |

- Verified: workload subject, token scope/key binding, client TLS/PoP preparation, and a matching GLOBAL/OPEN `OKE_JDBC_DEMO` with only `CREATE SESSION`. These checks do **not** prove database authorization.
- The Base Database result identifies an **IAM resource-authorization rejection**, not its underlying reason. Financialdb's `ORA-01017` alone does not prove the same internal cause.
- The disposable Base Database, attached storage, dedicated network/policy and diagnostic resources were **deleted**. Financialdb remains available. Token renewal is untested; GKE is [notes only](../workload-identity-gke/README.md).

## Next steps and owners

- **OCI IAM database authorization + Database Security/server IAM integration:** identify the permission/conditions rejected by the Base Database server, and why the documented `target.id` blocks this workload's token issuance. Confirm the supported workload mapping and server prerequisites.
- **Autonomous engineering:** identify financialdb's rejecting server-side check. Its accessible audit only reports 1017, without a resolved user or detailed reason.
- **OKE Workload Identity engineering:** involve if the investigation shows missing/incorrect workload claims or policy context. There is no evidence yet of a general Kubernetes or JDBC-only defect.
- Resolve the documentation conflict: the [Autonomous guide](https://docs.oracle.com/en/cloud/paas/autonomous-database/serverless/adbsb/iam-create-groups-policies.html) requires dynamic-group mappings for resource principals, whereas [OKE excludes workload dynamic groups](https://docs.oracle.com/en-us/iaas/Content/ContEng/Tasks/contenggrantingworkloadaccesstoresources.htm) and the [database guide describes exclusive principal mappings](https://docs.oracle.com/en/database/oracle/oracle-database/26/dbseg/accessing-database-using-instance-principal-or-resource-principal.html). Neither a support statement nor a product defect is established by this test.

Success means a fresh `OKE_JDBC_DEMO / TOKEN_GLOBAL` session, a passing denied-account control, then renewal across fresh physical connections. No support request or external engineering message has been submitted.

## Build and configure

Requires Java 17+, Maven, Node.js, kubectl, an enhanced OKE cluster and an IAM-enabled Oracle database. Use the explicit private kubeconfig; preserve existing applications and database identity settings.

```sh
cd workload-identity-oke
mvn -B -ntp verify
node --test scripts/*.test.cjs
cp config.env.example config.local.env
# Set non-secret targets and an immutable image digest.
set -a
. ./config.local.env
set +a
java -cp 'target/workload-identity-oke-0.1.0.jar:target/lib/*' demo.WorkloadIdentityDemo check
```

`check` is offline. Versions: JDBC/SQL*Plus **23.26.3.0.0**, Java OCI SDK **3.97.2**, JDBC OCI provider **1.1.0**. Reference clients use Python driver **26.0.1** / OCI SDK **2.187.2**; Base Database was **23.26.3.0.0**, patch **39578879**.

### Database and IAM

1. Use TCPS **EZConnect+** with certificate verification and bounded timeouts, as in [config.env.example](config.env.example). Keep credentials, wallets, tokens, customer IDs and raw traces out of Git. No ADMIN credentials belong in pods.
2. Review [iam-policy.example.txt](iam-policy.example.txt): exact database, cluster, namespace and `jdbc-allowed` service account; exclude `jdbc-denied`. Do not broaden grants to force success.
3. For a **new approved test environment only**, verify the workload subject and create the experimental mapping below. It already exists in financialdb; do not recreate/overwrite it. This is not yet a validated deployment recipe.

```sql
CREATE USER OKE_JDBC_DEMO
  IDENTIFIED GLOBALLY AS 'IAM_PRINCIPAL_OCID=<verified-workload-ocid>';
GRANT CREATE SESSION TO OKE_JDBC_DEMO;
```

Confirm GLOBAL/OPEN, exact subject, no roles/object grants and no admin option. `WRITE_PRINCIPAL_EVIDENCE=true` in `token` mode writes the subject to private pod termination metadata; decoding a subject is not signature verification.

### Run the Java test

Set `DEMO_KUBECONFIG` to the approved cluster. For new environments only, review/provision [k8s/namespace.yaml](k8s/namespace.yaml). Build [Dockerfile](Dockerfile), push to an approved registry, and set `DEMO_IMAGE` to its digest.

```sh
export DEMO_RUN_ID=test1
node scripts/render-job.cjs jdbc > /tmp/oke-jdbc-job.json
# Inspect the manifest before applying.
kubectl --kubeconfig "$DEMO_KUBECONFIG" apply -f /tmp/oke-jdbc-job.json
kubectl --kubeconfig "$DEMO_KUBECONFIG" -n jdbc-workload-identity \
  logs -f job/jdbc-allowed-jdbc-test1
```

- Modes: `identity`, `token`, `jdbc`; use fresh run IDs. Only `jdbc` with the expected session identity proves login. SQL reads session metadata from `DUAL`; no application data changes.
- Negative control: repeat `jdbc` with `DEMO_SERVICE_ACCOUNT=jdbc-denied`. It must not connect; interpret that alongside a successful allowed-account login.
- Separate `native-resource-principal` mode uses JDBC's unchanged `OCI_RESOURCE_PRINCIPAL`. In this pod/provider version it fails with `ORA-18726` / missing `OCI_RESOURCE_PRINCIPAL_VERSION`; it does not automatically select the explicit OKE signer.
- After first login succeeds, set `TEST_ROUNDS=70 TEST_INTERVAL_SECONDS=60`. Verify new token acquisition and physical connections across expiry, not just survival of an existing session.
- **Build inside OKE:** substitute `render-source-job.cjs` for `render-job.cjs`. It creates an immutable source ConfigMap and builds in the Job. Set `DEMO_IMAGE` to the tested Maven/JDK image `docker.io/library/maven@sha256:f58d59b6273e785ac0a4477f6e9b5ba1d7731c75b906c0f7b34076f1851318cc`. This downloads public dependencies; prefer prebuilt images for deployment.
- Optional mTLS requires approval for a `jdbc-mtls-wallet` Secret containing only `cwallet.sso`, and `DEMO_USE_MTLS_WALLET=true`. The previous comparison also failed; its temporary Secret was removed. Keep EZConnect+ and certificate verification enabled.

### Minimal independent reproducer

Inside an approved Python 3.12 OKE pod using `jdbc-allowed`, set private `OCI_REGION`, `OCI_COMPARTMENT_ID`, `OCI_DATABASE_ID`, `EXPECTED_WORKLOAD_SUBJECT` and `DB_DSN=tcps://<host>:<port>/<service>`, then:

```sh
python3 -m pip install --no-cache-dir --no-compile --target /tmp/repro-deps \
  -r scripts/reference-requirements.txt
PYTHONPATH=/tmp/repro-deps python3 scripts/reference-client.py
```

This client requests its own workload database token and read-only login; it never substitutes an operator principal. [Python authentication reference](https://python-oracledb.readthedocs.io/en/latest/user_guide/authentication_methods.html#oci-iam-token-based-authentication).

### Reproduce the isolated Base Database comparison

This environment was deleted; recreating it incurs cloud charges. Use a disposable private EE PDB with SSH/SYSDBA, not financialdb. A local container is not an equivalent OCI IAM service; ADB Dedicated does not provide unrestricted SYS/server access.

1. Provision a private Base Database (tested: `VM.Standard.E5.Flex`, 1 OCPU, 256 GB, LVM, license included, backups off). Permit SSH 22 and TCPS 2484 only from diagnostic/OKE networks; no public IP or TCP 1521 ingress.
2. Enable `IDENTITY_PROVIDER_TYPE=OCI_IAM` in the PDB, leaving `IDENTITY_PROVIDER_CONFIG` unset. Map the workload as above and a separate operator control using `IAM_PRINCIPAL_NAME=<domain>/<user>`; grant only `CREATE SESSION`.
3. Use the **PDB OCID**, not CDB OCID, in token scope and policy. Tested candidate: `Allow any-user to use database-connections in compartment id <compartment-ocid> where all {request.principal.type = 'workload', request.principal.cluster_id = '<cluster-ocid>', request.principal.namespace = 'jdbc-workload-identity', request.principal.service_account = 'jdbc-allowed', target.database.id = '<pdb-ocid>'}`. This failed, including an alternative exact-principal-ID condition.
4. Configure server TLS under `WALLET_ROOT/<PDB-GUID>/tls`; mount only its public trust certificate at `/public-ca/server-cert.pem`. Python thin required native encryption/checksum `ACCEPTED` rather than `REQUIRED` on this isolated server; TCPS and certificate verification remained enabled. Do not apply that change blindly to shared servers.
5. With the Python prerequisites above and PDB inputs, run `scripts/base-database-reference-client.py workload`. Its separate `control` mode accepts private operator `{token, privateKey, subject}` JSON on stdin only; never upload an operator API key or report control success as workload success.
6. Correlate UTC failures with `UNIFIED_AUDIT_TRAIL` and the alert log located through `V$DIAG_INFO`. The useful authorization error was in the **alert log**, not the bounded Oracle Net trace. Keep raw diagnostics private; disable tracing afterward.

[Base Database IAM/TLS](https://docs.oracle.com/en/cloud/paas/base-database/iam/) · [IAM troubleshooting](https://docs.oracle.com/en/database/oracle/oracle-database/19/dbseg/troubleshooting-iam-connections.html) · [Server tracing](https://docs.oracle.com/en/database/oracle/oracle-database/26/netag/setting-tracing-parameters.html)

### Cleanup

Delete only exact test Jobs, unused test ConfigMaps and temporary Secrets. Preserve existing namespaces, service accounts, policies and mappings unless removal is explicitly intended. A recreated Base Database must be terminated **with storage**, followed by its dedicated policy/subnet/security list. The previous comparison's cleanup is complete.

Keep only sanitized outcomes, versions and correlation times. Never commit cloud inventory or raw authentication traces. This README is the single status/setup record; the [article](blog.html) is an explanatory overview.
