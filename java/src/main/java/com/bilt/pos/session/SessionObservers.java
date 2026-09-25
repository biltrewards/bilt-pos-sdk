/*
 *    ____  _ _ _
 *   | __ )(_) | |_
 *   |  _ \| | | __|
 *   | |_) | | | |_
 *   |____/|_|_|\__|
 *
 *   Bilt POS SDK
 */
package com.bilt.pos.session;

import com.bilt.pos.session.basket.BasketChange;
import com.bilt.pos.session.identity.Member;
import com.bilt.pos.widget.SessionObserver;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A session's ordered list of {@link SessionObserver}s and the queue that delivers to them.
 *
 * <p>The session's hooks ({@code basketChanged}, {@code contextChanged}, ...) run under the session
 * lock on whichever thread made the change; they only enqueue here. Delivery runs on the session's
 * operation lane, one event per lane task, so callbacks are never entered under the lock or on the
 * register thread, never overlap, and keep the order the changes happened in — for every observer
 * alike, since one task walks the whole list.
 *
 * <p>The queue is conflated at its tail: a basket change arriving while the previous one is still
 * undelivered is merged into it (the diff then spans from the last delivered snapshot to the
 * newest), and a context snapshot replaces an undelivered one. This is what bounds the queue during
 * a fast ring-up, the way the conflated display push did before it became an observer, and what
 * keeps a settlement queued behind a basket change from waiting on a backlog of stale ones. Only
 * adjacent events of the same kind merge, so the relative order of basket, context, member and
 * lifecycle events is preserved exactly.
 *
 * <p>An observer that throws is reported through {@link SessionOperations#backgroundError} and the
 * walk continues with the next observer.
 */
final class SessionObservers {

  private final SessionOperations operations;
  private final List<SessionObserver> observers = new ArrayList<>();
  private final ArrayDeque<Event> queue = new ArrayDeque<>();
  private final AtomicBoolean ended = new AtomicBoolean();

  SessionObservers(SessionOperations operations) {
    this.operations = operations;
  }

  /** Appends an observer; registration order is delivery order. Before the first event only. */
  void add(SessionObserver observer) {
    synchronized (queue) {
      observers.add(observer);
    }
  }

  /** Prepends an observer, so it is delivered to before every observer registered so far. */
  void addFirst(SessionObserver observer) {
    synchronized (queue) {
      observers.add(0, observer);
    }
  }

  /** Drops an observer; it receives nothing from now on. */
  void remove(SessionObserver observer) {
    synchronized (queue) {
      observers.remove(observer);
    }
  }

  /** The observers in delivery order, a snapshot. */
  List<SessionObserver> observers() {
    synchronized (queue) {
      return Collections.unmodifiableList(new ArrayList<>(observers));
    }
  }

  void started(SessionContextSnapshot snapshot) {
    enqueue(new StartedEvent(snapshot));
  }

  void memberChanged(Member member) {
    enqueue(new MemberEvent(member));
  }

  void contextChanged(SessionContextSnapshot snapshot) {
    enqueue(new ContextEvent(snapshot));
  }

  void basketChanged(BasketChange change) {
    enqueue(new BasketEvent(change));
  }

  /**
   * Delivers {@code ended()} to every observer and then runs {@code afterwards} on the lane — the
   * session's teardown of what the observers were using (detaching widgets, closing the platform
   * client). Must be called before the session shuts its operations down, so the task is accepted.
   * Only the first call does anything.
   */
  void ended(Runnable afterwards) {
    if (!ended.compareAndSet(false, true)) {
      return;
    }
    synchronized (queue) {
      if (observers.isEmpty()) {
        afterwards.run();
        return;
      }
    }
    enqueue(new EndedEvent(afterwards));
  }

  private void enqueue(Event event) {
    // execute() inside the monitor keeps "appended" and "has a lane task"
    // in step: a merge into the tail never needs a task of its own, and a
    // rejected append (the session ended) is withdrawn before anyone can
    // merge into it
    synchronized (queue) {
      if (observers.isEmpty()) {
        return;
      }
      Event tail = queue.peekLast();
      if (tail != null) {
        Event merged = tail.merge(event);
        if (merged != null) {
          queue.pollLast();
          queue.addLast(merged);
          return;
        }
      }
      queue.addLast(event);
      try {
        operations.executor().execute(this::deliverNext);
      } catch (RejectedExecutionException e) {
        queue.pollLast();
      }
    }
  }

  private void deliverNext() {
    Event event;
    List<SessionObserver> targets;
    synchronized (queue) {
      event = queue.pollFirst();
      targets = new ArrayList<>(observers);
    }
    if (event == null) {
      return;
    }
    for (SessionObserver observer : targets) {
      try {
        event.deliver(observer);
      } catch (RuntimeException e) {
        operations.backgroundError(
            "the " + event.name() + " callback of " + observer.getClass().getSimpleName(), e);
      }
    }
    event.afterwards();
  }

  /** One queued notification; {@link #merge} folds a later event of the same kind into it. */
  private abstract static class Event {

    abstract String name();

    abstract void deliver(SessionObserver observer);

    /** The event that replaces this one when {@code next} arrives behind it, or null to append. */
    Event merge(Event next) {
      return null;
    }

    void afterwards() {}
  }

  private static final class StartedEvent extends Event {
    private final SessionContextSnapshot snapshot;

    StartedEvent(SessionContextSnapshot snapshot) {
      this.snapshot = snapshot;
    }

    @Override
    String name() {
      return "started";
    }

    @Override
    void deliver(SessionObserver observer) {
      observer.started(snapshot);
    }
  }

  private static final class MemberEvent extends Event {
    private final Member member;

    MemberEvent(Member member) {
      this.member = member;
    }

    @Override
    String name() {
      return "memberChanged";
    }

    @Override
    void deliver(SessionObserver observer) {
      observer.memberChanged(member);
    }
  }

  private static final class ContextEvent extends Event {
    private final SessionContextSnapshot snapshot;

    ContextEvent(SessionContextSnapshot snapshot) {
      this.snapshot = snapshot;
    }

    @Override
    String name() {
      return "contextChanged";
    }

    @Override
    void deliver(SessionObserver observer) {
      observer.contextChanged(snapshot);
    }

    @Override
    Event merge(Event next) {
      return next instanceof ContextEvent ? next : null;
    }
  }

  private static final class BasketEvent extends Event {
    private final BasketChange change;

    BasketEvent(BasketChange change) {
      this.change = change;
    }

    @Override
    String name() {
      return "basketChanged";
    }

    @Override
    void deliver(SessionObserver observer) {
      observer.basketChanged(change);
    }

    @Override
    Event merge(Event next) {
      if (!(next instanceof BasketEvent)) {
        return null;
      }
      BasketChange later = ((BasketEvent) next).change;
      BasketChange.Source source =
          change.source() == later.source() ? later.source() : BasketChange.Source.BATCH;
      BasketChange merged = BasketChange.between(change.previous(), later.current(), source);
      return merged.isEmpty() ? new EmptyEvent() : new BasketEvent(merged);
    }
  }

  /**
   * What a basket change becomes when a later one undoes it before delivery: nothing to say, but
   * the lane task that was scheduled for the original still runs, so it needs an event to find.
   */
  private static final class EmptyEvent extends Event {
    @Override
    String name() {
      return "nothing";
    }

    @Override
    void deliver(SessionObserver observer) {}

    @Override
    Event merge(Event next) {
      return next instanceof BasketEvent ? next : null;
    }
  }

  private static final class EndedEvent extends Event {
    private final Runnable teardown;

    EndedEvent(Runnable teardown) {
      this.teardown = teardown;
    }

    @Override
    String name() {
      return "ended";
    }

    @Override
    void deliver(SessionObserver observer) {
      observer.ended();
    }

    @Override
    void afterwards() {
      teardown.run();
    }
  }
}
