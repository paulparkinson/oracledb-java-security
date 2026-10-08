# OKE workload identity → Oracle Database JDBC evaluation

**Live status: OKE identity and financialdb-scoped token PASS; unauthorized service account rejected; workload JDBC login still fails with ORA-01017.** With approval, `OKE_JDBC_DEMO` was created and mapped to the verified workload OCID, with only `CREATE SESSION`. Read-back confirms the mapping and grants; the post-creation JDBC test still fails. See [STATUS.md](STATUS.md) for evidence and blockers, [OPERATIONS.md](OPERATIONS.md) for recovery/cleanup, and [blog.html](blog.html) for the article.

## Question being tested

Can a Java pod use OKE workload identity to obtain an OCI database proof-of-possession token and log in through JDBC without an application-managed long-lived secret? Does the existing JDBC `OCI_RESOURCE_PRINCIPAL` setting work unchanged?

These are separate questions. An OKE identity token is not itself a database token. A database token is not proof of an authorized database session. The test uses an explicit OKE SDK provider, never an automatic API-key/instance-principal/password fallback.

## Prerequisites and boundaries

- Existing **enhanced** OKE cluster, operator access, and an approved isolated namespace. No new cluster is provisioned by this project.
- Java 17+, Maven, Node.js for manifest generation, `kubectl`, OCI CLI, and a container builder/approved image registry.
- Reachable IAM-enabled Autonomous Database and a supported database mapping for the tested identity. **OKE workload identities currently cannot be members of dynamic groups; do not invent an `IAM_GROUP_NAME` mapping for them.** Oracle documents exclusive `IAM_PRINCIPAL_OCID` mappings; verify the actual workload subject and server configuration before creating a dedicated schema. This repository does not create that mapping automatically.
- Public-CA TLS connection compatible with the driver's trust store; use the database-provided TLS hostname/service. All samples use TCPS EZConnect+, never `tnsnames.ora` or disabled certificate verification.
- Administrative credentials used to provision IAM/resources are separate from runtime credentials. A registry pull credential, if required, is also separate; do not describe the entire deployment as credential-free.

Pinned test dependencies: JDBC **23.26.3.0.0**, OCI Java SDK **3.97.2**, JDBC OCI provider **1.1.0**. These are evaluation versions, not a minimum-version support matrix. The public provider source review is pinned in [SOURCES.md](SOURCES.md).

## Build and offline tests

```sh
cd workload-identity-oke
mvn -B -ntp verify
node --test scripts/*.test.cjs
```

Set non-secret inputs using `config.env.example`. `check` validates inputs only; it makes no cloud or database call:

```sh
cp config.env.example config.local.env
# Edit config.local.env to replace placeholders with approved non-secret values.
set -a
. ./config.local.env
set +a
java -cp 'target/workload-identity-oke-0.1.0.jar:target/lib/*' demo.WorkloadIdentityDemo check
```

Never mount `~/.oci`, passwords or static token files into the test pod. Do not use the local operator's API key as a substitute for pod identity.

## Live test procedure — approval required

1. Verify the exact tenancy, cluster and namespace. Save your isolated kubeconfig outside Git. Confirm the cluster is enhanced and its nodes are Ready. Do not upgrade or replace a shared cluster for this test.
2. Review the candidate `iam-policy.example.txt`, approve an exact scoped policy, and resolve the database-side workload mapping with the administrator. Policy syntax acceptance alone would not prove token issuance or JDBC authentication. If the mapping is unsupported, report that gap rather than using a node principal.
3. Build and push the test image to an approved registry, then set `DEMO_IMAGE` to its immutable digest. Example build command: `docker build --platform linux/amd64 -t REGISTRY/REPOSITORY:oke-eval .`. Use the architecture of the actual nodes. Obtain registry push/pull approval separately as needed.
4. After approval, create only the isolated namespace and two service accounts:

   ```sh
   kubectl --kubeconfig "$DEMO_KUBECONFIG" apply -f k8s/namespace.yaml
   ```

5. Generate and inspect the identity job, then apply it with the explicit kubeconfig:

   ```sh
   node scripts/render-job.cjs identity > /tmp/jdbc-identity-job.json
   kubectl --kubeconfig "$DEMO_KUBECONFIG" apply -f /tmp/jdbc-identity-job.json
   kubectl --kubeconfig "$DEMO_KUBECONFIG" -n jdbc-workload-identity logs -f job/jdbc-allowed-identity-manual
   ```

6. Repeat generation/apply for `token`, then `jdbc`, and finally `native-resource-principal`. The native mode deliberately uses the released JDBC setting as a comparison; it does not silently substitute the explicit OKE implementation. Use `DEMO_RUN_ID` for a new job name when rerunning, because Job pod templates are immutable.
7. For the negative control, set `DEMO_SERVICE_ACCOUNT=jdbc-denied` and run `token` with exactly the same image and target. Authentication as an OKE identity may still succeed; authorization to obtain/use a database token must not be reported as allowed. A negative failure is meaningful only after the matching positive control succeeds.
8. Only after an initial JDBC success, test renewal with `TEST_ROUNDS=70 TEST_INTERVAL_SECONDS=60`, using a new job name. Each round opens a fresh physical JDBC connection. Confirm more than one `acquisition` event and correlate it with the actual token lifetime; merely waiting or reusing an existing connection does not prove renewal. No refresh-token lifetime is assumed by the test.

## Interpreting evidence

| Stage | Evidence | Does not prove |
|---|---|---|
| `check` | Validated non-secret inputs | Any cloud access |
| `identity` | Explicit OKE provider obtained a signing identity | Database permission |
| `token` | Scoped identity-dataplane request returned a parseable database token | Database mapping or login |
| `jdbc` | Physical TCPS connection; expected session user; authentication metadata; database timestamp | Production support or renewal unless separately tested |
| `native-resource-principal` | Outcome of the existing JDBC provider path | That it used OKE unless its identity is independently correlated |

Keep sanitized job outcomes, pinned image digest/dependencies, timestamps, and operator-correlated audit evidence in `STATUS.md`. Raw tokens, proof-of-possession keys, full claims and raw debug logs must never enter Git. A successful source review/build must never be promoted to a successful cloud test.

## Dedicated database mapping

The approved financialdb change created exactly one dedicated global user. The application still does not provision users automatically. For a new deployment, first verify the target database and the workload subject from the allowed account's privately captured token. Obtain approval, check that the name is unused, and replace the placeholder below with that exact workload OCID (not a Kubernetes token or an OCI user OCID):

```sql
CREATE USER OKE_JDBC_DEMO
  IDENTIFIED GLOBALLY AS 'IAM_PRINCIPAL_OCID=<verified-workload-ocid>';
GRANT CREATE SESSION TO OKE_JDBC_DEMO;
```

Do not add `CONNECT`, `RESOURCE`, DBA roles, tablespace quotas or application-table grants for this session-metadata test. If the user already exists, inspect it; do not overwrite another mapping. Verify `DBA_USERS.AUTHENTICATION_TYPE = 'GLOBAL'`, `ACCOUNT_STATUS = 'OPEN'`, and `EXTERNAL_NAME` equals the verified mapping. Oracle stores the mapping prefix as lowercase `iam_principal_ocid=`; compare the prefix case-insensitively while comparing the OCID exactly.

Read back `DBA_SYS_PRIVS`: there must be exactly one direct system grant, `CREATE SESSION`, with `ADMIN_OPTION = 'NO'`. Confirm no direct grants in `DBA_ROLE_PRIVS`, `DBA_TAB_PRIVS` or `DBA_COL_PRIVS`. Preserve existing users and the existing `OCI_IAM` provider. These checks passed in financialdb; they do **not** establish that its server accepts this workload token. That still requires a successful `jdbc` run.

## Repository contents

- `src/`: explicit OKE → scoped database token → JDBC test and configuration tests.
- `k8s/`: isolated namespace and separate positive/negative service accounts; no cluster-wide RBAC grants.
- `scripts/render-job.cjs`: bounded, non-root Job generator; no cloud mutation by itself.
- `Dockerfile`: two-stage Java image, no secrets copied; runtime UID 10001.
- `STATUS.md`, `OPERATIONS.md`, `SOURCES.md`: durable evidence, incidents, decisions and references.

The source does not enable database IAM, create a database user, modify policies, or change network access automatically.

## Source-build evaluation option

The live run used `scripts/render-source-job.cjs` because Docker was unavailable locally. It produces an immutable, content-addressed ConfigMap containing only the POM and Java source/tests, plus a bounded Job. Set `DEMO_IMAGE` to a trusted **Maven/JDK image digest**, not the application image, then use the same approved kubeconfig and namespace:

```sh
export DEMO_IMAGE=docker.io/library/maven@sha256:f58d59b6273e785ac0a4477f6e9b5ba1d7731c75b906c0f7b34076f1851318cc
export DEMO_RUN_ID=eval1
node scripts/render-source-job.cjs identity | kubectl --kubeconfig "$DEMO_KUBECONFIG" apply -f -
```

This alternative downloads public Maven dependencies inside the test pod. It is an evaluation convenience, not the recommended production supply chain. Use the Dockerfile/prebuilt immutable application image for a controlled production-style pipeline. Source Jobs allow at least 15 minutes for the build, remain bounded, and do not need registry passwords. Source ConfigMaps are not removed by the Job TTL.

For administrator mapping preparation only, `WRITE_PRINCIPAL_EVIDENCE=true` is accepted in `token` mode. It writes just the validated-format workload subject OCID to the pod termination message, not the token or key. Read it through authorized Kubernetes access, retain it privately and never commit it. Decoding this field is diagnostic; it does not itself verify a JWT signature or prove database authorization. Default logs omit the subject. Error diagnostics report exception classes and specific missing resource-principal setting names without their values.
