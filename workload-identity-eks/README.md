# EKS workload identity — investigation notes

**Goal:** Java in EKS connects to Oracle Database using workload identity, without stored database passwords or long-lived cloud keys.

**Status: notes only; not implemented or tested. No AWS resources have been created for this experiment.**

## What is supported versus unproved

- **EKS supports workload identity:** EKS Pod Identity supplies temporary AWS role credentials through its agent and the AWS SDK credential chain. IRSA is a separate option using an OIDC service-account token and AWS STS. Neither is OKE Proxymux or an OCI RPST. [Pod Identity flow](https://docs.aws.amazon.com/eks/latest/userguide/pod-id-how-it-works.html) · [Supported SDKs](https://docs.aws.amazon.com/eks/latest/userguide/pod-id-minimum-sdk.html).
- **AWS-native database login:** IAM database authentication supports RDS PostgreSQL, MySQL and MariaDB, and Aurora PostgreSQL/MySQL, subject to engine/version/region availability. An authorized workload role signs a short-lived database authentication token; the database must also have the appropriate IAM-authenticated user. This is not RDS for Oracle authentication. [RDS](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/UsingWithRDS.IAMDBAuth.html) · [Aurora](https://docs.aws.amazon.com/AmazonRDS/latest/AuroraUserGuide/UsingWithRDS.IAMDBAuth.html) · [AWS EKS database example using IRSA](https://aws.amazon.com/blogs/containers/using-iam-database-authentication-with-workloads-running-on-amazon-eks/).
- **EKS → our Oracle database:** unproved. AWS credentials do not automatically become OCI database tokens, and the OKE-specific provider is not an EKS provider. A supported trust/token-exchange path, compatible Java provider and Oracle database mapping must be established first. The [OKE failures](../workload-identity-oke/README.md#what-we-tested) do not prove EKS will fail—or that changing Kubernetes vendors will fix them.

## Next investigation

1. Ask Oracle Database Security/IAM integration owners which external-workload identity path and database builds support this use case; involve AWS identity specialists for the AWS side of that trust.
2. If a supported path exists, use a dedicated service account/role and least-privilege database user. Verify token acquisition, actual SQL session identity, denied-account behavior and fresh connections across token expiry separately.
3. Keep TLS certificate verification enabled and use EZConnect+ for Oracle. No node-role, operator-key or stored-password fallback; no credentials or raw tokens in Git.

An RDS/Aurora comparison would test AWS-native passwordless login, **not** resolve or validate Oracle workload login. No deployment is planned until that scope is explicitly selected.

[GKE notes](../workload-identity-gke/README.md) · [OKE results and setup](../workload-identity-oke/README.md)
