package demo;

import java.util.Map;
import java.util.Set;

record Settings(String mode, String region, String compartment, String database, String url,
                String expectedUser, int rounds, int intervalSeconds, String walletDirectory) {
    static Settings from(Map<String, String> env, String mode) {
        if (!Set.of("check", "identity", "token", "jdbc", "native-resource-principal").contains(mode))
            throw new IllegalArgumentException("Unknown mode");
        for (String key : Set.of("DB_PASSWORD", "DB_USERNAME", "OCI_CONFIG_FILE", "OCI_CLI_AUTH")) {
            if (env.containsKey(key) && !env.get(key).isBlank())
                throw new IllegalArgumentException("Remove alternate credentials: " + key);
        }
        String region = required(env, "OCI_REGION");
        if (!region.matches("[a-z]+-[a-z]+-[0-9]+")) throw new IllegalArgumentException("Invalid OCI_REGION");
        String wallet = env.getOrDefault("DB_WALLET_DIR", "");
        if (!wallet.isEmpty() && (!wallet.equals("/var/run/oracle-wallet") ||
                !Set.of("check", "jdbc", "native-resource-principal").contains(mode)))
            throw new IllegalArgumentException("Wallet must use the dedicated read-only JDBC mount");
        if (mode.equals("identity")) return new Settings(mode, region, "", "", "", "", 1, 0, "");
        String compartment = required(env, "OCI_COMPARTMENT_ID");
        String database = required(env, "OCI_DATABASE_ID");
        if (!compartment.matches("ocid1\\.compartment\\.[a-z0-9.-]+") ||
            !database.matches("ocid1\\.autonomousdatabase\\.[a-z0-9.-]+"))
            throw new IllegalArgumentException("Use an exact compartment and Autonomous Database OCID; no wildcard scope");
        String url = env.getOrDefault("DB_JDBC_URL", "");
        String user = env.getOrDefault("EXPECTED_DB_USER", "");
        if (!mode.equals("token")) {
            if (!url.matches("jdbc:oracle:thin:@tcps://[a-zA-Z0-9.-]+:[0-9]+/[a-zA-Z0-9_.-]+\\?connect_timeout=15sec&transport_connect_timeout=10sec&retry_count=0"))
                throw new IllegalArgumentException("Use the documented credential-free TCPS EZConnect+ URL with bounded timeouts");
            if (!user.matches("[A-Z][A-Z0-9_]{0,127}")) throw new IllegalArgumentException("Set EXPECTED_DB_USER to the approved mapping");
        }
        int rounds = number(env, "TEST_ROUNDS", 1, 1, 120);
        int interval = number(env, "TEST_INTERVAL_SECONDS", 0, 0, 3600);
        return new Settings(mode, region, compartment, database, url, user, rounds, interval, wallet);
    }
    String scope() { return "urn:oracle:db::id::" + compartment + "::" + database; }
    private static String required(Map<String,String> env, String name) {
        String value = env.get(name);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing " + name);
        return value;
    }
    private static int number(Map<String,String> env, String name, int fallback, int min, int max) {
        int value = Integer.parseInt(env.getOrDefault(name, Integer.toString(fallback)));
        if (value < min || value > max) throw new IllegalArgumentException("Out of range: " + name);
        return value;
    }
}
