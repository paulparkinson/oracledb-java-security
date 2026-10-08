# Sources and decision boundaries

Reviewed 2026-10-08. Public sources only; internal correspondence is not a publishable source or proof of support.

- [Exclusive instance/resource-principal database mappings](https://docs.oracle.com/en/database/oracle/oracle-database/26/dbseg/accessing-database-using-instance-principal-or-resource-principal.html): documents `IAM_PRINCIPAL_OCID` as an alternative to shared dynamic-group mappings. In contrast, the Autonomous service guide below requires dynamic groups. Neither statement establishes that our workload mapping works on financialdb.
- [Oracle A-Team OKE-to-ADB example](https://www.ateam-oracle.com/connecting-oracle-kubernetes-engine-oke-namespaces-to-autonomous-database-with-oci-iamconnecting-oracle-kubernetes-engine-oke-namespaces-to-autonomous-database-with-oci-iam): describes extracting the workload subject and using an exclusive mapping. Its dynamic-group example conflicts with the current OKE documentation; this evaluation instead uses workload-conditioned IAM policy and tests it directly.
- [IAM login troubleshooting](https://docs.oracle.com/en/database/oracle/oracle-database/19/dbseg/troubleshooting-iam-connections.html): inspect database provider configuration and global mappings for ORA-01017; do not infer the exact cause from that code alone.
- [ORA-18726](https://docs.oracle.com/en/error-help/db/ora-18726/): JDBC resource-provider lookup/acquisition failure; inspect sanitized causes separately from SQL authentication.

- [OKE workload identity](https://docs.oracle.com/en-us/iaas/Content/ContEng/Tasks/contenggrantingworkloadaccesstoresources.htm): enhanced-cluster requirement, principal conditions, Java provider, provider reuse, and the dynamic-group restriction.
- [Database IAM users](https://docs.oracle.com/en/cloud/paas/autonomous-database/serverless/adbsb/iam-create-users.html): documented database IAM user mappings. Does not establish an OKE workload mapping for this evaluation.
- [Autonomous IAM groups and policies](https://docs.oracle.com/en-us/iaas/autonomous-database-serverless/doc/iam-create-groups-policies.html): explicitly requires dynamic-group mappings for instance/resource principals. This conflicts with the general database guide and A-Team direct-mapping example; OKE disallows workload dynamic groups. Record this discrepancy rather than claiming a confirmed root cause or trying an unsupported dynamic-group workaround.
- [Native client tracing](https://docs.oracle.com/en/database/oracle/oracle-database/19/dbseg/troubleshooting-iam-connections.html): `EVENT_25701=15` with a private client `ADR_BASE`. Tested with Oracle Instant Client/SQL*Plus 23.26.3.0.0 using TCPS EZConnect+, `TOKEN_AUTH=OCI_TOKEN` and pod-local `TOKEN_LOCATION`. No server tracing, wallet upload or password login. Only sanitized findings were retained; see [status](STATUS.md).
- [Enable database IAM](https://docs.oracle.com/en/cloud/paas/autonomous-database/serverless/adbsb/enable-iam-authentication.html): administrator-owned prerequisite; not executed automatically.
- [Oracle JDBC extensions](https://github.com/oracle/ojdbc-extensions/tree/821dedebfacc4ea5bced7713ffac4269b5b2571b): reviewed commit `821dedebfacc4ea5bced7713ffac4269b5b2571b`, particularly OCI `AuthenticationDetailsFactory`, `AuthenticationMethod` and `AccessTokenFactory`. Released provider 1.1.0 was also inspected. Generic resource-principal selection is an observation about this code, not proof of all possible SDK runtime behavior.
- [OCI Java SDK](https://github.com/oracle/oci-java-sdk): explicit `OkeWorkloadIdentityAuthenticationDetailsProvider` API. This project pins 3.97.2 across OCI SDK dependencies.

## Decisions

- Use separate explicit-OKE and native-resource-principal paths to test the compatibility question without disguising custom code as unchanged configuration.
- Use database-scoped proof-of-possession tokens with an ephemeral RSA key, not a Kubernetes service-account token passed directly to JDBC.
- Keep deployment isolated; preserve all existing applications and database identity settings.
- Stop at an unsupported database mapping rather than invent SQL or switch identity types.
- Use TCPS EZConnect+ and keep application credentials out of the image, environment, source and logs.
- Publish observed outcomes with their limits. No successful end-to-end screenshot exists yet; an architecture illustration must not be presented as one.
