# Isolated Base Database diagnostic

**Result: workload login still fails. The server identifies the failing stage as OCI IAM resource authorization.** Its underlying reason remains unresolved; this does not prove financialdb fails at the same internal check.

## Observed October 8, 2026 (UTC)

| Check | Result |
| --- | --- |
| Private Base Database | Oracle AI Database EE `23.26.3.0.0` (26ai), SQL patch `39578879` |
| OKE identity and new database-scoped token | Pass; subject matches workload mapping |
| Workload login with corrected PDB scope | ORA-01017 at 23:46:06, 23:47:15 and 23:55:45 (final repeat >10 minutes after correction) |
| Operator control, same PDB/OKE runtime/driver/TLS | Pass at 23:48:13; `IAM_CONTROL_DEMO / TOKEN_GLOBAL` |
| Correlated server alert | Final 23:55:44.703469: `DB OCI IAM: Authorization check failed. Bad response, error = 10`; `response_error RESOURCE_AUTHORIZATION_ERROR` |
| Server audit | Failed LOGON, 1017, no resolved database username |

- Python driver `26.0.1`, OCI SDK `2.187.2`, Python 3.12. Both clients use certificate-verified TCPS EZConnect+. Tokens/PoP keys remain private; no fallback.
- Server authorization targets the **PDB OCID**, not its parent CDB. The initial CDB-scoped test was corrected. Subsequent token scope, policy target and server-reported PDB/compartment IDs match, but authorization still fails.
- The policy tests exact cluster/namespace/service-account conditions and an alternative exact workload-principal OCID condition, each restricted to this PDB. No compartment-wide database grant.
- Successful operator mapping: `IAM_PRINCIPAL_NAME=<domain>/<user>`; workload mapping: `IAM_PRINCIPAL_OCID=<verified-workload-subject>`. Both schemas are GLOBAL/OPEN, with only `CREATE SESSION`. The control does not prove workload support or identical authorization rules.
- This is a Base Database **Python comparison**, not a new JDBC success. Earlier financialdb Java/Python/native failures are separate evidence. Renewal is untested.

## Remaining question and owners

- **Lead: OCI IAM database authorization engineering with Database Security/server IAM integration.** Which permission and condition context does the server submit, and why is this exact workload/PDB request rejected?
- Token issuance is not permission to open a database session. Policy-variable support, workload context propagation and service/build compatibility remain candidates.
- This does not establish bad signatures, missing schemas, unsupported OKE identity or an OKE defect. Financialdb's identical client error alone does not establish the same internal cause.
- No financialdb changes, support request or external engineering message.

## Reproduction

1. Provision a disposable private Base Database with SSH/SYSDBA. This run: `VM.Standard.E5.Flex`, 1 OCPU, 256 GB data, LVM, license included, automatic backups off. Save exact resource IDs privately; compute/storage are billable.
2. Dedicated subnet/security list: SSH 22 and TCPS 2484 only from OKE worker/pod networks and diagnostic subnet. No public IP or TCP 1521 ingress. Existing VCN/routes unchanged.
3. Enable `IDENTITY_PROVIDER_TYPE=OCI_IAM` in the new PDB; leave `IDENTITY_PROVIDER_CONFIG` unset. Create the two dedicated schemas above. Discover the PDB OCID through OCI; do not assume its parent's ID is the authorization target.
4. Server TLS wallet: `WALLET_ROOT/<PDB-GUID>/tls`. Mount only its public trust certificate at `/public-ca/server-cert.pem` in clients. Keep hostname verification enabled. The isolated server's native encryption/checksum settings were changed from `REQUIRED` to `ACCEPTED` so Python thin could reach token login; TCPS remained required by the client/network. Do not apply this blindly to shared servers.
5. Add a separate narrowly scoped policy. This is the **tested candidate, not a working configuration**:

   ```text
   Allow any-user to use database-connections in compartment id <compartment-ocid> where all {request.principal.type = 'workload', request.principal.cluster_id = '<cluster-ocid>', request.principal.namespace = 'jdbc-workload-identity', request.principal.service_account = 'jdbc-allowed', target.database.id = '<pdb-ocid>'}
   ```

6. In a temporary OKE pod using `jdbc-allowed`, install [reference-requirements.txt](scripts/reference-requirements.txt). Export `OCI_REGION`, `OCI_COMPARTMENT_ID`, `OCI_DATABASE_ID=<pdb-ocid>`, `EXPECTED_WORKLOAD_SUBJECT` and `DB_DSN=tcps://<certificate-host>:2484/<pdb-service>`. Run `python3 scripts/base-database-reference-client.py workload`. Success requires `OKE_JDBC_DEMO / TOKEN_GLOBAL` and a read-only DUAL query.
7. Separately, the same script's `control` mode accepts privately minted operator `{token, privateKey, subject}` JSON **only on stdin**, in a control pod without a mounted service-account token. Never upload the operator API key or call this workload success.
8. Correlate UTC times with PDB `UNIFIED_AUDIT_TRAIL` and the alert log found through `V$DIAG_INFO`. Bounded Oracle Net server tracing was also enabled (`SUPPORT`, 2 × 2048 KB files per process); the useful authorization reason came from the **alert log**, not packet dumps. Disable tracing promptly; never commit raw traces.
9. Delete only the saved diagnostic Jobs/ConfigMaps/SSH Secret and policy; terminate the DB system and attached storage; then remove its dedicated subnet/security list. Preserve shared resources.

## Cleanup

- Original server network configuration restored; diagnostic tracing disabled.
- All temporary test Jobs, source/trust ConfigMaps and SSH Secret removed; namespace inventory confirms none remain. Dedicated IAM policy deleted.
- **Completed October 9, approximately 00:05 UTC:** OCI confirms the diagnostic DB system `TERMINATED`; its dedicated subnet and security list are deleted. Termination removes the disposable database and attached storage, not just compute power. No test backups were configured.
- Temporary diagnostic credentials and raw output files removed. A credential-free exact-ID cleanup record remains private and Git-ignored. Existing financialdb, cluster, VCN and routes are unchanged.

[Base Database IAM/TLS](https://docs.oracle.com/en/cloud/paas/base-database/iam/) · [Create a DB system](https://docs.oracle.com/en/cloud/paas/base-database/create-dbs-new/index.html) · [Server tracing](https://docs.oracle.com/en/database/oracle/oracle-database/26/netag/setting-tracing-parameters.html)
