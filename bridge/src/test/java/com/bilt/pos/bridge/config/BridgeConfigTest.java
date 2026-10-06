package com.bilt.pos.bridge.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bilt.pos.nexo.client.BiltTerminalEnvironment;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BridgeConfigTest {

  private static final String DEV_TERMINAL =
      """
      { "poiId": "VictaLane-1", "host": "192.168.4.108", "port": 8443,
        "encryption": false, "passphrase": null, "keyId": null,
        "trustAll": true, "caCertificatePath": null, "environment": null }
      """;

  @Test
  void emptyObjectYieldsDefaults() throws Exception {
    BridgeConfig config = BridgeConfig.parse("{}");

    assertEquals(BridgeConfig.DEFAULT_PORT, config.port());
    assertTrue(config.bindAddress().isLoopbackAddress());
    assertEquals(List.of("*"), config.allowedOrigins());
    assertTrue(config.allowsAnyOrigin());
    assertTrue(config.terminals().isEmpty());
  }

  @Test
  void starterFileParsesWithOneDevelopmentTerminal() throws Exception {
    BridgeConfig config = BridgeConfig.parse(FileBridgeConfigSource.exampleJson());

    assertEquals(1, config.terminals().size());
    TerminalConfig t = config.terminals().get(0);
    assertEquals("VictaLane-275839164", t.poiId());
    assertEquals("https://192.168.4.108:8443/nexo", t.endpoint());
    assertTrue(t.trustAll());
    assertFalse(t.encryption());
    assertEquals(Optional.empty(), t.environment());
  }

  @Test
  void underscoreKeysAreCommentsButOtherUnknownKeysAreRejected() throws Exception {
    BridgeConfig.parse("{ \"_comment\": \"hi\", \"_note\": [1,2], \"port\": 50000 }");

    BridgeConfigException e =
        assertThrows(BridgeConfigException.class, () -> BridgeConfig.parse("{ \"prot\": 1 }"));
    assertTrue(e.getMessage().contains("unknown key 'prot'"), e.getMessage());
  }

  @Test
  void rejectsBadPorts() {
    for (String json :
        List.of("{\"port\": 0}", "{\"port\": 65536}", "{\"port\": \"48333\"}", "{\"port\": 1.5}")) {
      BridgeConfigException e =
          assertThrows(BridgeConfigException.class, () -> BridgeConfig.parse(json), json);
      assertTrue(e.getMessage().startsWith("port must be"), e.getMessage());
    }
  }

  @Test
  void refusesNonLoopbackBindAddress() {
    for (String address : List.of("0.0.0.0", "192.168.1.20", "::")) {
      BridgeConfigException e =
          assertThrows(
              BridgeConfigException.class,
              () -> BridgeConfig.parse("{\"bindAddress\": \"" + address + "\"}"),
              address);
      assertTrue(e.getMessage().contains("not a loopback address"), e.getMessage());
    }
  }

  @Test
  void acceptsLoopbackBindAddresses() throws Exception {
    assertTrue(
        BridgeConfig.parse("{\"bindAddress\": \"127.0.0.1\"}").bindAddress().isLoopbackAddress());
    assertTrue(BridgeConfig.parse("{\"bindAddress\": \"::1\"}").bindAddress().isLoopbackAddress());
    assertTrue(
        BridgeConfig.parse("{\"bindAddress\": \"localhost\"}").bindAddress().isLoopbackAddress());
  }

  @Test
  void validatesOrigins() throws Exception {
    assertEquals(
        List.of("https://pos.example.com", "http://localhost:3000"),
        BridgeConfig.parse(
                "{\"allowedOrigins\": [\"https://pos.example.com\", \"http://localhost:3000\"]}")
            .allowedOrigins());
    assertThrows(BridgeConfigException.class, () -> BridgeConfig.parse("{\"allowedOrigins\": []}"));
    assertThrows(
        BridgeConfigException.class,
        () -> BridgeConfig.parse("{\"allowedOrigins\": [\"https://pos.example.com/path\"]}"));
    assertThrows(
        BridgeConfigException.class, () -> BridgeConfig.parse("{\"allowedOrigins\": \"*\"}"));
  }

  @Test
  void terminalRequiresPoiIdAndHost() {
    BridgeConfigException noId =
        assertThrows(
            BridgeConfigException.class,
            () ->
                BridgeConfig.parse(
                    "{\"terminals\": [{\"host\": \"10.0.0.1\", \"trustAll\": true}]}"));
    assertTrue(noId.getMessage().contains("terminals[0].poiId is required"), noId.getMessage());

    BridgeConfigException noHost =
        assertThrows(
            BridgeConfigException.class,
            () -> BridgeConfig.parse("{\"terminals\": [{\"poiId\": \"T1\", \"trustAll\": true}]}"));
    assertTrue(noHost.getMessage().contains("terminals[0].host is required"), noHost.getMessage());
  }

  @Test
  void encryptionNeedsPassphraseAndKeyId() {
    String base =
        "{\"terminals\": [{\"poiId\": \"T1\", \"host\": \"h\", \"trustAll\": true, \"encryption\": true";
    BridgeConfigException noPass =
        assertThrows(BridgeConfigException.class, () -> BridgeConfig.parse(base + "}]}"));
    assertTrue(noPass.getMessage().contains("passphrase is missing"), noPass.getMessage());

    BridgeConfigException noKey =
        assertThrows(
            BridgeConfigException.class,
            () -> BridgeConfig.parse(base + ", \"passphrase\": \"s3cret\"}]}"));
    assertTrue(noKey.getMessage().contains("keyId is missing"), noKey.getMessage());
  }

  @Test
  void trustAllExcludesCaAndEnvironment() {
    BridgeConfigException e =
        assertThrows(
            BridgeConfigException.class,
            () ->
                BridgeConfig.parse(
                    "{\"terminals\": [{\"poiId\": \"T1\", \"host\": \"h\", \"trustAll\": true,"
                        + " \"environment\": \"STAGING\"}]}"));
    assertTrue(e.getMessage().contains("trustAll cannot be combined"), e.getMessage());
  }

  @Test
  void verifiedTerminalNeedsExistingCaAndEnvironment(@TempDir Path dir) throws Exception {
    Path ca = dir.resolve("ca.pem");
    Files.writeString(ca, "-----BEGIN CERTIFICATE-----\n");
    String caJson = ca.toString().replace("\\", "\\\\");

    BridgeConfigException noCa =
        assertThrows(
            BridgeConfigException.class,
            () -> BridgeConfig.parse("{\"terminals\": [{\"poiId\": \"T1\", \"host\": \"h\"}]}"));
    assertTrue(noCa.getMessage().contains("caCertificatePath is required"), noCa.getMessage());

    BridgeConfigException noEnv =
        assertThrows(
            BridgeConfigException.class,
            () ->
                BridgeConfig.parse(
                    "{\"terminals\": [{\"poiId\": \"T1\", \"host\": \"h\", \"caCertificatePath\": \""
                        + caJson
                        + "\"}]}"));
    assertTrue(noEnv.getMessage().contains("environment"), noEnv.getMessage());

    BridgeConfigException missingFile =
        assertThrows(
            BridgeConfigException.class,
            () ->
                BridgeConfig.parse(
                    "{\"terminals\": [{\"poiId\": \"T1\", \"host\": \"h\", \"environment\": \"STAGING\","
                        + " \"caCertificatePath\": \""
                        + caJson
                        + ".missing\"}]}"));
    assertTrue(missingFile.getMessage().contains("does not exist"), missingFile.getMessage());

    BridgeConfig ok =
        BridgeConfig.parse(
            "{\"terminals\": [{\"poiId\": \"T1\", \"host\": \"h\", \"environment\": \"staging\","
                + " \"caCertificatePath\": \""
                + caJson
                + "\", \"encryption\": true, \"passphrase\": \"p\", \"keyId\": \"k\","
                + " \"keyVersion\": 2}]}");
    TerminalConfig t = ok.terminals().get(0);
    assertEquals(Optional.of(BiltTerminalEnvironment.STAGING), t.environment());
    assertEquals(2, t.keyVersion());
    assertFalse(t.trustAll());
  }

  @Test
  void rejectsUnknownEnvironmentAndDuplicateIds() {
    BridgeConfigException env =
        assertThrows(
            BridgeConfigException.class,
            () ->
                BridgeConfig.parse(
                    "{\"terminals\": [{\"poiId\": \"T1\", \"host\": \"h\", \"environment\": \"QA\","
                        + " \"caCertificatePath\": \"/x\"}]}"));
    assertTrue(env.getMessage().contains("PRODUCTION or STAGING"), env.getMessage());

    BridgeConfigException dup =
        assertThrows(
            BridgeConfigException.class,
            () ->
                BridgeConfig.parse("{\"terminals\": [" + DEV_TERMINAL + "," + DEV_TERMINAL + "]}"));
    assertTrue(dup.getMessage().contains("duplicate terminal poiId"), dup.getMessage());
  }

  @Test
  void redactedCopyHidesPassphraseEverywhere() throws Exception {
    BridgeConfig config =
        BridgeConfig.parse(
            "{\"terminals\": [{\"poiId\": \"T1\", \"host\": \"h\", \"trustAll\": true,"
                + " \"encryption\": true, \"passphrase\": \"hunter2\", \"keyId\": \"k\"}]}");

    String json = config.redacted().toJson().toString();
    assertFalse(json.contains("hunter2"), json);
    assertTrue(json.contains("\"passphrase\":\"***\""), json);
    assertFalse(config.toString().contains("hunter2"), config.toString());
    assertFalse(config.terminals().get(0).toString().contains("hunter2"));
  }

  @Test
  void invalidJsonIsReportedAsSuch() {
    BridgeConfigException e =
        assertThrows(BridgeConfigException.class, () -> BridgeConfig.parse("{ port: 1 "));
    assertTrue(e.getMessage().startsWith("config is not valid JSON"), e.getMessage());
  }
}
