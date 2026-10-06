package com.bilt.pos.host.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/** A key is reserved by its first request, so a concurrent duplicate never runs the handler. */
class IdempotencyCacheTest {

  @Test
  void aConcurrentDuplicateIsRefusedWhileTheFirstRequestRuns() {
    IdempotencyCache cache = new IdempotencyCache(8);
    assertNull(cache.lookup("key", "fp"));
    assertEquals(409, assertThrows(HostError.class, () -> cache.lookup("key", "fp")).status());
    assertEquals(422, assertThrows(HostError.class, () -> cache.lookup("key", "other")).status());

    cache.store("key", "fp", 201, "{}");
    IdempotencyCache.Entry replay = cache.lookup("key", "fp");
    assertNotNull(replay);
    assertEquals(201, replay.status());
  }

  @Test
  void aReleasedKeyCanBeClaimedAgain() {
    IdempotencyCache cache = new IdempotencyCache(8);
    assertNull(cache.lookup("key", "fp"));
    cache.release("key");
    assertNull(cache.lookup("key", "fp"));
  }

  @Test
  void exactlyOneOfManyRacingRequestsOwnsTheKey() {
    IdempotencyCache cache = new IdempotencyCache(8);
    AtomicInteger owners = new AtomicInteger();
    IntStream.range(0, 32)
        .parallel()
        .forEach(
            i -> {
              try {
                if (cache.lookup("key", "fp") == null) {
                  owners.incrementAndGet();
                }
              } catch (HostError refused) {
                assertEquals(409, refused.status());
              }
            });
    assertEquals(1, owners.get());
  }
}
