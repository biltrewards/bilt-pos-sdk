package com.bilt.pos.host.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/** An abort and a launch race for an operation just promoted off the lane; exactly one wins. */
class HostedOperationLaunchTest {

  @Test
  void anAbortBeforeTheLaunchStopsTheLaunch() {
    HostedOperation operation = new HostedOperation("settle", true);
    assertTrue(operation.abortBeforeLaunch());
    assertFalse(operation.claimLaunch());
  }

  @Test
  void onceLaunchedTheSdkAbortAppliesInstead() {
    HostedOperation operation = new HostedOperation("settle", true);
    assertTrue(operation.claimLaunch());
    assertFalse(operation.abortBeforeLaunch());
  }

  @Test
  void exactlyOneOfAnAbortAndALaunchWinsUnderContention() {
    for (int round = 0; round < 200; round++) {
      HostedOperation operation = new HostedOperation("settle", true);
      AtomicInteger winners = new AtomicInteger();
      IntStream.range(0, 2)
          .parallel()
          .forEach(
              side -> {
                boolean won = side == 0 ? operation.claimLaunch() : operation.abortBeforeLaunch();
                if (won) {
                  winners.incrementAndGet();
                }
              });
      assertEquals(1, winners.get());
    }
  }
}
