package demo;

import com.oracle.bmc.ClientConfiguration;
import com.oracle.bmc.auth.okeworkloadidentity.OkeWorkloadIdentityAuthenticationDetailsProvider;
import com.oracle.bmc.identitydataplane.DataplaneClient;
import com.oracle.bmc.identitydataplane.model.GenerateScopedAccessTokenDetails;
import com.oracle.bmc.identitydataplane.requests.GenerateScopedAccessTokenRequest;
import com.oracle.bmc.model.BmcException;
import com.oracle.bmc.retrier.RetryConfiguration;
import oracle.jdbc.AccessToken;
import oracle.jdbc.datasource.impl.OracleDataSource;

import java.security.KeyPairGenerator;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/** Evaluation only. No API-key, instance-principal, password, or mock fallback. */
public final class WorkloadIdentityDemo {
    private static String stage = "configuration";
    private static final AtomicInteger tokenRequests = new AtomicInteger();

    public static void main(String[] args) {
        try {
            if (args.length != 1) throw new IllegalArgumentException("Exactly one mode required");
            Settings s = Settings.from(System.getenv(), args[0]);
            if (s.mode().equals("check")) { event("configuration", "PASS"); return; }
            if (s.mode().equals("native-resource-principal")) {
                stage = "native-resource-principal";
                OracleDataSource ds = dataSource(s);
                ds.setConnectionProperty("oracle.jdbc.tokenAuthentication", "OCI_RESOURCE_PRINCIPAL");
                ds.setConnectionProperty("oracle.jdbc.ociCompartment", s.compartment());
                ds.setConnectionProperty("oracle.jdbc.ociDatabase", s.database());
                connect(ds, s);
                return;
            }
            stage = "oke-identity";
            // Construct one provider per process; reuse its SDK-managed identity cache.
            var identity = OkeWorkloadIdentityAuthenticationDetailsProvider.builder().build();
            String signingKeyId = identity.getKeyId();
            if (signingKeyId == null || signingKeyId.isBlank())
                throw new IllegalStateException("No workload session token");
            event(stage, "PASS"); // Never log the token or its claims.
            if (s.mode().equals("identity")) return;
            try (var client = DataplaneClient.builder().region(s.region())
                    .configuration(ClientConfiguration.builder().connectionTimeoutMillis(10000)
                            .readTimeoutMillis(20000).retryConfiguration(RetryConfiguration.NO_RETRY_CONFIGURATION).build())
                    .build(identity)) {
                Supplier<? extends AccessToken> tokens = AccessToken.createJsonWebTokenCache(() -> token(client, s));
                stage = "database-token";
                tokens.get();
                if (s.mode().equals("token")) return;
                OracleDataSource ds = dataSource(s);
                ds.setTokenSupplier(tokens);
                connect(ds, s);
            }
        } catch (Exception e) {
            // Do not print raw SDK/JDBC messages or stack traces: they can include sensitive request details.
            System.err.printf("time=%s stage=%s result=FAIL type=%s%n", Instant.now(), stage, e.getClass().getSimpleName());
            for (Throwable t = e; t != null; t = t.getCause()) {
                System.err.printf("causeType=%s%n", t.getClass().getSimpleName());
                String detail = t.getMessage();
                for (String setting : java.util.List.of("OCI_RESOURCE_PRINCIPAL_VERSION", "OCI_RESOURCE_PRINCIPAL_RPST", "OCI_RESOURCE_PRINCIPAL_PRIVATE_PEM")) {
                    if (detail != null && detail.contains(setting))
                        System.err.printf("referencedSetting=%s present=%s%n", setting, System.getenv(setting) != null);
                }
                if (t instanceof BmcException b) System.err.printf("httpStatus=%d serviceCode=%s%n", b.getStatusCode(), safe(b.getServiceCode()));
                if (t instanceof SQLException q) System.err.printf("oracleError=%d sqlState=%s%n", q.getErrorCode(), safe(q.getSQLState()));
            }
            System.exit(1);
        }
    }

    private static AccessToken token(DataplaneClient client, Settings s) {
        stage = "database-token";
        try {
            var generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            var pair = generator.generateKeyPair();
            var request = GenerateScopedAccessTokenRequest.builder()
                    .generateScopedAccessTokenDetails(GenerateScopedAccessTokenDetails.builder()
                            .scope(s.scope()).publicKey(Base64.getEncoder().encodeToString(pair.getPublic().getEncoded())).build()).build();
            char[] jwt = client.generateScopedAccessToken(request).getSecurityToken().getToken().toCharArray();
            try {
                AccessToken token = AccessToken.createJsonWebToken(jwt, pair.getPrivate());
                if ("true".equals(System.getenv("WRITE_PRINCIPAL_EVIDENCE"))) {
                    // Opt-in private Kubernetes metadata, never a token or a committed artifact.
                    java.nio.file.Files.writeString(java.nio.file.Path.of("/dev/termination-log"), principalSubject(jwt));
                }
                System.out.printf("time=%s stage=database-token result=PASS acquisition=%d%n", Instant.now(), tokenRequests.incrementAndGet());
                return token;
            } finally { Arrays.fill(jwt, '\0'); }
        } catch (Exception e) { throw new IllegalStateException("Database token acquisition failed", e); }
    }
    static String principalSubject(char[] token) throws Exception {
        String[] parts = new String(token).split("\\.");
        if (parts.length != 3) throw new IllegalArgumentException("Invalid JWT envelope");
        byte[] payload = Base64.getUrlDecoder().decode(parts[1]);
        try {
            var claims = new com.fasterxml.jackson.databind.ObjectMapper().readTree(payload);
            String subject = claims.path("sub").asText();
            if (!subject.matches("ocid1\\.workload\\.[a-zA-Z0-9._:-]+"))
                throw new IllegalArgumentException("Unexpected workload subject type");
            return subject;
        } finally { Arrays.fill(payload, (byte)0); }
    }

    private static OracleDataSource dataSource(Settings s) throws SQLException {
        OracleDataSource ds = new OracleDataSource();
        ds.setURL(s.url());
        ds.setLoginTimeout(30);
        ds.setConnectionProperty("oracle.jdbc.ReadTimeout", "30000");
        return ds;
    }

    private static void connect(OracleDataSource ds, Settings s) throws Exception {
        for (int round = 1; round <= s.rounds(); round++) {
            stage = s.mode().equals("native-resource-principal") ? "native-resource-principal" : "jdbc-login";
            try (Connection c = ds.getConnection(); var statement = c.prepareStatement(
                    "select sys_context('USERENV','SESSION_USER'), sys_context('USERENV','AUTHENTICATION_METHOD'), " +
                    "sys_context('USERENV','AUTHENTICATED_IDENTITY'), systimestamp from dual")) {
                statement.setQueryTimeout(15);
                try (var rows = statement.executeQuery()) {
                    if (!rows.next() || !s.expectedUser().equals(rows.getString(1)))
                        throw new IllegalStateException("Unexpected database mapping");
                    // Query authenticated identity for operator correlation, but do not publish its raw value.
                    if (rows.getString(3) == null) throw new IllegalStateException("No authenticated identity returned");
                    System.out.printf("time=%s stage=jdbc-login result=PASS round=%d sessionUser=%s authentication=%s databaseTime=%s%n",
                            Instant.now(), round, safe(rows.getString(1)), safe(rows.getString(2)), rows.getTimestamp(4));
                }
            }
            if (round < s.rounds()) Thread.sleep(s.intervalSeconds() * 1000L);
        }
    }
    private static String safe(String value) { return value == null ? "null" : value.replaceAll("[^a-zA-Z0-9_:-]", "_"); }
    private static void event(String stage, String result) { System.out.printf("time=%s stage=%s result=%s%n", Instant.now(), stage, result); }
}
