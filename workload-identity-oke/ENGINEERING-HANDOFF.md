# OKE workload token rejected by Oracle Database

## Request to engineering

- Identify the **server-side check returning ORA-01017**: token validation, principal-type handling, or schema resolution.
- Confirm support/prerequisites for `IAM_PRINCIPAL_OCID=<workload-subject>` on this Autonomous Serverless build. The failure is reproducible; the responsible component is not established.

## Evidence to forward

- **Fails:** OKE token login in Java, Python thin and native SQL*Plus; JDBC with and without mTLS.
- **Passes:** token issuance, subject/scope/workload checks, RSA key binding, client TLS/expiry/PoP signing. Server acceptance remains unproven.
- **Mapping:** `OKE_JDBC_DEMO`, GLOBAL/OPEN, exact workload subject, only `CREATE SESSION`; OCI_IAM enabled. No alternate-identity fallback.
- **Control passes:** operator IAM token → same database/endpoint → `TOKEN_DEMO / TOKEN_GLOBAL`. Control ran outside the pod, not in an identical execution environment.
- **2026-10-08 UTC:** Python failure 21:28:02; SQL*Plus failure 21:52:10; control success 21:54:58. Audit gives 1017, no resolved username/additional reason.
- **Versions:** JDBC/SQL*Plus 23.26.3.0.0; Java SDK 3.97.2; Python driver 26.0.1 / SDK 2.187.2. Capture the exact server patch build during correlation; control-plane `23ai` is insufficient. [Full findings](STATUS.md).

## Smallest runnable reproducer

- Use the [already-tested standalone Python client](scripts/reference-client.py) in a Python 3.12 OKE pod: namespace `jdbc-workload-identity`, service account `jdbc-allowed`, mounted service-account token, 2 GiB writable dependency storage.
- Retain the existing [IAM policy](iam-policy.example.txt)/database mapping. Export private deployment values: `OCI_REGION`, `OCI_COMPARTMENT_ID`, `OCI_DATABASE_ID`, `EXPECTED_WORKLOAD_SUBJECT`, `DB_DSN=tcps://<host>:1521/<service>`; use the wallet-free TLS endpoint.

```sh
# Inside the prepared OKE pod; working directory: workload-identity-oke
python3 -m pip install --no-cache-dir --no-compile --target /tmp/repro-deps \
  -r scripts/reference-requirements.txt
PYTHONPATH=/tmp/repro-deps python3 scripts/reference-client.py
```

- Observed: `oke-identity PASS` → `database-token PASS` → `python-login FAIL`, **1017**, nonzero exit. Success requires `OKE_JDBC_DEMO` with token authentication and a read-only `DUAL` query.
- [Java source](src/main/java/demo/WorkloadIdentityDemo.java) / [pod recipe](README.md#run-the-test): explicit `jdbc` mode. No passwords, API keys, node-principal fallback or application DML. Keep tokens/keys/raw traces private; clean up temporary pods.

## Best owners

- **Lead: Database Security / server IAM authentication + Autonomous engineering:** identify the rejecting check.
- **Partner: OCI IAM database-token engineering:** verify workload claims against the database's expectations.
- **Consult OKE Workload Identity** if claim generation is implicated. General Kubernetes operations/JDBC-only investigation are lower priority. This is triage, not fault assignment.

## Questions that need answers

- Are different claims, mapping syntax or server patches required? Which verifier/mapping branch returns 1017?
- Reconcile the contradictory [Oracle mapping guidance and A-Team example](SOURCES.md). Do not infer unsupported functionality solely from that contradiction.

## Can we identify the rejection ourselves?

- **Current Serverless database:** accessible audit/diagnostic evidence has not exposed the rejecting check. Client trace confirms client preparation, not server acceptance.
- **Self-managed server:** OS/SYSDBA access permits controlled server-side Oracle Net tracing and ADR inspection. That could narrow the failure, but does not guarantee the internal verifier reason is exposed. See [server tracing](https://docs.oracle.com/en/database/oracle/oracle-database/26/netag/setting-tracing-parameters.html).
- **Local Docker/Podman database:** do not assume it reproduces this integration. Oracle's [OCI IAM integration environments](https://docs.oracle.com/en/database/oracle/oracle-database/26/dbseg/introduction-authenticating-and-authorizing-iam-users-oracle-dbaas.html) list OCI database services, not arbitrary local database containers. A multitenant CDB is not the same thing as an OCI IAM-enabled cloud service.
- **ADB Dedicated:** supports OCI IAM, but [ADMIN remains restricted compared with SYS](https://docs.oracle.com/en/cloud/paas/autonomous-database/dedicated/adbdk/index.html). A new Dedicated instance is not a reliable way to obtain unrestricted server tracing.
- **Best candidate if deeper self-service tracing is needed:** an isolated OCI Base Database Service PDB, which has documented IAM integration and [SSH/SYSDBA access](https://docs.oracle.com/en/cloud/paas/base-database/connect-bequeath/index.html). First verify version/patch compatibility; use newly scoped tokens and narrowly scoped policy/mapping for that target. Trace one failing workload login and one successful control, then restore tracing and retain only sanitized findings. This is a proposed diagnostic experiment, not a fix or an executed test.

The owner subsequently approved an [isolated Base Database diagnostic](BASE-DATABASE-DIAGNOSTIC.md); provisioning has started. That separate experiment does not alter the financialdb findings above. No external engineering message or support request has been submitted.
