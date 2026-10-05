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

import com.bilt.pos.session.identity.IdentifyResult;
import com.bilt.pos.session.identity.IdentifyStatus;
import com.bilt.pos.session.identity.Member;
import java.util.ArrayDeque;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A session's member state: the current {@link Member}, the background resolution of a pending one,
 * and the {@code onMemberChanged} notification. Every way a member reaches the session — the POS
 * setting it, a terminal prompt, a background lookup — lands here, so anything observing the member
 * sees one path.
 *
 * <p>Transitions take the session lock the owner hands in. Each change queues its notification
 * under that lock, so the queue holds the changes in the order they happened; {@link #flush()}
 * delivers the queue after the lock is released, fire-and-forget on the callback executor, one
 * thread at a time, so handler code never runs under the lock and notifications reach the executor
 * in change order. A throwing handler is contained.
 *
 * <p>Latest attempt wins: a pending member is resolved on the session's operation lane, and the
 * outcome is applied only if that very pending member is still the session's member when it arrives
 * — a member set in the meantime, resolved or not, is never overwritten by a stale lookup.
 */
final class SessionMember {

  private static final Logger LOGGER = Logger.getLogger(SessionMember.class.getName());

  private final ReentrantLock lock;
  private final SessionOperations operations;
  private final MemberResolver resolver;
  private final BooleanSupplier ended;
  private final Consumer<Member> onMemberChanged;
  private final Consumer<Member> observers;
  private volatile Member member;
  private volatile int attachments;
  private final ArrayDeque<Optional<Member>> notifications = new ArrayDeque<>();
  private boolean flushing;

  /**
   * {@code seed} is the builder's pre-seeded member, or null; it is installed silently, as initial
   * state rather than a change. A pending seed is resolved once the owner calls {@link
   * #resolveSeed()}. {@code observers} is the session's internal fan-out to its observers, told of
   * every change before the register's {@code onMemberChanged} handler is dispatched.
   */
  SessionMember(
      ReentrantLock lock,
      SessionOperations operations,
      MemberResolver resolver,
      BooleanSupplier ended,
      Consumer<Member> onMemberChanged,
      Consumer<Member> observers,
      Member seed) {
    this.lock = lock;
    this.operations = operations;
    this.resolver = Objects.requireNonNull(resolver, "resolver");
    this.ended = ended;
    this.onMemberChanged = onMemberChanged;
    this.observers = Objects.requireNonNull(observers, "observers");
    this.member = seed;
  }

  /** The current member, resolved or pending; null when none is attached. */
  Member current() {
    return member;
  }

  /**
   * The current member as an identification result, for the settlement path; null unless resolved.
   */
  IdentifyResult identified() {
    Member current = member;
    return current == null || !current.isResolved()
        ? null
        : IdentifyResult.found(
            current.memberId(), current.loyaltyBrand(), current.rewards(), current.pointBalance());
  }

  /**
   * How many times the POS has attached a member through {@link #set(Member)}. A terminal
   * identification reads it before it goes to the terminal and hands it back to {@link
   * #applyIdentification(IdentifyResult, int)}, so a member attached while it was waiting is not
   * overwritten by its older outcome.
   */
  int attachments() {
    return attachments;
  }

  /**
   * The public setter: installs {@code next} (null clears), announces the change, and starts the
   * lookup when {@code next} is pending. {@code precondition} runs first, under the lock, and
   * refuses the change by throwing; the announcement and the lookup happen after the lock is
   * released, so a handler never runs under it.
   */
  void set(Member next, Runnable precondition) {
    lock.lock();
    try {
      precondition.run();
      attachments++;
      install(next);
    } finally {
      lock.unlock();
    }
    flush();
    if (next != null && !next.isResolved()) {
      scheduleResolution(next);
    }
  }

  /** Starts the lookup of a pending seed; a no-op for no seed or a resolved one. */
  void resolveSeed() {
    Member seed = member;
    if (seed != null && !seed.isResolved()) {
      scheduleResolution(seed);
    }
  }

  /**
   * Applies an identification outcome, for the terminal prompt path. The latest completed attempt
   * wins: {@code FOUND} attaches the member; {@code NOT_FOUND} and {@code SUSPENDED} are
   * affirmative "no usable member" outcomes and detach any previously attached member (so a
   * re-identify cannot leave loyalty running against a stale account); {@code CANCELLED} only means
   * the customer dismissed this prompt — a prior member, resolved or pending, stands. An outcome
   * whose lookup began before the POS last attached a member ({@code attachmentsAtStart} is stale)
   * is dropped: the later attachment wins.
   *
   * <p>Must be called with the lock held; returns whether the member changed. The caller delivers
   * the queued notification with {@link #flush()} once it has released the lock.
   */
  boolean applyIdentification(IdentifyResult result, int attachmentsAtStart) {
    if (attachmentsAtStart != attachments) {
      return false;
    }
    if (result.getStatus() == IdentifyStatus.FOUND) {
      return install(Member.resolved(result));
    }
    if (result.getStatus() == IdentifyStatus.CANCELLED) {
      return false;
    }
    return install(null);
  }

  /**
   * Replaces the member under the lock. Always installs the given instance, even one equal to the
   * current member, so a re-attached pending member is the instance its lookup then checks for;
   * reports whether the value changed, in which case the notification is queued.
   */
  private boolean install(Member next) {
    Member previous = member;
    member = next;
    boolean changed = !Objects.equals(previous, next);
    if (changed) {
      synchronized (notifications) {
        notifications.add(Optional.ofNullable(next));
      }
    }
    return changed;
  }

  /**
   * Delivers the queued change notifications, never throwing into the caller: to the session's
   * observers first, then {@code onMemberChanged} on the callback executor. Call it after releasing
   * the lock. Only one thread drains at a time; a thread that finds a drain under way leaves its
   * notifications to it.
   */
  void flush() {
    synchronized (notifications) {
      if (flushing) {
        return;
      }
      flushing = true;
    }
    while (true) {
      Optional<Member> now;
      synchronized (notifications) {
        now = notifications.poll();
        if (now == null) {
          flushing = false;
          return;
        }
      }
      observers.accept(now.orElse(null));
      if (onMemberChanged == null) {
        continue;
      }
      HandlerDispatch.fireAndForget(
          operations.callback(),
          "onMemberChanged",
          () -> {
            try {
              onMemberChanged.accept(now.orElse(null));
            } catch (RuntimeException handlerFailure) {
              LOGGER.log(Level.SEVERE, "the onMemberChanged handler threw", handlerFailure);
            }
          });
    }
  }

  private void scheduleResolution(Member pending) {
    try {
      operations.executor().execute(() -> resolve(pending));
    } catch (RejectedExecutionException e) {
      operations.backgroundError(
          "resolving " + pending,
          AbstractShopperSession.invalidState(
              "the session has ended; the member cannot be resolved"));
    }
  }

  private void resolve(Member pending) {
    // superseded before the lookup began — nothing to look up
    if (member != pending) {
      return;
    }
    Member outcome;
    try {
      outcome = resolver.resolve(pending);
    } catch (RuntimeException e) {
      operations.backgroundError("resolving " + pending, e);
      return;
    }
    lock.lock();
    try {
      if (member != pending) {
        return;
      }
      if (ended.getAsBoolean()) {
        operations.backgroundError(
            "resolving " + pending,
            AbstractShopperSession.invalidState(
                "the session ended while the member was being resolved; the outcome was discarded"));
        return;
      }
      if (outcome != null && !outcome.isResolved()) {
        // nothing learned; the member stays pending
        return;
      }
      install(outcome);
    } finally {
      lock.unlock();
    }
    flush();
  }
}
