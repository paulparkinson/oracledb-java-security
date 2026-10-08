# Goal and blocker

**Goal: Java in OKE connects to financialdb using workload identity, without a database password. Not accomplished.**

## What works

- OKE authenticates the pod; OCI issues a financialdb-scoped database token.
- ADMIN/password JDBC works with the selected wallet and TCPS EZConnect+.
- `OKE_JDBC_DEMO` exists, is GLOBAL/OPEN, matches the token's workload subject, and has only `CREATE SESSION`.

## What fails

- JDBC with the workload token returns **ORA-01017 before any SQL session opens**, both with and without the wallet. Renewal is therefore untested.
- Separately, the unchanged `OCI_RESOURCE_PRINCIPAL` provider fails before token acquisition (`ORA-18726`, missing resource-principal environment). The explicit OKE SDK path gets further but still cannot log in.

## Why the exact cause is not established

- The error and inspected audit record do not identify which authentication check failed.
- Matching the subject, checking expiry and seeing the requested scope in the token do **not** prove that the database accepts its contents or proof-of-possession signature.
- We have not isolated a token/request defect, incorrect JDBC presentation, or database-side principal mapping/authorization/support issue. None is a confirmed root cause.
- We do not yet have a detailed database authentication diagnostic or a successful reference-client test using this workload identity.

## Next decisive checks

- Obtain the database-side rejection reason for the failed token login, with Oracle assistance if needed.
- Compare with a supported reference client using the same workload identity, database scope and mapping. Do not substitute ADMIN credentials.

Latest evidence: **2026-10-08 20:45:05 UTC**, Job `jdbc-allowed-jdbc-mtls1`, code `9da42b1`: identity/token PASS, JDBC ORA-01017. JDBC 23.26.3.0.0; OCI SDK 3.97.2; OCI provider 1.1.0. Temporary wallet Secret removed; local wallet retained. No fallback credentials or extra database grants.
