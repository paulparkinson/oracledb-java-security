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

## Would GCP/GKE help?

- **Not a drop-in fix:** the OKE provider is platform-specific. [GKE federation](https://docs.cloud.google.com/kubernetes-engine/docs/concepts/workload-identity) does not itself supply the [OCI database token](https://docs.oracle.com/en-us/iaas/autonomous-database-serverless/doc/about-iam-authentication.html) our database expects. A secretless GKE design needs a supported trust/token-exchange/mapping path; none is validated here.
- The evidence concerns the **cloud-identity/database-authentication boundary**, not a demonstrated Kubernetes-engine defect. Either issuer or consumer could be responsible. Password/API-key login from GKE would change the goal, not solve workload authentication.

No second database, GKE deployment, permission expansion or external message was created.
