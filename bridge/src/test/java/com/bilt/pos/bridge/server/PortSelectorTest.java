package com.bilt.pos.bridge.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.BindException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PortSelectorTest {

  private static final InetAddress LOOPBACK = InetAddress.getLoopbackAddress();

  @Test
  void takesPreferredPortWhenFree() throws Exception {
    List<Integer> tried = new ArrayList<>();
    int bound = PortSelector.bind(LOOPBACK, 50100, 10, port -> tried.add(port) ? port : -1);

    assertEquals(50100, bound);
    assertEquals(List.of(50100), tried);
  }

  @Test
  void fallsBackToNextFreePortAgainstRealSockets() throws Exception {
    try (ServerSocket taken = new ServerSocket(0, 1, LOOPBACK)) {
      int busy = taken.getLocalPort();
      int chosen =
          PortSelector.bind(
              LOOPBACK,
              busy,
              10,
              port -> {
                try (ServerSocket s = new ServerSocket(port, 1, LOOPBACK)) {
                  return s.getLocalPort();
                }
              });
      assertTrue(chosen > busy && chosen <= busy + 10, "chosen " + chosen + " for busy " + busy);
    }
  }

  @Test
  void failsWhenRangeIsExhausted() {
    IOException e =
        assertThrows(
            IOException.class,
            () ->
                PortSelector.bind(
                    LOOPBACK,
                    50200,
                    2,
                    port -> {
                      throw new BindException("taken");
                    }));
    assertTrue(e.getMessage().contains("no free port between 50200 and 50202"), e.getMessage());
    assertTrue(e.getCause() instanceof BindException);
  }

  @Test
  void ephemeralPortIsTriedOnce() throws Exception {
    List<Integer> tried = new ArrayList<>();
    int bound = PortSelector.bind(LOOPBACK, 0, 10, port -> tried.add(port) ? 61234 : -1);

    assertEquals(61234, bound);
    assertEquals(List.of(0), tried);
  }

  @Test
  void refusesNonLoopbackAddress() throws Exception {
    InetAddress any = InetAddress.getByName("0.0.0.0");
    assertThrows(IllegalArgumentException.class, () -> PortSelector.bind(any, 0, 0, port -> port));
  }
}
