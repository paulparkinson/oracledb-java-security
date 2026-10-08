# Goal and blocker

**Goal: Java in OKE connects to financialdb using workload identity, without a database password. Not accomplished.**

## What works

- OKE authenticates the pod; OCI issues a financialdb-scoped database token.
- ADMIN/password JDBC works with the selected wallet and TCPS EZConnect+.
- `OKE_JDBC_DEMO` exists, is GLOBAL/OPEN, matches the token's workload subject, and has only `CREATE SESSION`.

## What fails

- JDBC with the workload token returns **ORA-01017 before any SQL session opens**, both with and without the wallet. Renewal is therefore untested.
- Independent Python comparison: Oracle driver **26.0.1**, OCI SDK **2.187.2**, same OKE service account, exact database scope and workload subject. Identity/token PASS; database login **ORA-01017** at **2026-10-08 21:28:02 UTC**. No password or wallet fallback. [Reproducer](scripts/reference-client.py).
- Separately, the unchanged `OCI_RESOURCE_PRINCIPAL` provider fails before token acquisition (`ORA-18726`, missing resource-principal environment). The explicit OKE SDK path gets further but still cannot log in.

## Why the exact cause is not established

- Database audit confirms the Python failure: return code 1017, no resolved database username, `ADDITIONAL_INFO` null. Existing cross-instance trace records contain no matching explanation in the test window. Cross-instance alert view access returns ORA-00942; local alert check found no relevant records.
- Matching the subject, checking expiry and seeing the requested scope in the token do **not** prove that the database accepts its contents or proof-of-possession signature.
- Two independent SDK/driver paths now fail, making a JDBC-only defect less likely. The shared token/scope/identity configuration and database validation/mapping/support remain unisolated; none is a confirmed root cause.

## Next decisive checks

- Request Oracle-assisted authentication diagnostics for the audited Python failure above and JDBC failure at **20:45:05 UTC**. Ask which token validation or principal-mapping check rejected the login, and whether this database supports the workload subject.
- No tracing or database settings were changed. A support request or trace enablement needs approval; another password test will not resolve this question.

JDBC versions: 23.26.3.0.0; OCI SDK 3.97.2; OCI provider 1.1.0. Python comparison Job and ConfigMap removed; earlier temporary wallet Secret removed; local wallet retained. No extra database grants or credential changes.
