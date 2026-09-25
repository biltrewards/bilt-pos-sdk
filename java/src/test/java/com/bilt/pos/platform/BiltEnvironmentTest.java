package com.bilt.pos.platform;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import org.junit.jupiter.api.Test;

class BiltEnvironmentTest {

  @Test
  void shippedEnvironmentsHavePlaceholderEndpoints() {
    assertEquals("PRODUCTION", BiltEnvironment.PRODUCTION.name());
    assertEquals(
        URI.create("https://auth.prod.bilt.example/oauth2/token"),
        BiltEnvironment.PRODUCTION.tokenEndpoint());
    assertEquals(
        URI.create("https://api.prod.bilt.example"), BiltEnvironment.PRODUCTION.apiBaseUrl());

    assertEquals("STAGING", BiltEnvironment.STAGING.name());
    assertEquals(
        URI.create("https://auth.staging.bilt.example/oauth2/token"),
        BiltEnvironment.STAGING.tokenEndpoint());
    assertEquals(
        URI.create("https://api.staging.bilt.example"), BiltEnvironment.STAGING.apiBaseUrl());
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
