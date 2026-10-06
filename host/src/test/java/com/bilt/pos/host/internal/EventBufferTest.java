package com.bilt.pos.host.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/** Live delivery follows the sequence, and a joining subscriber sees each event exactly once. */
class EventBufferTest {

  private static final int PUBLISHERS = 8;
  private static final int PER_PUBLISHER = 250;

  @Test
  void concurrentPublishersAndLateSubscribersSeeEveryEventOnceInOrder() throws Exception {
    int total = PUBLISHERS * PER_PUBLISHER;
    EventBuffer buffer = new EventBuffer(total, Duration.ofMinutes(5));
    List<List<Long>> seen = new ArrayList<>();
    CountDownLatch done = new CountDownLatch(4);
    CountDownLatch go = new CountDownLatch(1);

    List<Thread> publishers = new ArrayList<>();
    for (int p = 0; p < PUBLISHERS; p++) {
      Thread thread =
          new Thread(
              () -> {
                try {
                  go.await();
                } catch (InterruptedException e) {
                  return;
                }
                for (int i = 0; i < PER_PUBLISHER; i++) {
                  buffer.publish("tick", JsonNodeFactory.instance.objectNode());
                }
              });
      publishers.add(thread);
      thread.start();
    }
    go.countDown();
    // subscribers join while the publishers are mid-flight
    for (int s = 0; s < 4; s++) {
      List<Long> sequence = new CopyOnWriteArrayList<>();
      seen.add(sequence);
      buffer.subscribe(
          0,
          new EventBuffer.Subscriber() {
            @Override
            public void onEvent(Event event) {
              sequence.add(event.seq());
              if (event.seq() == total) {
                done.countDown();
              }
            }
          });
      Thread.sleep(1);
    }
    for (Thread publisher : publishers) {
      publisher.join();
    }

    assertTrue(done.await(10, TimeUnit.SECONDS), "every subscriber reaches the last event");
    for (List<Long> sequence : seen) {
      assertEquals(total, sequence.size());
      for (int i = 0; i < total; i++) {
        assertEquals(i + 1L, sequence.get(i));
      }
    }
  }
}
