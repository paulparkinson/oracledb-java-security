# Sources and decision boundaries

Reviewed 2026-10-08. Public sources only; internal correspondence is not a publishable source or proof of support.

- [OKE workload identity](https://docs.oracle.com/en-us/iaas/Content/ContEng/Tasks/contenggrantingworkloadaccesstoresources.htm): enhanced-cluster requirement, principal conditions, Java provider, provider reuse, and the dynamic-group restriction.
- [Database IAM users](https://docs.oracle.com/en/cloud/paas/autonomous-database/serverless/adbsb/iam-create-users.html): documented database IAM user mappings. Does not establish an OKE workload mapping for this evaluation.
- [Database IAM groups and policies](https://docs.oracle.com/en/cloud/paas/autonomous-database/serverless/adbsb/iam-create-groups-policies.html): database IAM authorization background. The candidate policy in this repository is not a tested support claim.
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
