package com.bilt.pos.platform;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class BiltCredentialsTest {

  @Test
  void toStringRedactsTheSecret() {
    BiltCredentials credentials = BiltCredentials.clientCredentials("pos-client", "hunter2");

    String text = credentials.toString();

    assertFalse(text.contains("hunter2"), text);
    assertTrue(text.contains("pos-client"));
    assertTrue(text.contains("<redacted>"));
    assertTrue(text.contains("CLIENT_CREDENTIALS"));
  }

  @Test
  void exposesClientIdAndCopiesTheSecret() {
    BiltCredentials credentials = BiltCredentials.clientCredentials("pos-client", "hunter2");

    assertEquals("pos-client", credentials.clientId());
    assertEquals(BiltCredentials.Kind.CLIENT_CREDENTIALS, credentials.kind());
    char[] copy = credentials.copySecret();
    assertArrayEquals("hunter2".toCharArray(), copy);
    java.util.Arrays.fill(copy, 'x');
    assertArrayEquals("hunter2".toCharArray(), credentials.copySecret(), "copy is independent");
  }

  @Test
  void equalityCoversIdAndSecret() {
    BiltCredentials a = BiltCredentials.clientCredentials("id", "secret");
    BiltCredentials b = BiltCredentials.clientCredentials("id", "secret");
    BiltCredentials otherSecret = BiltCredentials.clientCredentials("id", "other");
    BiltCredentials otherId = BiltCredentials.clientCredentials("id2", "secret");

    assertEquals(a, b);
    assertEquals(a.hashCode(), b.hashCode());
    assertNotEquals(a, otherSecret);
    assertNotEquals(a, otherId);
  }

  @Test
  void rejectsMissingParts() {
    assertThrows(NullPointerException.class, () -> BiltCredentials.clientCredentials(null, "s"));
    assertThrows(NullPointerException.class, () -> BiltCredentials.clientCredentials("id", null));
    assertThrows(IllegalArgumentException.class, () -> BiltCredentials.clientCredentials("", "s"));
    assertThrows(IllegalArgumentException.class, () -> BiltCredentials.clientCredentials("id", ""));
  }
}
