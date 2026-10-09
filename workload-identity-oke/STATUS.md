# Goal and blocker

**Goal: Java in OKE connects to financialdb using workload identity, without a database password. Not accomplished.**

## Verified on 2026-10-08

- **Workload identity and token issuance pass.** Database scope, subject, cluster, namespace and service account match the intended configuration. The token's RSA public key matches its proof-of-possession private key.
- **Three workload-token clients fail with ORA-01017:** Java JDBC, independent Python thin and native SQL*Plus 23.26.3.0.0. JDBC fails with and without mTLS. No application SQL executes; renewal remains untested.
- **Native client trace passes** TCPS, certificate-name checking, expiry, private-key loading and PoP header/signature creation. This verifies client preparation, not server acceptance of the signature. Native failure: **21:52:10 UTC**.
- **Separate operator IAM-token control passes against the same financialdb endpoint:** `SESSION_USER=TOKEN_DEMO`, `AUTHENTICATION_METHOD=TOKEN_GLOBAL`, wallet-free TCPS EZConnect+, **21:54:58 UTC**. This is a diagnostic control, never a workload fallback.
- `OKE_JDBC_DEMO` is GLOBAL/OPEN, has only `CREATE SESSION`, and is the sole exact mapping for the workload subject. The existing IAM policy is scoped to the intended workload and database. No mappings, grants or IAM settings were changed during these checks.

## What this isolates

- General database IAM-token authentication and the tested endpoint work. The unresolved failure is workload-specific; it is not reproduced by the operator token. Token issuance does not prove database-session authorization.
- Financialdb audit provides only 1017, no resolved username and no additional reason. **A new isolated Base Database comparison narrows its own failure to server OCI IAM resource authorization** (`RESOURCE_AUTHORIZATION_ERROR`, error 10). Its operator control passes from OKE; workload login fails even after correcting the target from CDB to PDB. This does not prove financialdb has the same internal cause. [Versions, times, policy variants and reproduction](BASE-DATABASE-DIAGNOSTIC.md).
- **Oracle's guidance conflicts:** the [Autonomous guide](https://docs.oracle.com/en-us/iaas/autonomous-database-serverless/doc/iam-create-groups-policies.html) requires dynamic-group mappings for resource principals; [OKE documentation](https://docs.oracle.com/en-us/iaas/Content/ContEng/Tasks/contenggrantingworkloadaccesstoresources.htm) excludes workloads from dynamic groups. However, the [database guide](https://docs.oracle.com/en/database/oracle/oracle-database/26/dbseg/accessing-database-using-instance-principal-or-resource-principal.html) allows exclusive resource-principal mappings, and the [A-Team example](https://www.ateam-oracle.com/connecting-oracle-kubernetes-engine-oke-namespaces-to-autonomous-database-with-oci-iamconnecting-oracle-kubernetes-engine-oke-namespaces-to-autonomous-database-with-oci-iam) describes this exact workload-OCID approach. This is a compatibility question, **not proof of either support or non-support on financialdb**.

## Current next step

- The [isolated Base Database diagnostic](BASE-DATABASE-DIAGNOSTIC.md) ran October 8. It identified an authorization-stage rejection, not a fix. GKE remains [notes only](../workload-identity-gke/README.md).
- Use the [engineering handoff and reproducer](ENGINEERING-HANDOFF.md). Lead with OCI IAM database authorization and Database Security/server IAM integration; establish the evaluated permission/context and why the exact workload/PDB request is rejected.
- **Concrete financialdb follow-up:** read-only inspection found its existing dedicated policy uses `target.database.id`, while the [Autonomous service guide](https://docs.oracle.com/en/cloud/paas/autonomous-database/serverless/adbsb/iam-create-groups-policies.html) demonstrates `target.id`. Validate a separately approved, otherwise-identical exact-database policy with the documented variable before concluding a service defect. That variant has not been tested on financialdb; this isolated experiment did not change its policy.

- A fix requires identifying the database's workload-specific rejection or a verified mapping/configuration applicable to this deployment. No safe configuration-only fix has been established. Repeating password tests, broadening policy or silently switching principals does not resolve the goal.
- No support request is planned. The current approval covers the isolated diagnostic, not changes to shared financialdb settings.

Earlier financialdb Python/native Jobs and source ConfigMaps were removed; native token/key/client traces were pod-local and are gone. No database-side tracing was enabled on financialdb. The earlier temporary wallet Secret is removed; the original local wallet is retained. Separate Base Database server tracing and resource cleanup are recorded in its [diagnostic report](BASE-DATABASE-DIAGNOSTIC.md#cleanup).
