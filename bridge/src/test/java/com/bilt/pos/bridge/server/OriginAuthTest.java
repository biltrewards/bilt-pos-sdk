package com.bilt.pos.bridge.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bilt.pos.host.HostRequest;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class OriginAuthTest {

  private static HostRequest request(String origin) {
    return new HostRequest() {
      @Override
      public String method() {
        return "GET";
      }

      @Override
      public String path() {
        return "/v1/sessions";
      }

      @Override
      public String header(String name) {
        return name.equalsIgnoreCase("Origin") ? origin : null;
      }

      @Override
      public String remoteAddress() {
        return "127.0.0.1";
      }
    };
  }

  @Test
  void wildcardAdmitsEveryOrigin() {
    OriginAuth auth = new OriginAuth(() -> List.of("*"));

    assertTrue(auth.permits(request("https://pos.example.com")));
    assertTrue(auth.permits(request(null)));
  }

  @Test
  void listedOriginsMatchCaseInsensitivelyAndOthersAreRefused() {
    OriginAuth auth = new OriginAuth(() -> List.of("https://pos.example.com"));

    assertTrue(auth.permits(request("https://POS.example.com")));
    assertFalse(auth.permits(request("https://evil.example.com")));
    assertTrue(auth.permits(request(null)), "non-browser clients carry no Origin");
  }

  @Test
  void allowListIsReadLive() {
    AtomicReference<List<String>> origins = new AtomicReference<>(List.of("https://a.example"));
    OriginAuth auth = new OriginAuth(origins::get);

    assertFalse(auth.allows("https://b.example"));
    origins.set(List.of("https://b.example"));
    assertTrue(auth.allows("https://b.example"));
    assertEquals(false, auth.allows("https://a.example"));
  }
}
