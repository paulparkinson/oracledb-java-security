# Repository guidance

Keep credentials, tokens, wallets, kubeconfigs, customer identifiers, raw logs and internal correspondence out of Git. This repository is an evaluation, not a support statement.

For `workload-identity-oke`, run `mvn -B -ntp verify` and `node --test scripts/*.test.cjs` from that directory. Review HTML at desktop and mobile sizes when editing the article. Use TCPS EZConnect+ for database connections; never disable certificate verification.

Record observed results and unresolved prerequisites in `workload-identity-oke/STATUS.md`. Distinguish offline tests, pod identity, token issuance, database login and renewal. Do not fabricate successful test output or silently replace OKE identity with another credential source.

Cloud deployment, IAM changes and database mappings require explicit approval. Never modify existing shared workloads as part of an experiment. Preserve the repository's private visibility until publication is cleared.
