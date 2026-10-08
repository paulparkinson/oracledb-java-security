# GKE workload identity — investigation notes

**Status: not implemented or tested. No GKE resources have been created for this experiment.**

- GKE Workload Identity Federation gives a Kubernetes workload Google Cloud identities/credentials. It does not automatically issue an OCI IAM database token or make JDBC's `OCI_RESOURCE_PRINCIPAL` consume a GKE token.
- An Oracle database connection would need a supported identity/federation path, a database-scoped token, any required proof-of-possession key, and a matching database schema. We have not established that path for GKE.
- The current [OKE investigation](../workload-identity-oke/ENGINEERING-HANDOFF.md) reaches OCI token issuance but receives `ORA-01017` from financialdb in three clients. Changing Kubernetes vendors alone is not an evidenced fix. The unresolved boundary is database acceptance of that workload identity; it has not been isolated to Kubernetes, IAM, or a particular server check.
- Next, when this work is explicitly resumed: identify the supported Google-to-Oracle trust mechanism; use a dedicated identity and least-privilege mapping; test token issuance, actual session identity, denied-account behavior and renewal separately. Do not substitute an operator API key and call it GKE workload identity.

[GKE Workload Identity Federation](https://cloud.google.com/kubernetes-engine/docs/concepts/workload-identity) · [Oracle database IAM integration environments](https://docs.oracle.com/en/database/oracle/oracle-database/26/dbseg/introduction-authenticating-and-authorizing-iam-users-oracle-dbaas.html)
