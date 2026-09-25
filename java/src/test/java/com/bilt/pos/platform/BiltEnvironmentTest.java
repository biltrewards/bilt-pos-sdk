package com.bilt.pos.platform;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import org.junit.jupiter.api.Test;

class BiltEnvironmentTest {

  @Test
  void shippedEnvironmentsTargetPosEndpoints() {
    assertEquals("PRODUCTION", BiltEnvironment.PRODUCTION.name());
    assertEquals(
        URI.create("https://www.bilt.com/realms/ENTERPRISE-POS/protocol/openid-connect/token"),
        BiltEnvironment.PRODUCTION.tokenEndpoint());
    assertEquals(
        URI.create("https://api.bilt.com/biltpos"), BiltEnvironment.PRODUCTION.apiBaseUrl());

    assertEquals("STAGING", BiltEnvironment.STAGING.name());
    assertEquals(
        URI.create(
            "https://staging.biltrewards.com/realms/ENTERPRISE-POS/protocol/openid-connect/token"),
        BiltEnvironment.STAGING.tokenEndpoint());
    assertEquals(
        URI.create("https://api.staging.bilt.dev/biltpos"), BiltEnvironment.STAGING.apiBaseUrl());
    assertNotEquals(BiltEnvironment.PRODUCTION, BiltEnvironment.STAGING);
  }

  @Test
  void customEnvironmentCarriesItsEndpoints() {
    URI token = URI.create("http://localhost:8080/token");
    URI api = URI.create("http://localhost:8080/api/");

    BiltEnvironment env = BiltEnvironment.custom(token, api);

    assertEquals("CUSTOM", env.name());
    assertEquals(token, env.tokenEndpoint());
    assertEquals(api, env.apiBaseUrl());
    assertEquals(BiltEnvironment.custom(token, api), env);
    assertTrue(env.toString().contains("localhost:8080"));
  }

  @Test
  void customEnvironmentRejectsNonHttpOrRelativeUrls() {
    URI ok = URI.create("https://example.test/");
    assertThrows(
        IllegalArgumentException.class,
        () -> BiltEnvironment.custom(URI.create("ftp://example.test/token"), ok));
    assertThrows(
        IllegalArgumentException.class,
        () -> BiltEnvironment.custom(ok, URI.create("/relative/path")));
    assertThrows(NullPointerException.class, () -> BiltEnvironment.custom(null, ok));
  }
}
