package demo;

import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class SettingsTest {
    @Test void jdbcTokenRetainsItsCopyWhenInputIsWiped() throws Exception {
        // Synthetic data only: detect accidental destruction of the driver's token handoff.
        var encoder = java.util.Base64.getUrlEncoder().withoutPadding();
        var charset = java.nio.charset.StandardCharsets.UTF_8;
        String header = encoder.encodeToString("{\"alg\":\"RS256\"}".getBytes(charset));
        String body = encoder.encodeToString("{\"exp\":2222222222,\"sub\":\"synthetic\"}".getBytes(charset));
        String original = header + "." + body + ".signature";
        var generator = java.security.KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        char[] input = original.toCharArray();
        var token = oracle.jdbc.AccessToken.createJsonWebToken(input, generator.generateKeyPair().getPrivate());
        java.util.Arrays.fill(input, '\0');
        char[] copy = token.toCharArray();
        try { assertArrayEquals(original.toCharArray(), copy); }
        finally { java.util.Arrays.fill(copy, '\0'); }
    }
    @Test void extractsOnlyWorkloadSubject() throws Exception {
        String payload=java.util.Base64.getUrlEncoder().withoutPadding().encodeToString("{\"sub\":\"ocid1.workload.oc1.example\",\"other\":\"not-emitted\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertEquals("ocid1.workload.oc1.example",WorkloadIdentityDemo.principalSubject(("header."+payload+".signature").toCharArray()));
    }
    @Test void rejectsNonWorkloadSubject() {
        String payload=java.util.Base64.getUrlEncoder().withoutPadding().encodeToString("{\"sub\":\"ocid1.user.oc1.example\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertThrows(IllegalArgumentException.class,()->WorkloadIdentityDemo.principalSubject(("header."+payload+".signature").toCharArray()));
    }
    private Map<String,String> valid() {
        return new HashMap<>(Map.of("OCI_REGION","eu-frankfurt-1", "OCI_COMPARTMENT_ID","ocid1.compartment.oc1..example",
                "OCI_DATABASE_ID","ocid1.autonomousdatabase.oc1.eu-frankfurt-1.example", "EXPECTED_DB_USER","OKE_JDBC_DEMO",
                "DB_JDBC_URL","jdbc:oracle:thin:@tcps://example.oraclecloud.com:1522/example_low?connect_timeout=15sec&transport_connect_timeout=10sec&retry_count=0"));
    }
    @Test void exactScope() { assertEquals("urn:oracle:db::id::ocid1.compartment.oc1..example::ocid1.autonomousdatabase.oc1.eu-frankfurt-1.example",Settings.from(valid(),"check").scope()); }
    @Test void unknownModeFails() { assertThrows(IllegalArgumentException.class,()->Settings.from(valid(),"auto")); }
    @Test void passwordRejected() { var e=valid();e.put("DB_PASSWORD","secret");assertThrows(IllegalArgumentException.class,()->Settings.from(e,"jdbc")); }
    @Test void apiKeyConfigRejected() { var e=valid();e.put("OCI_CONFIG_FILE","/config");assertThrows(IllegalArgumentException.class,()->Settings.from(e,"jdbc")); }
    @Test void usernameRejected() { var e=valid();e.put("DB_USERNAME","admin");assertThrows(IllegalArgumentException.class,()->Settings.from(e,"jdbc")); }
    @Test void wildcardRejected() { var e=valid();e.put("OCI_DATABASE_ID","*");assertThrows(IllegalArgumentException.class,()->Settings.from(e,"token")); }
    @Test void nonTlsRejected() { var e=valid();e.put("DB_JDBC_URL",e.get("DB_JDBC_URL").replace("tcps://","tcp://"));assertThrows(IllegalArgumentException.class,()->Settings.from(e,"jdbc")); }
    @Test void insecureUrlOptionRejected() { var e=valid();e.put("DB_JDBC_URL",e.get("DB_JDBC_URL")+"&ssl_server_dn_match=false");assertThrows(IllegalArgumentException.class,()->Settings.from(e,"jdbc")); }
    @Test void expectedMappingRequired() { var e=valid();e.remove("EXPECTED_DB_USER");assertThrows(IllegalArgumentException.class,()->Settings.from(e,"jdbc")); }
    @Test void roundsBounded() { var e=valid();e.put("TEST_ROUNDS","121");assertThrows(IllegalArgumentException.class,()->Settings.from(e,"jdbc")); }
    @Test void identityDoesNotRequireDatabase() { assertEquals("identity",Settings.from(Map.of("OCI_REGION","eu-frankfurt-1"),"identity").mode()); }
    @Test void tokenDoesNotRequireUrl() { var e=valid();e.remove("DB_JDBC_URL");e.remove("EXPECTED_DB_USER");assertEquals("token",Settings.from(e,"token").mode()); }
    @Test void optionalWalletUsesOnlyDedicatedMount() { var e=valid();e.put("DB_WALLET_DIR","/var/run/oracle-wallet");assertEquals("/var/run/oracle-wallet",Settings.from(e,"jdbc").walletDirectory()); }
    @Test void arbitraryWalletPathRejected() { var e=valid();e.put("DB_WALLET_DIR","/tmp/other");assertThrows(IllegalArgumentException.class,()->Settings.from(e,"jdbc")); }
    @Test void walletNotNeededForTokenAcquisition() { var e=valid();e.put("DB_WALLET_DIR","/var/run/oracle-wallet");assertThrows(IllegalArgumentException.class,()->Settings.from(e,"token")); }
}
