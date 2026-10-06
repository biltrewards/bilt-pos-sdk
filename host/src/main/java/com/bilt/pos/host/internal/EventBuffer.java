/*
 *    ____  _ _ _
 *   | __ )(_) | |_
 *   |  _ \| | | __|
 *   | |_) | | | |_
 *   |____/|_|_|\__|
 *
 *   Bilt POS SDK
 */
package com.bilt.pos.host.internal;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A session's event log and fan-out.
 *
 * <p>Every event gets the next sequence number and is kept for replay inside a bounded window — at
 * most {@code capacity} events and no older than {@code window} — so a client that reconnects with
 * {@code ?since=<seq>} picks up where it left off. Subscribers receive events in order on a thread
 * of their own, so a slow Server-Sent Events socket never stalls the session thread that published
 * the event, and publication under the lock guarantees a subscriber never sees an event twice or
 * misses one between its replay and its live feed.
 *
 * <p>{@link #close()} marks the stream finished after {@code session.ended}: live subscribers are
 * told so they can hang up, and a later subscriber still gets the replay before being told the
 * same.
 */
public final class EventBuffer {

  private static final Logger LOGGER = Logger.getLogger(EventBuffer.class.getName());
  private static final AtomicInteger SUBSCRIBER_COUNTER = new AtomicInteger();

  /** What a subscriber sees: the events, in order, then possibly the end of the stream. */
  public interface Subscriber {
    void onEvent(Event event);

    default void onClosed() {}
  }

  /** The handle a subscriber cancels with. */
  public interface Subscription extends AutoCloseable {
    @Override
    void close();
  }

  private final int capacity;
  private final Duration window;
  private final Clock clock;
  private final ArrayDeque<Event> events = new ArrayDeque<>();
  private final List<Delivery> deliveries = new ArrayList<>();
  private long nextSeq = 1;
  private boolean closed;

  public EventBuffer(int capacity, Duration window) {
    this(capacity, window, Clock.systemUTC());
  }

  EventBuffer(int capacity, Duration window, Clock clock) {
    if (capacity < 1) {
      throw new IllegalArgumentException("capacity must be positive");
    }
    this.capacity = capacity;
    this.window = window;
    this.clock = clock;
  }

  /**
   * Appends an event and fans it out. Offering stays inside the lock: it only enqueues on each
   * subscriber's own thread, and doing it here fixes the delivery order to the sequence order and
   * keeps a joining subscriber from getting the event through both its replay and the live feed.
   */
  public synchronized Event publish(String type, JsonNode payload) {
    Event event = new Event(nextSeq++, Instant.now(clock), type, payload);
    events.addLast(event);
    trim(event.at());
    for (Delivery delivery : deliveries) {
      delivery.offer(event);
    }
    return event;
  }

  /** Replays everything after {@code since} to the subscriber, then keeps it on the live feed. */
  public Subscription subscribe(long since, Subscriber subscriber) {
    Delivery delivery = new Delivery(subscriber);
    synchronized (this) {
      for (Event event : events) {
        if (event.seq() > since) {
          delivery.offer(event);
        }
      }
      if (closed) {
        delivery.offerClose();
      } else {
        deliveries.add(delivery);
      }
    }
    return () -> {
      synchronized (this) {
        deliveries.remove(delivery);
      }
      delivery.shutdown();
    };
  }

  /** The sequence number of the latest event, or 0 before the first. */
  public synchronized long lastSeq() {
    return nextSeq - 1;
  }

  /** The oldest sequence number still buffered, or the next one when nothing is buffered. */
  public synchronized long oldestSeq() {
    Event oldest = events.peekFirst();
    return oldest == null ? nextSeq : oldest.seq();
  }

  /**
   * Whether a client that last saw {@code since} can be brought up to date from the buffer: the
   * first event it has not seen must still be here (or not exist yet).
   */
  public synchronized boolean canReplayFrom(long since) {
    return since + 1 >= oldestSeq();
  }

  /** The events currently replayable, oldest first. */
  public synchronized List<Event> replayable() {
    return new ArrayList<>(events);
  }

  /** Ends the stream: live subscribers are told and no further events are expected. */
  public synchronized void close() {
    if (closed) {
      return;
    }
    closed = true;
    for (Delivery delivery : deliveries) {
      delivery.offerClose();
    }
    deliveries.clear();
  }

  public synchronized boolean isClosed() {
    return closed;
  }

  private void trim(Instant now) {
    Instant horizon = now.minus(window);
    while (events.size() > capacity
        || (!events.isEmpty() && events.peekFirst().at().isBefore(horizon))) {
      events.pollFirst();
    }
  }

  /** One subscriber's ordered, single-threaded delivery queue. */
  private static final class Delivery {
    private final Subscriber subscriber;
    private final ExecutorService thread;

    Delivery(Subscriber subscriber) {
      this.subscriber = subscriber;
      this.thread =
          Executors.newSingleThreadExecutor(
              runnable -> {
                Thread t =
                    new Thread(
                        runnable, "bilt-host-events-" + SUBSCRIBER_COUNTER.incrementAndGet());
                t.setDaemon(true);
                return t;
              });
    }

    void offer(Event event) {
      run(() -> subscriber.onEvent(event), "delivering " + event);
    }

    void offerClose() {
      run(subscriber::onClosed, "closing a subscriber");
      thread.shutdown();
    }

    private void run(Runnable work, String what) {
      try {
        thread.execute(
            () -> {
              try {
                work.run();
              } catch (RuntimeException e) {
                LOGGER.log(Level.FINE, what + " failed; the subscriber is probably gone", e);
              }
            });
      } catch (RejectedExecutionException e) {
        // the subscription was cancelled; nothing to deliver to
      }
    }

    void shutdown() {
      thread.shutdownNow();
    }
  }
}
