package com.bilt.pos.platform;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class PlatformRequestResponseTest {

  @Test
  void requestNormalizesMethodAndPathAndCopiesBody() {
    byte[] payload = "{}".getBytes(StandardCharsets.UTF_8);
    PlatformRequest request =
        PlatformRequest.builder()
            .method("post")
            .path("//v1/things")
            .header("X-A", "1")
            .header("x-a", "2")
            .body(payload, "application/json")
            .timeout(Duration.ofSeconds(3))
            .build();
    payload[0] = 'x';

    assertEquals("POST", request.method());
    assertEquals("v1/things", request.path());
    assertEquals(1, request.headers().size());
    assertEquals("2", request.headers().get("x-a"));
    assertTrue(request.hasBody());
    assertEquals("{}", new String(request.body(), StandardCharsets.UTF_8));
    assertEquals("application/json", request.contentType());
    assertEquals(Duration.ofSeconds(3), request.timeout());
    assertFalse(request.toString().contains("{}"), "toString does not dump the body");
  }

  @Test
  void requestRejectsInvalidShapes() {
    assertThrows(IllegalStateException.class, () -> PlatformRequest.builder().path("a").build());
    assertThrows(
        IllegalStateException.class, () -> PlatformRequest.builder().method("GET").build());
    assertThrows(
        IllegalStateException.class, () -> PlatformRequest.get("a").jsonBody("{}").build());
    assertThrows(IllegalArgumentException.class, () -> PlatformRequest.builder().method("GE T"));
    assertThrows(
        IllegalArgumentException.class,
        () -> PlatformRequest.get("a").header("Authorization", "Bearer x"));
    assertThrows(
        IllegalArgumentException.class, () -> PlatformRequest.get("a").timeout(Duration.ZERO));
    assertFalse(PlatformRequest.get("a").build().hasBody());
    assertEquals(0, PlatformRequest.get("a").build().body().length);
    assertNull(PlatformRequest.get("a").build().contentType());
  }

  @Test
  void responseExposesStatusHeadersAndBody() {
    PlatformResponse response =
        PlatformResponse.builder()
            .status(201)
            .addHeader("Content-Type", "application/json")
            .addHeader("X-Multi", "a")
            .addHeader("X-Multi", "b")
            .body("{\"id\":1}")
            .build();

    assertEquals(201, response.status());
    assertTrue(response.isSuccessful());
    assertEquals("application/json", response.header("content-type"));
    assertEquals("a", response.header("X-Multi"));
    assertEquals(2, response.headers().get("X-Multi").size());
    assertNull(response.header("Missing"));
    assertEquals("{\"id\":1}", response.bodyAsString());
    assertFalse(PlatformResponse.builder().status(404).build().isSuccessful());
    assertThrows(IllegalStateException.class, () -> PlatformResponse.builder().build());
    assertThrows(IllegalArgumentException.class, () -> PlatformResponse.builder().status(42));
  }
}
