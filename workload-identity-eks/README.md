# EKS workload identity — investigation notes

**Goal:** Java in EKS connects to Oracle Database using workload identity, without stored database passwords or long-lived cloud keys.

**Result: blocked before pod deployment. Oracle workload login was not tested. No AWS resources, database users, grants or identity settings were created/changed.**

## Live checks — 2026-10-09 UTC

- AWS SSO/STS authentication succeeded. EKS `ListClusters` returned an empty list in `us-east-1`.
- IAM simulation for both assigned roles (`Multicloud-Engineering-Bedrock` and `Field-Engineering-Standard`) returned `implicitDeny` for `eks:CreateCluster` and `iam:CreateRole`, including the region context. No cluster creation was attempted; this is a provisioning prerequisite failure, **not ORA-01017**.
- Read-only checks against the existing Oracle database in AWS returned `identity_provider_type=NONE`, no identity-provider configuration, and session authentication `PASSWORD`. That verifies database reachability with the existing login only—not pod authentication.
- **Needed from the AWS account administrator:** an approved existing EKS test cluster with access, or a scoped provisioning role for the cluster, worker/runtime resources and associated IAM roles. User consent does not override AWS account permissions.
- **Needed from Oracle Database Security/IAM integration owners:** confirm the supported external-workload trust and SQL-token path for this Oracle Database@AWS deployment before enabling it. AWS STS credentials are not Oracle SQL credentials.

## Candidate to evaluate after access is available

OCI now documents [external JWT → RPST workload federation](https://docs.oracle.com/en-us/iaas/Content/Identity/api-getstarted/token_exchange_grant_type_workload_id-federation.htm), including Kubernetes tokens. This is a candidate, **not a tested database-login solution**. Its documented exchange requires a trusted issuer and authenticated exchange caller; the OAuth-client example introduces a client secret. Do not label that example fully secretless or assume an OCI control-plane RPST is already a database token. Establish the bootstrap identity, database-token issuance, schema mapping and renewal separately.

## Resume after restart

- No cloud cleanup is pending. Recheck `git status` before edits and renew AWS SSO if expired.
- The sibling Bedrock repository's `RESUME.md` records the local CLI/profile paths. Temporary tooling can be recreated; credentials are only in private SDK caches/configuration, never here.
- After provisioning access is granted: inspect the exact target, create isolated test resources, then verify pod identity, Oracle token issuance, actual SQL session identity, denied-account behavior and renewal. Do not substitute a password, node role or operator credential for the pod identity.

## What is supported versus unproved

- **EKS supports workload identity:** EKS Pod Identity supplies temporary AWS role credentials through its agent and the AWS SDK credential chain. IRSA is a separate option using an OIDC service-account token and AWS STS. Neither is OKE Proxymux or an OCI RPST. [Pod Identity flow](https://docs.aws.amazon.com/eks/latest/userguide/pod-id-how-it-works.html) · [Supported SDKs](https://docs.aws.amazon.com/eks/latest/userguide/pod-id-minimum-sdk.html).
- **AWS-native database login:** IAM database authentication supports RDS PostgreSQL, MySQL and MariaDB, and Aurora PostgreSQL/MySQL, subject to engine/version/region availability. An authorized workload role signs a short-lived database authentication token; the database must also have the appropriate IAM-authenticated user. This is not RDS for Oracle authentication. [RDS](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/UsingWithRDS.IAMDBAuth.html) · [Aurora](https://docs.aws.amazon.com/AmazonRDS/latest/AuroraUserGuide/UsingWithRDS.IAMDBAuth.html) · [AWS EKS database example using IRSA](https://aws.amazon.com/blogs/containers/using-iam-database-authentication-with-workloads-running-on-amazon-eks/).
- **EKS → our Oracle database:** unproved. AWS credentials do not automatically become OCI database tokens, and the OKE-specific provider is not an EKS provider. A supported trust/token-exchange path, compatible Java provider and Oracle database mapping must be established first. The [OKE failures](../workload-identity-oke/README.md#what-we-tested) do not prove EKS will fail—or that changing Kubernetes vendors will fix them.

## Next investigation

1. Ask Oracle Database Security/IAM integration owners which external-workload identity path and database builds support this use case; involve AWS identity specialists for the AWS side of that trust.
2. If a supported path exists, use a dedicated service account/role and least-privilege database user. Verify token acquisition, actual SQL session identity, denied-account behavior and fresh connections across token expiry separately.
3. Keep TLS certificate verification enabled and use EZConnect+ for Oracle. No node-role, operator-key or stored-password fallback; no credentials or raw tokens in Git.

An RDS/Aurora comparison would test AWS-native passwordless login, **not** resolve or validate Oracle workload login. The authorized Oracle experiment remains blocked on the prerequisites above; no RDS/Aurora comparison was provisioned.

[GKE notes](../workload-identity-gke/README.md) · [OKE results and setup](../workload-identity-oke/README.md)
