# Isolated Base Database diagnostic

**Purpose:** identify the server-side rejection of an OKE workload token, not migrate financialdb or substitute another identity.

## Setup approved and started — October 8, 2026

- OCI Base Database Service, Frankfurt, single-node `VM.Standard.E5.Flex`, 1 OCPU, 256 GB requested data storage, license included, LVM. Requested database release: `26.0.0.0.0`; installed patch must be read from the server after provisioning.
- A separate private subnet and security list in the existing OKE VCN. No public IP or Internet-facing database listener. SSH and TCPS ingress restricted to the diagnostic subnet and existing OKE worker/pod subnets. Existing route table reused without modification.
- New diagnostic SSH key and generated administrator password stay in private local state. No financialdb wallet or ADMIN password is uploaded. Automatic backups are disabled for this disposable database.
- A temporary, bounded Kubernetes SSH-helper Job mounts only the new database's key. It does not mount a service-account token. Workload-authentication tests use a separate Job with the existing permitted service account.
- No financialdb changes. No GKE resources. Provisioned compute/storage incur charges until terminated; stopping compute alone is not complete cleanup.

## Test sequence

1. Wait for the database to become available. Read exact database version, patch inventory, `WALLET_ROOT`, PDB service and identity-provider parameters over SSH/SYSDBA.
2. Configure trusted one-way TLS on this test server. Use a private test CA/certificate and distribute only the public trust certificate to clients. Preserve certificate-name verification and TCPS EZConnect+; do not use a plaintext listener for token authentication.
3. Enable OCI IAM only in the diagnostic PDB. Create a dedicated global schema with `CREATE SESSION` only and the already-verified OKE workload principal OCID. This mapping is experimental, not a claim of supported OKE login.
4. Add a separate IAM policy allowing only the existing cluster/namespace/service account to use `database-connections`, with `target.database.id` restricted to this new database. Do not extend the financialdb policy.
5. Request a fresh token scoped to the new database from the OKE workload identity. Attempt a read-only identity query. Separately test an operator IAM-token control; never substitute that token on workload failure.
6. Use documented, bounded server-side Oracle Net tracing and ADR to correlate the failed/successful attempts. Inspect locally; retain only sanitized findings. A trace that repeats ORA-01017 without an internal reason does not establish the cause.
7. Restore tracing, remove temporary Jobs/Secrets/policies, terminate the diagnostic DB system, and remove only its dedicated subnet/security list. Record the actual cleanup state below.

## Result

- Provisioning accepted; live server configuration and authentication results pending.
- The original financialdb workload login remains unresolved. This experiment does not yet change that result.

## Reproduction and recovery

- Private state records the exact resource IDs, connection details, key and generated password; none belong in Git. The operator must preserve that state until cleanup completes.
- A new reproduction requires explicit approval for billable resources and IAM/database mutations. Use a unique resource name and dedicated subnet, and save each returned resource ID before proceeding.
- After an interrupted run, inspect recorded resource lifecycle state before retrying creation. Never delete resources by a broad tag/name match. Cleanup must use the exact IDs from this experiment and verify they still identify the diagnostic resources.

[Base Database IAM/TLS configuration](https://docs.oracle.com/en/cloud/paas/base-database/iam/) · [Create a DB system](https://docs.oracle.com/en/cloud/paas/base-database/create-dbs-new/index.html) · [Server-side tracing](https://docs.oracle.com/en/database/oracle/oracle-database/26/netag/setting-tracing-parameters.html)
