# Goal and blocker

**Goal: Java in OKE connects to financialdb using workload identity, without a database password. Not accomplished.**

## Verified on 2026-10-08

- **Workload identity and token issuance pass.** Database scope, subject, cluster, namespace and service account match the intended configuration. The token's RSA public key matches its proof-of-possession private key.
- **Three workload-token clients fail with ORA-01017:** Java JDBC, independent Python thin and native SQL*Plus 23.26.3.0.0. JDBC fails with and without mTLS. No application SQL executes; renewal remains untested.
- **Native client trace passes** TCPS, certificate-name checking, expiry, private-key loading and PoP header/signature creation. This verifies client preparation, not server acceptance of the signature. Native failure: **21:52:10 UTC**.
- **Separate operator IAM-token control passes against the same financialdb endpoint:** `SESSION_USER=TOKEN_DEMO`, `AUTHENTICATION_METHOD=TOKEN_GLOBAL`, wallet-free TCPS EZConnect+, **21:54:58 UTC**. This is a diagnostic control, never a workload fallback.
- `OKE_JDBC_DEMO` is GLOBAL/OPEN, has only `CREATE SESSION`, and is the sole exact mapping for the workload subject. The existing IAM policy is scoped to the intended workload and database. No mappings, grants or IAM settings were changed during these checks.

## What this isolates

- General database IAM-token authentication and the tested endpoint work. The unresolved failure is specific to workload-token validation or workload-principal resolution; it is not reproduced by the operator token.
- Existing database audit provides only 1017, no resolved username and no additional reason. Available server traces did not identify the rejected check. Successful client signing does not prove that the server accepts the workload identity.
- **Oracle's guidance conflicts:** the [Autonomous guide](https://docs.oracle.com/en-us/iaas/autonomous-database-serverless/doc/iam-create-groups-policies.html) requires dynamic-group mappings for resource principals; [OKE documentation](https://docs.oracle.com/en-us/iaas/Content/ContEng/Tasks/contenggrantingworkloadaccesstoresources.htm) excludes workloads from dynamic groups. However, the [database guide](https://docs.oracle.com/en/database/oracle/oracle-database/26/dbseg/accessing-database-using-instance-principal-or-resource-principal.html) allows exclusive resource-principal mappings, and the [A-Team example](https://www.ateam-oracle.com/connecting-oracle-kubernetes-engine-oke-namespaces-to-autonomous-database-with-oci-iamconnecting-oracle-kubernetes-engine-oke-namespaces-to-autonomous-database-with-oci-iam) describes this exact workload-OCID approach. This is a compatibility question, **not proof of either support or non-support on financialdb**.

## Remaining decision

- Use the [succinct engineering handoff and existing reproducer](ENGINEERING-HANDOFF.md). Lead with database-server IAM authentication and OCI IAM token engineering; no second-database comparison or GKE migration is planned.

- A fix requires identifying the database's workload-specific rejection or a verified mapping/configuration applicable to this deployment. No safe configuration-only fix has been established. Repeating password tests, broadening policy or silently switching principals does not resolve the goal.
- No support request is planned. Further tests that change IAM, mappings, database settings or deployment targets require approval.

Temporary Python/native Jobs and source ConfigMaps are removed; native token, key and raw client traces were pod-local and are gone. No database-side tracing was enabled. Earlier temporary wallet Secret is removed; the original local wallet is retained.
