# Oracle Database Java security

Oracle Database Java security examples, articles and experiments, with explicit evidence and limitations.

- [Deep Data Security and JDBC security](deepdatasecurity/README.md): source, articles and assets moved from `oracle-ai-for-sustainable-dev/security/`; API and provider examples retain their original [UPL license](deepdatasecurity/LICENSE).

- [OKE workload identity](workload-identity-oke/README.md): distinguish pod authentication, OCI database-token issuance, and JDBC login.
- [GKE workload identity notes](workload-identity-gke/README.md): scope and open questions only; no implementation yet.
- [EKS workload identity notes](workload-identity-eks/README.md): AWS-native database support versus the unproved Oracle path; no implementation yet.
- [Passwordless Java: OCI IAM and Entra](java-jdbc-token-authentication-oci-iam-entra.html): full instance- and resource-principal examples; [runnable source](https://github.com/paulparkinson/oracle-db-examples/tree/main/java/jdbc-token-auth).
- [Article](workload-identity-oke/blog.html)

GitHub Pages is not configured. Open the [token-authentication HTML source](https://github.com/paulparkinson/oracledb-java-security/blob/main/java-jdbc-token-authentication-oci-iam-entra.html), or download it with its assets and open it locally. Repository changes do not publish a Pages site or change cloud deployments. Local `.env` files, wallets, build output and video working files are not part of the source distribution.

Examples are evaluations, not a statement of Oracle product support. Never commit credentials, Kubernetes tokens, database tokens, wallets, private kubeconfigs, or raw cloud inventory. Consult each experiment's status before running it.
